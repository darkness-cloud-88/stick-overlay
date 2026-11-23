package com.stick.overlay
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private var stage: StageView? = null
    private var panel: LinearLayout? = null
    private var finger = false
    private var hidden = false
    private val others = ArrayList<View>()
    private var homeBtn: TextView? = null
    private var server: ServerSocket? = null
    private var hostCode = ""
    @Volatile private var wrong = 0
    @Volatile private var visitors = 0
    private var link: Link? = null
    @Volatile private var visiting = false
    private var visitId = ""
    private var stickW: View? = null
    private var punchW: View? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i != null) {
            i.getStringExtra("spawn")?.let { stage?.spawn(it) }
            i.getStringExtra("host")?.let { startHost(it) }
            val ip = i.getStringExtra("visit_ip")
            if (ip != null) startVisit(ip, i.getStringExtra("visit_code") ?: "", i.getStringExtra("visit_id") ?: "")
        }
        return START_STICKY
    }

    private fun toast(m: String) { ui.post { Toast.makeText(this, m, Toast.LENGTH_LONG).show() } }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("stick", "Stick", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "stick").setContentTitle("أصدقاء العصا يعملون").setSmallIcon(android.R.drawable.ic_menu_edit).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val st = StageView(this)
        val sp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        wm.addView(st, sp)
        stage = st

        val pn = LinearLayout(this)
        pn.orientation = LinearLayout.VERTICAL
        fun btn(txt: String, keep: Boolean, act: () -> Unit): TextView {
            val v = TextView(this)
            v.text = txt
            v.textSize = 26f
            v.gravity = Gravity.CENTER
            v.setPadding(28, 20, 28, 20)
            val bg = GradientDrawable()
            bg.setColor(0xAA222222.toInt())
            bg.cornerRadius = 40f
            v.background = bg
            v.setOnClickListener { act() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8)
            pn.addView(v, lp)
            if (!keep) others.add(v)
            return v
        }
        var eye: TextView? = null
        eye = btn("👁", true) {
            hidden = !hidden
            for (v in others) if (v !== homeBtn || visiting) v.visibility = if (hidden) View.GONE else View.VISIBLE
            eye?.alpha = if (hidden) .5f else 1f
            if (visiting) { if (hidden) hideControls() else showControls() }
        }
        btn("✖", false) { stopSelf() }
        homeBtn = btn("🏠", false) { endVisit() }
        homeBtn?.visibility = View.GONE
        btn("🚪", false) { stage?.kickAll(); toast("أُخرج الزوار") }
        btn("⚡", false) { st.power("bolt") }
        btn("🔥", false) { st.power("fire") }
        btn("❄️", false) { st.power("ice") }
        btn("💥", false) { st.power("blast") }
        btn("🔧", false) { st.repair() }
        var fb: TextView? = null
        fb = btn("👆", false) {
            finger = !finger
            st.setFinger(finger)
            (fb?.background as? GradientDrawable)?.setColor(if (finger) 0xFF2F7DE1.toInt() else 0xAA222222.toInt())
            if (finger) Toast.makeText(this, "وضع الإصبع: المس الشاشة. اضغط 👆 مرة ثانية للخروج", Toast.LENGTH_LONG).show()
        }
        val pp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        pp.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        wm.addView(pn, pp)
        panel = pn
    }
        private fun showControls() {
        hideControls()
        val d = resources.displayMetrics.density
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val fl = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val sv = Stick(this)
        val p1 = WindowManager.LayoutParams((170 * d).toInt(), (170 * d).toInt(), type, fl, PixelFormat.TRANSLUCENT)
        p1.gravity = Gravity.BOTTOM or Gravity.LEFT
        p1.y = (70 * d).toInt()
        val pb = PunchBtn(this)
        val p2 = WindowManager.LayoutParams((92 * d).toInt(), (92 * d).toInt(), type, fl, PixelFormat.TRANSLUCENT)
        p2.gravity = Gravity.BOTTOM or Gravity.RIGHT
        p2.x = (20 * d).toInt()
        p2.y = (110 * d).toInt()
        try {
            wm.addView(sv, p1); stickW = sv
            wm.addView(pb, p2); punchW = pb
        } catch (e: Exception) {}
    }

    private fun hideControls() {
        stickW?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        punchW?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        stickW = null
        punchW = null
        Ctl.dx = 0; Ctl.jump = false; Ctl.sit = false; Ctl.punch = false
    }

    private fun startHost(code: String) {
        stopHost()
        hostCode = code
        wrong = 0
        Thread {
            try {
                val ss = ServerSocket(Net.PORT)
                server = ss
                toast("بدأ الاستقبال")
                while (!ss.isClosed) {
                    val s = ss.accept()
                    Thread { handleGuest(Link(s)) }.start()
                }
            } catch (e: Exception) {
                if (server != null) toast("توقف الاستقبال")
                else toast("تعذّر بدء الاستقبال")
            }
        }.start()
    }

    private fun stopHost() {
        val s = server
        server = null
        hostCode = ""
        try { s?.close() } catch (e: Exception) {}
    }

    private fun decode(b64: String): Bitmap {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        if (o.outWidth !in 1..400 || o.outHeight !in 1..400) throw Exception("size")
        val b = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: throw Exception("bad")
        return b.copy(Bitmap.Config.ARGB_8888, true) ?: b
    }

    private fun handleGuest(l: Link) {
        var sid = ""
        try {
            l.sock.soTimeout = 15000
            val h = l.read()
            if (h == null) { l.close(); return }
            if (wrong >= 8 || hostCode.isEmpty() || h.optString("t") != "hello" || h.optString("code") != hostCode) {
                wrong++
                l.send(JSONObject().put("t", "no").put("why", "code"))
                Thread.sleep(800)
                l.close()
                return
            }
            if (visitors >= 3) {
                l.send(JSONObject().put("t", "no").put("why", "full"))
                l.close()
                return
            }
            val name = h.optString("name", "ضيف").filter { !it.isISOControl() }.take(14).ifEmpty { "ضيف" }
            val arr = h.optJSONArray("png")
            if (arr == null || arr.length() != 6) {
                l.send(JSONObject().put("t", "no").put("why", "bad"))
                l.close()
                return
            }
            val bms = Array(6) { k -> decode(arr.getString(k)) }
            val q = ArrayBlockingQueue<Boolean>(1)
            ui.post {
                val st = stage
                if (st == null) q.offer(false) else st.askVisit(name) { ok -> q.offer(ok) }
            }
            val ok = q.poll(35, TimeUnit.SECONDS) ?: false
            if (!ok) {
                l.send(JSONObject().put("t", "no").put("why", "denied"))
                Thread.sleep(300)
                l.close()
                return
            }
            sid = System.nanoTime().toString()
            visitors++
            l.send(JSONObject().put("t", "ok"))
            l.sock.soTimeout = 8000
            val s0 = sid
            ui.post { stage?.addVisitor(s0, name, bms, l) }
            while (l.alive) {
                val m = l.read() ?: break
                val t = m.optString("t")
                if (t == "in") {
                    val dx = m.optInt("dx")
                    val j = m.optBoolean("jump")
                    val si = m.optBoolean("sit")
                    val pu = m.optBoolean("punch")
                    ui.post { stage?.remoteInput(s0, dx, j, si, pu) }
                } else if (t == "bye") break
            }
        } catch (e: Exception) {}
        l.close()
        if (sid.isNotEmpty()) {
            visitors--
            val s1 = sid
            ui.post { stage?.removeVisitor(s1) }
        }
    }

    private fun startVisit(ip: String, code: String, id: String) {
        endVisit()
        toast("جاري الاتصال بصديقك...")
        Thread {
            var l: Link? = null
            var started = false
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, Net.PORT), 6000)
                s.tcpNoDelay = true
                val lk = Link(s)
                l = lk
                val bms = Store.load(this, id)
                val arr = JSONArray()
                for (b in bms) {
                    val bo = ByteArrayOutputStream()
                    b.compress(Bitmap.CompressFormat.PNG, 100, bo)
                    arr.put(Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP))
                }
                lk.send(JSONObject().put("t", "hello").put("code", code).put("name", Store.name(this, id)).put("png", arr))
                s.soTimeout = 40000
                val r = lk.read()
                if (r != null && r.optString("t") == "ok") {
                    s.soTimeout = 0
                    started = true
                    link = lk
                    visiting = true
                    visitId = id
                    ui.post {
                        stage?.hideFig(id)
                        if (!hidden) { showControls(); homeBtn?.visibility = View.VISIBLE }
                    }
                    toast("وصلت إلى هاتف صديقك!")
                    Thread {
                        while (lk.alive && visiting) {
                            lk.send(JSONObject().put("t", "in").put("dx", Ctl.dx).put("jump", Ctl.jump).put("sit", Ctl.sit).put("punch", Ctl.punch))
                            Ctl.punch = false
                            try { Thread.sleep(80) } catch (e: Exception) {}
                        }
                    }.start()
                    while (lk.alive) {
                        val m = lk.read() ?: break
                        if (m.optString("t") == "bye") break
                    }
                } else {
                    val why = r?.optString("why") ?: ""
                    toast(when (why) {
                        "code" -> "الرمز غير صحيح"
                        "denied" -> "رفض صديقك الزيارة"
                        "full" -> "عند صديقك زوار كثيرون"
                        else -> "لم يقبل الاتصال"
                    })
                }
            } catch (e: Exception) {
                toast("تعذّر الاتصال. تأكد من العنوان ومن أن الهاتفين على الشبكة نفسها")
            }
            l?.close()
            if (started) ui.post { endVisit() }
        }.start()
    }

    private fun endVisit() {
        val was = visiting
        visiting = false
        link?.bye()
        link = null
        hideControls()
        homeBtn?.visibility = View.GONE
        if (was) {
            toast("عادت شخصيتك")
            stage?.spawn(visitId)
        }
    }

    override fun onDestroy() {
        stopHost()
        endVisit()
        stage?.kickAll()
        hideControls()
        try { stage?.let { wm.removeView(it) } } catch (e: Exception) {}
        try { panel?.let { wm.removeView(it) } } catch (e: Exception) {}
        super.onDestroy()
    }
}
// END
