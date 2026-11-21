package com.stick.overlay
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

object Puppet {
    private val norm = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(.7f, .7f, .7f, 1f) })
    }

    private fun seg(cv: Canvas, b: Bitmap, i: Int, x1: Float, y1: Float, x2: Float, y2: Float, flip: Boolean, p: Paint) {
        val len = hypot(x2 - x1, y2 - y1)
        if (len < 1f) return
        val k = len / hypot(Store.BX[i] - Store.AX[i], Store.BY[i] - Store.AY[i])
        cv.save()
        cv.translate(x1, y1)
        cv.rotate(Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat() - 90f)
        if (flip) cv.scale(-1f, 1f)
        cv.scale(k, k)
        cv.drawBitmap(b, -Store.AX[i], -Store.AY[i], p)
        cv.restore()
    }

    fun draw(cv: Canvas, bm: Array<Bitmap>, jx: FloatArray, jy: FloatArray, face: Float, s: Float) {
        val fl = face < 0f
        seg(cv, bm[2], 2, jx[1], jy[1], jx[3], jy[3], fl, dark)
        seg(cv, bm[3], 3, jx[3], jy[3], jx[4], jy[4], fl, dark)
        seg(cv, bm[4], 4, jx[2], jy[2], jx[7], jy[7], fl, dark)
        seg(cv, bm[5], 5, jx[7], jy[7], jx[8], jy[8], fl, dark)
        seg(cv, bm[1], 1, jx[1], jy[1], jx[2], jy[2], fl, norm)
        val th = Math.toDegrees(atan2((jy[0] - jy[1]).toDouble(), (jx[0] - jx[1]).toDouble())).toFloat() + 90f
        cv.save()
        cv.translate(jx[1], jy[1])
        cv.rotate(th)
        if (fl) cv.scale(-1f, 1f)
        cv.scale(.2f * s, .2f * s)
        cv.drawBitmap(bm[0], -Store.AX[0], -Store.AY[0], norm)
        cv.restore()
        seg(cv, bm[4], 4, jx[2], jy[2], jx[9], jy[9], fl, norm)
        seg(cv, bm[5], 5, jx[9], jy[9], jx[10], jy[10], fl, norm)
        seg(cv, bm[2], 2, jx[1], jy[1], jx[5], jy[5], fl, norm)
        seg(cv, bm[3], 3, jx[5], jy[5], jx[6], jy[6], fl, norm)
    }

    fun standing(jx: FloatArray, jy: FloatArray, cx: Float, cy: Float, u: Float) {
        jx[2] = cx; jy[2] = cy
        jx[1] = cx; jy[1] = cy - 30f * u
        jx[0] = cx; jy[0] = cy - 44f * u
        jx[3] = cx - 9f * u; jy[3] = jy[1] + 13f * u
        jx[4] = cx - 12f * u; jy[4] = jy[1] + 27f * u
        jx[5] = cx + 9f * u; jy[5] = jy[1] + 13f * u
        jx[6] = cx + 12f * u; jy[6] = jy[1] + 27f * u
        jx[7] = cx - 6f * u; jy[7] = cy + 20f * u
        jx[8] = cx - 9f * u; jy[8] = cy + 40f * u
        jx[9] = cx + 6f * u; jy[9] = cy + 20f * u
        jx[10] = cx + 9f * u; jy[10] = cy + 40f * u
    }
}

class Fig(val id: String, val name: String, val mood: Int, val bm: Array<Bitmap>) {
    val jx = FloatArray(11); val jy = FloatArray(11)
    val pjx = FloatArray(11); val pjy = FloatArray(11)
    val sx = FloatArray(11); val sy = FloatArray(11)
    var x = 0f; var yoff = 0f; var vy = 0f
    var face = 1f; var t = 0f; var t2 = 0f
    var walk = 0f; var sit = 0f; var lie = 0f; var wave = 0f; var cast = 0f
    var mode = 1; var modeT = 0
    var wantSit = false; var wx = 0f; var moving = false
    var timer = 60; var idleT = 300
    var say = ""; var sayT = 0
    var castT = 0; var waveT = 0; var fireT = 0
    var grab: PointF? = null
    var held = -1; var rest = 0
    var fvx = 0f; var fvy = 0f
    var bl = 0f; var fxCd = 0; var crackCd = 0
    var greet = true
    var handle: View? = null
    var hlp: WindowManager.LayoutParams? = null
    var hx = -1; var hy = -1; var hw = -1; var hh = -1
}
class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var l: Int, val c: Int, val r: Float)
class Ring(val x: Float, val y: Float, var r: Float, val m: Float, val c: Int)

class StageView(ctx: Context) : View(ctx) {
    private val dn = ctx.resources.displayMetrics.density
    private val s = dn * 1.05f
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val pokeLines = arrayOf(
        arrayOf("مرحباً صديقي!", "نعم؟ أنا هنا!"),
        arrayOf("أوه! أهلاً..", "لقد أخفتني قليلاً"),
        arrayOf("هاها! دغدغة!", "مرة ثانية!"),
        arrayOf("مممم؟ أيقظتني..", "خمس دقائق فقط.."))
    private val idleLines = arrayOf(
        arrayOf("ما هذا؟ سأستكشفه!", "هل رأيت هذه الأيقونات؟", "أنا أحرس هاتفك!"),
        arrayOf("هل أنت هنا؟", "أحب هذا الركن الهادئ", "لا تتركني وحدي.."),
        arrayOf("لنلعب شيئاً!", "شاهد قفزتي!", "هاها! أنا أسرع منك"),
        arrayOf("أشعر بالنعاس..", "هل حان وقت النوم؟", "سأرتاح قليلاً"))
    private val hurtLines = arrayOf(
        arrayOf("هذا لا يؤلم!", "أنا قوي!"),
        arrayOf("آآه! كفى!", "أوووه.."),
        arrayOf("هاها! مرة ثانية!", "واااو!"),
        arrayOf("آه.. ظهري", "أريد أن أنام.."))
    private val recLines = arrayOf(
        arrayOf("أنا بخير!", "هيا مجدداً!"),
        arrayOf("هل انتهينا؟", "رأسي يدور.."),
        arrayOf("ممتع! مرة ثانية!", "هاها!"),
        arrayOf("آه.. أحتاج راحة", "كان ذلك كافياً"))
    private val showLines = arrayOf("شاهد هذا!", "انظر ماذا أفعل!", "أنا أتدرب فقط")
    private val rc = arrayOf(
        floatArrayOf(0f, 1f, 14f, 1f), floatArrayOf(1f, 2f, 30f, 1f), floatArrayOf(1f, 3f, 15f, 1f),
        floatArrayOf(3f, 4f, 15f, 1f), floatArrayOf(1f, 5f, 15f, 1f), floatArrayOf(5f, 6f, 15f, 1f),
        floatArrayOf(2f, 7f, 20f, 1f), floatArrayOf(7f, 8f, 20f, 1f), floatArrayOf(2f, 9f, 20f, 1f),
        floatArrayOf(9f, 10f, 20f, 1f), floatArrayOf(0f, 2f, 44f, .25f))
    private val figs = ArrayList<Fig>()
    private val pending = ArrayList<String>()
    private val parts = ArrayList<Part>()
    private val rings = ArrayList<Ring>()
    private val cracks = ArrayList<FloatArray>()
    private val tmp = FloatArray(4)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 12f * dn; color = Color.WHITE; setShadowLayer(3f, 0f, 0f, Color.BLACK) }
    private val bt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13f * dn; color = Color.BLACK }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
    private var flash = 0
    private var boltL = 0
    private var bolt: List<PointF> = emptyList()
    private var last = 0L
    private var acc = 0f
    private val ground: Float get() = height - 56f * dn

    private fun rnd() = Math.random().toFloat()
    private fun dp(v: Int) = (v * dn).toInt()
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun say(f: Fig, t: String, n: Int) { f.say = t; f.sayT = n }
    private fun pick(a: Array<String>) = a[(rnd() * a.size).toInt().coerceIn(0, a.size - 1)]

    fun spawn(id: String) { pending.add(id) }

    fun repair() {
        for (c in cracks) part(c[0], c[1], 0xFFFFE08A.toInt(), 6, 4f)
        cracks.clear()
    }

    override fun onDetachedFromWindow() {
        for (f in figs) removeHandle(f)
        super.onDetachedFromWindow()
    }

    private fun addFig(id: String) {
        for (o in figs.filter { it.id == id }) removeHandle(o)
        figs.removeAll { it.id == id }
        if (figs.size >= 6) { val o = figs.removeAt(0); removeHandle(o) }
        val f = Fig(id, Store.name(context, id), abs(id.hashCode()) % 4, Store.load(context, id))
        f.x = width * (.15f + rnd() * .7f)
        f.yoff = ground - 40f * dn
        f.face = if (rnd() < .5f) 1f else -1f
        figs.add(f)
        pose(f)
        makeHandle(f)
    }

    private fun makeHandle(f: Fig) {
        val v = View(context)
        var downT = 0L
        var sx = 0f
        var sy = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downT = e.eventTime; sx = e.rawX; sy = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && hypot(e.rawX - sx, e.rawY - sy) > 12f * dn) {
                        moved = true
                        var bi = 1
                        var bd = 1e9f
                        for (i in 0..10) {
                            val d = hypot(f.jx[i] - sx, f.jy[i] - sy)
                            if (d < bd) { bd = d; bi = i }
                        }
                        f.held = bi
                        f.grab = PointF(e.rawX, e.rawY)
                    }
                    f.grab?.set(e.rawX, e.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (f.grab != null) f.grab = null
                    else if (!moved && e.eventTime - downT < 300 && f.mode == 0) {
                        say(f, pick(pokeLines[f.mood]), 80)
                        f.waveT = 60
                    }
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            dp(50), dp(100), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        try { wm.addView(v, lp); f.handle = v; f.hlp = lp } catch (e: Exception) {}
    }

    private fun removeHandle(f: Fig) {
        f.handle?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        f.handle = null; f.hlp = null; f.grab = null
    }

    private fun syncHandle(f: Fig) {
        val v = f.handle ?: return
        val lp = f.hlp ?: return
        var minX = 1e9f; var minY = 1e9f; var maxX = -1e9f; var maxY = -1e9f
        for (i in 0..10) { minX = min(minX, f.jx[i]); minY = min(minY, f.jy[i]); maxX = max(maxX, f.jx[i]); maxY = max(maxY, f.jy[i]) }
        val pad = 22f * dn
        val nx = (minX - pad).toInt()
        val ny = (minY - pad).toInt()
        val nw = max(maxX - minX + 2f * pad, 50f * dn).toInt()
        val nh = max(maxY - minY + 2f * pad, 90f * dn).toInt()
        if (abs(nx - f.hx) < 2 && abs(ny - f.hy) < 2 && nw == f.hw && nh == f.hh) return
        lp.x = nx; lp.y = ny; lp.width = nw; lp.height = nh
        f.hx = nx; f.hy = ny; f.hw = nw; f.hh = nh
        try { wm.updateViewLayout(v, lp) } catch (e: Exception) {}
    }

    private fun part(x: Float, y: Float, c: Int, n: Int, sp: Float) {
        repeat(n) { parts.add(Part(x, y, (rnd() - .5f) * sp * s, (rnd() - .8f) * sp * s, 20 + (rnd() * 20).toInt(), c, (1 + rnd() * 2) * s)) }
    }

    private fun addCrack(x: Float, y: Float, power: Float) {
        val seg = ArrayList<Float>()
        val n = 4 + (rnd() * 3).toInt()
        val len = min(power * 1.2f + 10f, 40f) * s
        for (k in 0 until n) {
            var a = rnd() * 6.283f
            var cx = x
            var cy = y
            repeat(4) {
                val l = len * (.6f + rnd() * .6f) / 2f
                a += (rnd() - .5f) * .9f
                val nx = cx + cos(a) * l
                val ny = cy + sin(a) * l
                seg.add(cx); seg.add(cy); seg.add(nx); seg.add(ny)
                cx = nx; cy = ny
            }
        }
        cracks.add(seg.toFloatArray())
        if (cracks.size > 14) cracks.removeAt(0)
    }

    private fun hitFx(f: Fig, x: Float, y: Float, imp: Float) {
        val a = abs(imp)
        if (a > 4f && f.fxCd <= 0) {
            part(x, y, 0xFFB8B0A0.toInt(), min(10, 2 + (a * .4f).toInt()), 3f)
            f.fxCd = 6
        }
        if (a > 15f && f.crackCd <= 0) {
            addCrack(x, y, a)
            f.crackCd = 25
            if (f.sayT <= 0) say(f, pick(hurtLines[f.mood]), 60)
        }
    }

    private fun cast(f: Fig, kind: String) {
        if (f.mode != 0) return
        f.castT = 60
        f.wantSit = false
        say(f, pick(showLines), 70)
        val x = (f.x + f.face * 100f * s).coerceIn(30f, max(30f, width - 30f))
        val y = ground - 20f * s
        when (kind) {
            "bolt" -> {
                val pts = ArrayList<PointF>()
                for (i in 0..8) pts.add(PointF(if (i == 8) x else x + (rnd() - .5f) * 40f * s, y * i / 8f))
                bolt = pts; boltL = 9; flash = 3
                part(x, y, 0xFFFFF27A.toInt(), 12, 6f)
            }
            "fire" -> f.fireT = 90
            "ice" -> { rings.add(Ring(x, y, 10f * s, 90f * s, 0xFF7FD4FF.toInt())); part(x, y, 0xFFBFEAFF.toInt(), 14, 5f) }
            "blast" -> { rings.add(Ring(x, y, 10f * s, 120f * s, 0xFFFF9A3C.toInt())); part(x, y, 0xFFFFB347.toInt(), 18, 8f) }
        }
    }

    fun power(kind: String) {
        if (figs.isEmpty()) return
        cast(figs[(rnd() * figs.size).toInt().coerceIn(0, figs.size - 1)], kind)
    }

    private fun choose(f: Fig) {
        if (f.sit > .05f || f.wantSit) {
            if (rnd() < .5f) { f.wantSit = true; f.timer = 150 + (rnd() * 250).toInt() }
            else { f.wantSit = false; f.timer = 35 }
            return
        }
        val pSit = when (f.mood) { 3 -> .4f; 1 -> .2f; 0 -> .15f; else -> .1f }
        val pWalk = when (f.mood) { 0 -> .5f; 1 -> .35f; 2 -> .55f; else -> .2f }
        val r = rnd()
        when {
            r < pSit -> { f.wantSit = true; f.timer = 200 + (rnd() * 300).toInt() }
            r < pSit + pWalk -> { f.wx = width * (.1f + rnd() * .8f); f.moving = true }
            r < pSit + pWalk + .1f -> { f.waveT = 100; f.timer = 140 }
            f.mood == 2 && r < pSit + pWalk + .2f -> { f.vy = -9f * s; f.timer = 90 }
            else -> f.timer = 80 + (rnd() * 120).toInt()
        }
    }

    private fun enterRag(f: Fig) {
        f.mode = 4
        for (i in 0..10) { f.pjx[i] = f.jx[i]; f.pjy[i] = f.jy[i] }
        f.rest = 0; f.fvx = 0f; f.fvy = 0f; f.bl = 0f
        f.wantSit = false; f.moving = false; f.walk = 0f; f.vy = 0f
    }

    private fun exitRag(f: Fig) {
        for (i in 0..10) { f.sx[i] = f.jx[i]; f.sy[i] = f.jy[i] }
        f.bl = 1f
        f.x = f.jx[2].coerceIn(width * .05f, width * .95f)
        f.face = if (f.jx[1] >= f.jx[2]) 1f else -1f
        f.yoff = 0f; f.vy = 0f; f.lie = 1f; f.sit = 0f; f.walk = 0f
        f.mode = 2; f.modeT = 40
        f.wantSit = false; f.moving = false
    }

    private fun ragStep(f: Fig) {
        val g = ground
        val w = width.toFloat()
        val gr = f.grab
        if (gr == null && f.held >= 0) {
            val h = f.held
            val lim = 45f * s
            f.pjx[h] = f.jx[h] - f.fvx.coerceIn(-lim, lim) * 1.4f
            f.pjy[h] = f.jy[h] - f.fvy.coerceIn(-lim, lim) * 1.4f
            f.held = -1
        }
        val gi = f.held
        val pinned = gr != null && gi >= 0
        if (pinned) {
            f.fvx = lerp(f.fvx, gr!!.x - f.jx[gi], .5f)
            f.fvy = lerp(f.fvy, gr.y - f.jy[gi], .5f)
        }
        for (i in 0..10) {
            if (pinned && i == gi) continue
            val vx = (f.jx[i] - f.pjx[i]) * .992f
            val vy = (f.jy[i] - f.pjy[i]) * .992f
            f.pjx[i] = f.jx[i]; f.pjy[i] = f.jy[i]
            f.jx[i] += vx; f.jy[i] += vy + .7f * s
        }
        if (pinned) {
            f.pjx[gi] = f.jx[gi]; f.pjy[gi] = f.jy[gi]
            f.jx[gi] = gr!!.x; f.jy[gi] = gr.y
        }
        repeat(8) {
            for (c in rc) {
                val a = c[0].toInt()
                val b = c[1].toInt()
                val dx = f.jx[b] - f.jx[a]
                val dy = f.jy[b] - f.jy[a]
                val d = max(hypot(dx, dy), .001f)
                val df = (d - c[2] * s) / d * .5f * c[3]
                val wa = if (pinned && a == gi) 0f else 1f
                val wb = if (pinned && b == gi) 0f else 1f
                val tw = wa + wb
                if (tw == 0f) continue
                val fa = 2f * wa / tw
                val fb = 2f * wb / tw
                f.jx[a] += dx * df * fa; f.jy[a] += dy * df * fa
                f.jx[b] -= dx * df * fb; f.jy[b] -= dy * df * fb
            }
            for (i in 0..10) {
                if (pinned && i == gi) continue
                var x = f.jx[i]
                var y = f.jy[i]
                if (y > g) {
                    val v = y - f.pjy[i]
                    y = g
                    f.pjy[i] = y + v * .3f
                    f.pjx[i] += (x - f.pjx[i]) * .25f
                    hitFx(f, x, g, v / s)
                }
                if (y < 4f) {
                    val v = y - f.pjy[i]
                    y = 4f
                    f.pjy[i] = y + v * .3f
                    hitFx(f, x, 4f, v / s)
                }
                if (x < 8f) {
                    val v = x - f.pjx[i]
                    x = 8f
                    f.pjx[i] = x + v * .4f
                    hitFx(f, 8f, y, v / s)
                }
                if (x > w - 8f) {
                    val v = x - f.pjx[i]
                    x = w - 8f
                    f.pjx[i] = x + v * .4f
                    hitFx(f, w - 8f, y, v / s)
                }
                f.jx[i] = x; f.jy[i] = y
            }
        }
        var mx = 0f
        for (i in 0..10) mx = max(mx, abs(f.jx[i] - f.pjx[i]) + abs(f.jy[i] - f.pjy[i]))
        if (!pinned && f.held < 0 && mx < 1.6f * s && f.jy[2] > g - 22f * s) f.rest++ else f.rest = 0
        if (f.rest > 25) exitRag(f)
    }

    private fun step(f: Fig) {
        f.t2 += .06f
        if (f.sayT > 0) f.sayT--
        if (f.castT > 0) f.castT--
        if (f.waveT > 0) f.waveT--
        if (f.fxCd > 0) f.fxCd--
        if (f.crackCd > 0) f.crackCd--
        f.cast = lerp(f.cast, if (f.castT > 0) 1f else 0f, .15f)
        f.wave = lerp(f.wave, if (f.waveT > 0) 1f else 0f, .12f)
        if (f.fireT > 0) {
            f.fireT--
            part(f.jx[6], f.jy[6], if (rnd() < .5f) 0xFFFF7A1A.toInt() else 0xFFFFD23F.toInt(), 2, 3f)
        }
        if (f.grab != null && f.mode != 4) enterRag(f)
        if (f.mode == 4) { ragStep(f); return }
        if (f.mode == 1) {
            f.vy += .7f * s
            f.yoff -= f.vy
            if (f.yoff <= 0f) {
                f.yoff = 0f; f.vy = 0f; f.mode = 2; f.modeT = 70; f.lie = 1f
                part(f.x, ground, 0xFFB8B0A0.toInt(), 10, 4f)
            }
        } else if (f.mode == 2) {
            f.modeT--
            if (f.modeT <= 0) f.mode = 3
        } else if (f.mode == 3) {
            f.lie = max(0f, f.lie - .012f)
            if (f.lie <= .01f) {
                f.lie = 0f; f.mode = 0; f.timer = 100
                if (f.greet) {
                    f.greet = false
                    say(f, "أهلاً، أنا " + f.name, 150)
                    f.waveT = 120
                } else say(f, pick(recLines[f.mood]), 90)
            }
        } else if (f.yoff > 0f || f.vy != 0f) {
            f.vy += .8f * s
            f.yoff -= f.vy
            if (f.yoff <= 0f) {
                f.yoff = 0f; f.vy = 0f; f.timer = 40
                part(f.x, ground, 0xFFB8B0A0.toInt(), 4, 2.5f)
            }
        } else {
            val spd = when (f.mood) { 0 -> .6f; 1 -> .45f; 2 -> .9f; else -> .3f }
            if (f.wantSit) f.sit = min(1f, f.sit + .04f) else f.sit = max(0f, f.sit - .04f)
            if (!f.moving) {
                f.timer--
                if (f.timer <= 0) choose(f)
            }
            if (f.moving) {
                val dx = f.wx - f.x
                if (abs(dx) < 4f * s || f.sit > .05f) {
                    f.moving = false; f.timer = 40 + (rnd() * 80).toInt()
                } else {
                    f.face = if (dx > 0f) 1f else -1f
                    f.x += f.face * spd * s
                    f.walk = lerp(f.walk, 1f, .1f)
                    f.t += spd * .12f
                }
            } else f.walk = lerp(f.walk, 0f, .1f)
            f.idleT--
            if (f.idleT <= 0) {
                f.idleT = 700 + (rnd() * 900).toInt()
                if (f.sayT <= 0) say(f, pick(idleLines[f.mood]), 110)
            }
        }
        val w = width.toFloat()
        f.x = f.x.coerceIn(w * .05f, w * .95f)
    }

    private fun ik(hx: Float, hy: Float, fx: Float, fy: Float, l1: Float, l2: Float, bend: Float) {
        val dx = fx - hx
        val dy = fy - hy
        val d0 = max(hypot(dx, dy), .001f)
        val ux = dx / d0
        val uy = dy / d0
        val d = min(d0, l1 + l2 - .01f)
        val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val h = sqrt(max(l1 * l1 - a * a, 0f))
        tmp[0] = hx + ux * a - uy * h * bend
        tmp[1] = hy + uy * a + ux * h * bend
        tmp[2] = hx + ux * d
        tmp[3] = hy + uy * d
    }

    private fun pose(f: Fig) {
        val fy = ground - f.yoff
        val face = f.face
        val pi = Math.PI.toFloat()
        val bob = abs(sin(f.t)) * 2.5f * f.walk
        val low = max(f.sit, f.lie)
        val hipX = f.x
        val hipY = fy - (lerp(38f, 5f, low) - bob) * s
        val ang = f.lie * 1.45f + f.sit * .1f
        val neckX = hipX + face * sin(ang) * 30f * s
        val neckY = hipY - cos(ang) * 30f * s + sin(f.t2) * .8f * s
        f.jx[2] = hipX; f.jy[2] = hipY
        f.jx[1] = neckX; f.jy[1] = neckY
        val dxn = neckX - hipX
        val dyn = neckY - hipY
        val dl = max(hypot(dxn, dyn), .001f)
        f.jx[0] = neckX + dxn / dl * 14f * s
        f.jy[0] = neckY + dyn / dl * 14f * s
        val air = if (f.yoff > 3f * s) 1f else 0f
        for (i in 0..1) {
            val ph = f.t + i * pi
            val base = if (i == 0) -6f else 6f
            val sitX = face * (24f + i * 7f)
            val lieX = -face * (26f + i * 9f)
            val swing = face * sin(ph) * 13f * f.walk * (1f - f.sit)
            val lift = max(0f, cos(ph)) * 9f * f.walk * (1f - f.sit)
            val stand = base + swing + sin(f.t2 * 2f + i * 2f) * 5f * air
            val fx = hipX + lerp(lerp(stand, sitX, f.sit), lieX, f.lie) * s
            ik(hipX, hipY, fx, fy - lift * s, 20f * s, 20f * s, -face)
            val b = 7 + i * 2
            f.jx[b] = tmp[0]; f.jy[b] = tmp[1]; f.jx[b + 1] = tmp[2]; f.jy[b + 1] = tmp[3]
        }
        val nl = 1f - f.lie
        for (i in 0..1) {
            val side = if (i == 0) -1f else 1f
            val pa = f.t + (1 - i) * pi
            var hx = neckX + side * 8f * s + face * sin(pa) * 11f * s * f.walk * (1f - f.sit)
            var hy = neckY + 26f * s
            hx = lerp(hx, hipX + face * (12f + i * 6f) * s, f.sit)
            hy = lerp(hy, fy - 20f * s, f.sit)
            hx = lerp(hx, neckX + face * (6f + i * 8f) * s, f.lie)
            hy = lerp(hy, fy - (4f + i * 3f) * s, f.lie)
            if (i == 1) {
                hx = lerp(hx, neckX + face * 14f * s + sin(f.t2 * 4f) * 7f * s, f.wave * nl)
                hy = lerp(hy, neckY - 22f * s, f.wave * nl)
            }
            hx = lerp(hx, neckX + side * 12f * s, f.cast * nl)
            hy = lerp(hy, neckY - 28f * s, f.cast * nl)
            ik(neckX, neckY, hx, hy, 15f * s, 15f * s, face)
            val b = 3 + i * 2
            f.jx[b] = tmp[0]; f.jy[b] = tmp[1]; f.jx[b + 1] = tmp[2]; f.jy[b + 1] = tmp[3]
        }
    }

    private fun tick() {
        if (pending.isNotEmpty() && width > 0) {
            val l = ArrayList<String>(pending)
            pending.clear()
            for (id in l) addFig(id)
        }
        for (f in figs) {
            step(f)
            if (f.mode != 4) {
                pose(f)
                if (f.bl > 0f) {
                    f.bl -= .08f
                    val k = max(f.bl, 0f)
                    for (i in 0..10) { f.jx[i] = lerp(f.jx[i], f.sx[i], k); f.jy[i] = lerp(f.jy[i], f.sy[i], k) }
                }
            }
        }
        for (q in parts) { q.x += q.vx; q.y += q.vy; q.vy += .15f * s; q.l-- }
        parts.removeAll { it.l <= 0 }
        for (r in rings) r.r += 7f * s
        rings.removeAll { it.r > it.m }
        if (flash > 0) flash--
        if (boltL > 0) boltL--
    }

    override fun onDraw(cv: Canvas) {
        val now = System.nanoTime()
        if (last == 0L) last = now
        acc = min(acc + (now - last) / 16_666_667f, 3f)
        last = now
        while (acc >= 1f) { tick(); acc -= 1f }
        for (f in figs) syncHandle(f)
        for (c in cracks) {
            paint.color = Color.BLACK; paint.alpha = 120; paint.strokeWidth = 4f * s
            cv.drawLines(c, paint)
            paint.color = Color.WHITE; paint.alpha = 235; paint.strokeWidth = 2f * s
            cv.drawLines(c, paint)
        }
        for (f in figs) {
            val sh = if (f.mode == 4) .7f else max(.4f, 1f - f.yoff / (160f * s))
            fill.color = Color.BLACK
            fill.alpha = 55
            cv.drawOval(RectF(f.jx[2] - 18f * s * sh, ground + 1f, f.jx[2] + 18f * s * sh, ground + 6f * s), fill)
            Puppet.draw(cv, f.bm, f.jx, f.jy, f.face, s)
            cv.drawText(f.name, f.jx[0], f.jy[0] - 22f * s, tp)
            if (f.sayT > 0) {
                val tw = bt.measureText(f.say)
                val half = tw / 2f + 14f * dn
                val bx = f.jx[0].coerceIn(half, max(half, width - half))
                val by = max(f.jy[0] - 46f * s, 30f * dn)
                cv.drawRoundRect(RectF(bx - half, by - 18f * dn, bx + half, by + 8f * dn), 14f * dn, 14f * dn, bg)
                cv.drawText(f.say, bx, by, bt)
            }
        }
        for (r in rings) {
            paint.color = r.c
            paint.alpha = ((1f - r.r / r.m) * 255).toInt().coerceIn(0, 255)
            paint.strokeWidth = 4f * s
            cv.drawCircle(r.x, r.y, r.r, paint)
        }
        for (q in parts) {
            fill.color = q.c
            fill.alpha = (min(1f, q.l / 15f) * 255).toInt()
            cv.drawCircle(q.x, q.y, q.r, fill)
        }
        if (boltL > 0 && bolt.size > 1) {
            paint.color = 0xFFFFF27A.toInt(); paint.alpha = 255; paint.strokeWidth = 5f * s
            for (i in 0 until bolt.size - 1) cv.drawLine(bolt[i].x, bolt[i].y, bolt[i + 1].x, bolt[i + 1].y, paint)
        }
        if (flash > 0) cv.drawColor(Color.argb(flash * 18, 255, 255, 255))
        postInvalidateOnAnimation()
    }
}
