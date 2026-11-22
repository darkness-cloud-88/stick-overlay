package com.stick.overlay
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket

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
    @Volatile var alive = true

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
// END
