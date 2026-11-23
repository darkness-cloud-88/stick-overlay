package com.stick.overlay
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.math.*

object Ctl {
    @Volatile var dx = 0
    @Volatile var jump = false
    @Volatile var sit = false
    @Volatile var punch = false
}

object Net {
    const val PORT = 8765
    fun ip(): String {
        var best = ""
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val ni = en.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val ad = ni.inetAddresses
                while (ad.hasMoreElements()) {
                    val a = ad.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        val nm = ni.name
                        if (nm.startsWith("wlan") || nm.startsWith("ap") || nm.startsWith("swlan") || nm.startsWith("eth")) return a.hostAddress ?: ""
                        if (best.isEmpty()) best = a.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {}
        return best
    }
}

class Link(val sock: Socket) {
    private val inp = DataInputStream(sock.getInputStream().buffered())
    private val out = DataOutputStream(sock.getOutputStream().buffered())
    private val ex = Executors.newSingleThreadExecutor()
    @Volatile private var pend = 0
    @Volatile var alive = true

    fun post(o: JSONObject) {
        if (!alive || pend > 2) return
        pend++
        try { ex.execute { try { send(o) } finally { pend-- } } } catch (e: Exception) { pend-- }
    }

    fun send(o: JSONObject) {
        try {
            val b = o.toString().toByteArray(Charsets.UTF_8)
            synchronized(out) { out.writeInt(b.size); out.write(b); out.flush() }
        } catch (e: Exception) { close() }
    }

    fun read(): JSONObject? {
        try {
            val n = inp.readInt()
            if (n <= 0 || n > 4000000) { close(); return null }
            val b = ByteArray(n)
            inp.readFully(b)
            return JSONObject(String(b, Charsets.UTF_8))
        } catch (e: Exception) { close(); return null }
    }

    fun bye() {
        Thread {
            try { send(JSONObject().put("t", "bye")) } catch (e: Exception) {}
            close()
        }.start()
    }

    fun close() {
        alive = false
        try { sock.close() } catch (e: Exception) {}
        try { ex.shutdownNow() } catch (e: Exception) {}
    }
}

class Stick(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = 56f * ctx.resources.displayMetrics.density
    private var on = false
    private var ox = 0f
    private var oy = 0f
    private var cx = 0f
    private var cy = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { on = true; ox = e.x; oy = e.y; cx = ox; cy = oy }
            MotionEvent.ACTION_MOVE -> { cx = e.x; cy = e.y }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { on = false; Ctl.dx = 0; Ctl.jump = false; Ctl.sit = false }
        }
        if (on) {
            val dx = ((cx - ox) / r).coerceIn(-1f, 1f)
            val dy = ((cy - oy) / r).coerceIn(-1f, 1f)
            Ctl.dx = if (dx > .3f) 1 else if (dx < -.3f) -1 else 0
            Ctl.jump = dy < -.5f
            Ctl.sit = dy > .5f
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        if (!on) return
        p.style = Paint.Style.FILL
        p.color = Color.argb(45, 255, 255, 255)
        c.drawCircle(ox, oy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Color.argb(110, 255, 255, 255)
        c.drawCircle(ox, oy, r, p)
        var kx = cx - ox
        var ky = cy - oy
        val d = Math.hypot(kx.toDouble(), ky.toDouble()).toFloat()
        if (d > r) { kx = kx / d * r; ky = ky / d * r }
        p.style = Paint.Style.FILL
        p.color = Color.argb(120, 255, 255, 255)
        c.drawCircle(ox + kx, oy + ky, r * .42f, p)
    }
}

class PunchBtn(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var down = false
    private val d = ctx.resources.displayMetrics.density

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = true; Ctl.punch = true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> down = false
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        p.style = Paint.Style.FILL
        p.color = if (down) Color.argb(190, 229, 72, 77) else Color.argb(110, 229, 72, 77)
        c.drawCircle(r, r, r - 4f, p)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = 30f * d
        c.drawText("👊", r, r + 11f * d, p)
    }
}

class Mini(ctx: Context, private val bm: Array<Bitmap>, private val mySid: String) : View(ctx) {
    private val dn = ctx.resources.displayMetrics.density
    private var fr: JSONObject? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val bgp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 9f * dn; color = Color.WHITE }
    private val jx = FloatArray(11)
    private val jy = FloatArray(11)
    private val cols = intArrayOf(0xFF7FB8FF.toInt(), 0xFFFF8F8F.toInt(), 0xFF8FE3A8.toInt(), 0xFFFFD27F.toInt(), 0xFFC7A8FF.toInt())
    private val lk = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(1, 3), intArrayOf(3, 4), intArrayOf(1, 5),
        intArrayOf(5, 6), intArrayOf(2, 7), intArrayOf(7, 8), intArrayOf(2, 9), intArrayOf(9, 10))

    fun update(o: JSONObject) { fr = o; invalidate() }

    override fun onDraw(c: Canvas) {
        bgp.color = Color.argb(160, 18, 24, 34)
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 10f * dn, 10f * dn, bgp)
        val o = fr
        if (o == null) { c.drawText("...", width / 2f, height / 2f, tp); return }
        val hw = max(o.optInt("w", 1), 1).toFloat()
        val hh = max(o.optInt("h", 1), 1).toFloat()
        val k = min(width / hw, height / hh)
        val s = o.optDouble("s", 3.0).toFloat() * k
        val g = o.optInt("g", 0) * k
        p.color = Color.argb(150, 255, 255, 255)
        p.strokeWidth = 1.5f * dn
        c.drawLine(0f, g + 2f, width.toFloat(), g + 2f, p)
        val arr = o.optJSONArray("f") ?: return
        for (pass in 0..1) {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val mine = mySid.isNotEmpty() && f.optString("sid") == mySid
                if (mine != (pass == 1)) continue
                val pa = f.optJSONArray("p") ?: continue
                if (pa.length() < 22) continue
                for (j in 0..10) {
                    jx[j] = pa.optDouble(2 * j).toFloat() * k
                    jy[j] = pa.optDouble(2 * j + 1).toFloat() * k
                }
                val face = f.optInt("fc", 1).toFloat()
                if (mine) Puppet.draw(c, bm, jx, jy, face, s)
                else {
                    p.color = cols[abs(f.optString("n").hashCode()) % cols.size]
                    p.strokeWidth = max(1.5f * dn, 5f * s)
                    for (l in lk) c.drawLine(jx[l[0]], jy[l[0]], jx[l[1]], jy[l[1]], p)
                    c.drawCircle(jx[0], jy[0], 9f * s, p)
                    c.drawText(f.optString("n"), jx[0], jy[0] - 14f * s, tp)
                }
            }
        }
        p.color = Color.argb(200, 47, 125, 225)
        p.strokeWidth = 2f * dn
        c.drawRoundRect(RectF(1f, 1f, width - 1f, height - 1f), 10f * dn, 10f * dn, p)
    }
}
// END
