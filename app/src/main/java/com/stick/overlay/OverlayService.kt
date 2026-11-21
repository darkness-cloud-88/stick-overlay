package com.stick.overlay
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var stage: StageView? = null
    private var panel: LinearLayout? = null
    private var finger = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        i?.getStringExtra("spawn")?.let { stage?.spawn(it) }
        return START_STICKY
    }

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
        fun btn(txt: String, act: () -> Unit): TextView {
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
            return v
        }
        btn("⚡") { st.power("bolt") }
        btn("🔥") { st.power("fire") }
        btn("❄️") { st.power("ice") }
        btn("💥") { st.power("blast") }
        btn("🔧") { st.repair() }
        var fb: TextView? = null
        fb = btn("👆") {
            finger = !finger
            st.setFinger(finger)
            (fb?.background as? GradientDrawable)?.setColor(if (finger) 0xFF2F7DE1.toInt() else 0xAA222222.toInt())
            if (finger) Toast.makeText(this, "وضع الإصبع: المس الشاشة. اضغط 👆 مرة ثانية للخروج", Toast.LENGTH_LONG).show()
        }
        btn("✖") { stopSelf() }
        val pp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        pp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        wm.addView(pn, pp)
        panel = pn
    }

    override fun onDestroy() {
        try { stage?.let { wm.removeView(it) } } catch (e: Exception) {}
        try { panel?.let { wm.removeView(it) } } catch (e: Exception) {}
        super.onDestroy()
    }
}
