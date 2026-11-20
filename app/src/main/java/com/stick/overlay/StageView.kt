package com.stick.overlay
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class Pt(var x: Float, var y: Float) { var px = x; var py = y }
class Fig(val team: Int, val slot: Int, val name: String, val mood: Int, val p: Array<Pt>, val c: List<FloatArray>) {
    var hp = 100f; var stun = 30; var frz = 0; var burn = 0
    var t = 0f; var cd = 30; var atk = 0; var face = 1f; var dead = 0
    var target: Fig? = null; var tt = 0
    var grab: PointF? = null
    var say = ""; var sayT = 0
    var wx = 0f; var wt = 0
    var sit = 0; var idleT = 300
    var handle: View? = null
    var hlp: WindowManager.LayoutParams? = null
    var hx = -1; var hy = -1; var hw = -1; var hh = -1
}
class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var l: Int, val c: Int, val r: Float)
class Ring(val x: Float, val y: Float, var r: Float, val m: Float, val c: Int)

class StageView(ctx: Context) : View(ctx) {
    private val dn = ctx.resources.displayMetrics.density
    private val s = dn * 0.9f
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = ctx.getSharedPreferences("stick", Context.MODE_PRIVATE)
    private val defNames = arrayOf("زيد", "سالم", "ليلى", "كرم", "نور", "هدى")
    private val slotMood = intArrayOf(0, 1, 2, 3, 0, 2)
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
    private val showLines = arrayOf("شاهد هذا!", "انظر ماذا أفعل!", "أنا أتدرب فقط", "لا تقلق، لن أؤذي أحداً")
    private val fightLines = arrayOf("أنا مستعد!", "أرجوك لا تؤلمني..", "هيا نلعب بقوة!", "حسناً.. لكن بسرعة")
    private val hurtLines = arrayOf("هذا لا يؤلم!", "آآه! كفى!", "هاها! أخطأت!", "آه.. أنا نعسان")
    private val off = arrayOf(
        floatArrayOf(0f, -44f), floatArrayOf(0f, -30f), floatArrayOf(0f, 0f), floatArrayOf(-12f, -20f),
        floatArrayOf(-22f, -8f), floatArrayOf(12f, -20f), floatArrayOf(22f, -8f), floatArrayOf(-6f, 20f),
        floatArrayOf(-8f, 40f), floatArrayOf(6f, 20f), floatArrayOf(8f, 40f))
    private val lk = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(1, 3), intArrayOf(3, 4), intArrayOf(1, 5),
        intArrayOf(5, 6), intArrayOf(2, 7), intArrayOf(7, 8), intArrayOf(2, 9), intArrayOf(9, 10))
    private val col = intArrayOf(0xFF2F7DE1.toInt(), 0xFFE5484D.toInt())
    private val figs = ArrayList<Fig>()
    private val parts = ArrayList<Part>()
    private val rings = ArrayList<Ring>()
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
    var fight = false
    private val ground: Float get() = height - 56f * dn

    private fun rnd() = Math.random().toFloat()
    private fun say(f: Fig, t: String, n: Int) { f.say = t; f.sayT = n }

    fun toggleFight(): Boolean {
        fight = !fight
        for (f in figs) if (f.hp > 0) {
            f.sit = 0; f.target = null; f.atk = 0
            if (fight) say(f, fightLines[f.mood], 70)
            else { f.hp = 100f; f.burn = 0; f.frz = 0; say(f, "عدنا أصدقاء!", 60) }
        }
        return fight
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (figs.isEmpty() && w > 0) { addFig(0); addFig(1) }
    }

    override fun onDetachedFromWindow() {
        for (f in figs) removeHandle(f)
        super.onDetachedFromWindow()
    }

    fun addNext() {
        val used = figs.filter { it.hp > 0 }.map { it.slot }
        val slot = (0..5).firstOrNull { it !in used } ?: return
        addFig(slot)
    }

    private fun addFig(slot: Int) {
        val x = width * (0.15f + rnd() * 0.7f)
        val y0 = ground - 130f * s
        val p = Array(11) { Pt(x + off[it][0] * s, y0 + off[it][1] * s) }
        val c = lk.map { l -> floatArrayOf(l[0].toFloat(), l[1].toFloat(), hypot(p[l[0]].x - p[l[1]].x, p[l[0]].y - p[l[1]].y)) }
        var name = prefs.getString("n$slot", defNames[slot]) ?: defNames[slot]
        if (name.isBlank()) name = defNames[slot]
        val f = Fig(slot % 2, slot, name, slotMood[slot], p, c)
        figs.add(f)
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
                    f.grab = PointF(e.rawX, e.rawY)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(e.rawX - sx, e.rawY - sy) > 12f * dn) moved = true
                    f.grab?.set(e.rawX, e.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    f.grab = null
                    if (!moved && e.eventTime - downT < 300 && f.hp > 0) {
                        val arr = pokeLines[f.mood]
                        say(f, arr[(rnd() * arr.size).toInt().coerceIn(0, arr.size - 1)], 80)
                        f.sit = 0
                        if (f.mood == 2) for (q in f.p) q.py += 7f * s
                    }
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            (50f * dn).toInt(), (100f * dn).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        for (q in f.p) { minX = min(minX, q.x); minY = min(minY, q.y); maxX = max(maxX, q.x); maxY = max(maxY, q.y) }
        val pad = 16f * dn
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

    private fun hit(f: Fig, d: Float, vx: Float, vy: Float, st: Int) {
        f.hp -= d
        f.sit = 0
        f.stun = max(f.stun, st)
        for (q in f.p) { q.px -= vx; q.py -= vy }
        if (f.hp > 0 && f.sayT <= 0 && rnd() < .3f) say(f, hurtLines[f.mood], 50)
    }

    fun power(kind: String) {
        val alive = figs.filter { it.hp > 0 }
        if (alive.isEmpty()) return
        val user = alive.random()
        if (!fight) {
            val x = (user.p[2].x + user.face * 110f * s).coerceIn(30f, max(30f, width - 30f))
            val y = ground - 30f * s
            say(user, showLines[(rnd() * showLines.size).toInt().coerceIn(0, showLines.size - 1)], 70)
            when (kind) {
                "bolt" -> {
                    val pts = ArrayList<PointF>()
                    for (i in 0..8) pts.add(PointF(if (i == 8) x else x + (rnd() - .5f) * 40f * s, y * i / 8f))
                    bolt = pts; boltL = 9; flash = 3
                    part(x, y, 0xFFFFF27A.toInt(), 12, 6f)
                }
                "fire" -> part(x, y, 0xFFFF8A1F.toInt(), 22, 6f)
                "ice" -> { rings.add(Ring(x, y, 10f * s, 90f * s, 0xFF7FD4FF.toInt())); part(x, y, 0xFFBFEAFF.toInt(), 14, 5f) }
                "blast" -> { rings.add(Ring(x, y, 10f * s, 120f * s, 0xFFFF9A3C.toInt())); part(x, y, 0xFFFFB347.toInt(), 18, 8f) }
            }
            return
        }
        val tg = user.target?.takeIf { it.hp > 0 } ?: alive.firstOrNull { it.team != user.team } ?: return
        val x = tg.p[2].x
        val y = tg.p[2].y
        say(user, "خذ هذا!", 50)
        when (kind) {
            "bolt" -> {
                val pts = ArrayList<PointF>()
                for (i in 0..8) pts.add(PointF(if (i == 8) x else x + (rnd() - .5f) * 40f * s, y * i / 8f))
                bolt = pts; boltL = 9; flash = 6
                hit(tg, 35f, (rnd() - .5f) * 4f * s, -9f * s, 70)
                part(x, y, 0xFFFFF27A.toInt(), 18, 8f)
            }
            "fire" -> tg.burn = 240
            "ice" -> {
                tg.frz = 200
                rings.add(Ring(x, y, 10f * s, 90f * s, 0xFF7FD4FF.toInt()))
                part(x, y, 0xFFBFEAFF.toInt(), 14, 5f)
            }
            "blast" -> {
                rings.add(Ring(x, y, 10f * s, 140f * s, 0xFFFF9A3C.toInt()))
                for (f in figs) {
                    var hurt = false
                    for (q in f.p) {
                        val dx = q.x - x
                        val dy = q.y - y
                        val d = hypot(dx, dy)
                        if (d < 140f * s) {
                            val k = (1f - d / (140f * s)) * 16f * s
                            val dd = max(d, 1f)
                            q.px -= dx / dd * k
                            q.py -= dy / dd * k - 1f
                            hurt = true
                        }
                    }
                    if (hurt) { f.hp -= 14f; f.stun = max(f.stun, 60) }
                }
                part(x, y, 0xFFFFB347.toInt(), 24, 10f)
            }
        }
    }

    private fun step(f: Fig) {
        val p = f.p
        for (q in p) {
            val vx = (q.x - q.px) * .985f
            val vy = (q.y - q.py) * .985f
            q.px = q.x; q.py = q.y
            q.x += vx; q.y += vy + .7f * s
        }
        if (f.stun > 0) f.stun--
        if (f.cd > 0) f.cd--
        if (f.sayT > 0) f.sayT--
        if (f.idleT > 0) f.idleT--
        val gr = f.grab
        if (gr != null && f.hp > 0) {
            val nk = p[1]
            nk.x += (gr.x - nk.x) * .5f
            nk.y += (gr.y - nk.y) * .5f
            f.stun = max(f.stun, 8)
            f.sit = 0
        }
        val frozen = f.frz > 0
        if (frozen) { f.frz--; for (q in p) { q.px = q.x; q.py = q.y } }
        if (f.burn > 0) {
            f.burn--; f.hp -= .25f
            if (f.burn % 4 == 0) { val q = p[(rnd() * 11).toInt().coerceIn(0, 10)]; part(q.x, q.y, 0xFFFF8A1F.toInt(), 1, 2f) }
        }
        val g = ground
        val w = width.toFloat()
        if (f.hp > 0 && f.stun <= 0 && !frozen) {
            val hip = p[2]; val hd = p[0]; val nk = p[1]
            val sit = f.sit > 0 && f.target == null
            val hh = if (sit) 24f else 40f
            if (hip.y > g - 75f * s) hip.y += ((g - hh * s) - hip.y) * .2f
            nk.x += (hip.x - nk.x) * .25f; nk.y += ((hip.y - 30f * s) - nk.y) * .25f
            hd.x += (hip.x - hd.x) * .25f; hd.y += ((hip.y - 44f * s) - hd.y) * .25f
            if (fight) {
                f.tt--
                if (f.tt <= 0) {
                    f.tt = 15
                    var b: Fig? = null
                    var bd = 1e9f
                    for (o in figs) if (o.team != f.team && o.hp > 0) {
                        val d = abs(o.p[2].x - hip.x)
                        if (d < bd) { bd = d; b = o }
                    }
                    f.target = b
                }
            } else f.target = null
            val spd = when (f.mood) { 0 -> 1.6f; 1 -> 1.3f; 2 -> 2.0f; else -> .9f }
            val cdMax = when (f.mood) { 0 -> 40; 1 -> 70; 2 -> 45; else -> 80 }
            val dmg = when (f.mood) { 0 -> 11f; 1 -> 6f; 2 -> 8f; else -> 9f }
            var dir = 0f
            val tg = f.target
            if (tg != null && tg.hp > 0) {
                val dx = tg.p[2].x - hip.x
                f.face = if (dx >= 0f) 1f else -1f
                val flee = f.mood == 1 && f.hp < 60f
                if (flee) dir = -f.face else if (abs(dx) > 40f * s) dir = f.face
                if (dir != 0f) hip.x += dir * spd * s
                if (!flee && abs(dx) < 52f * s && abs(tg.p[2].y - hip.y) < 60f * s && f.cd <= 0 && f.atk <= 0) { f.atk = 14; f.cd = cdMax }
            } else {
                f.wt--
                if (f.wt <= 0) {
                    if (rnd() < .3f) { f.sit = 150 + (rnd() * 250).toInt(); f.wt = f.sit + 40 }
                    else { f.wx = w * (.12f + rnd() * .76f); f.wt = 120 + (rnd() * 200).toInt() }
                }
                if (f.sit > 0) f.sit--
                else {
                    val dx = f.wx - hip.x
                    if (abs(dx) > 20f * s) { dir = if (dx > 0f) 1f else -1f; f.face = dir; hip.x += dir * spd * .5f * s }
                }
            }
            if (hip.x < w * .08f) hip.x += .8f * s
            if (hip.x > w * .92f) hip.x -= .8f * s
            if (!fight && f.idleT <= 0) {
                f.idleT = 600 + (rnd() * 900).toInt()
                if (f.sayT <= 0 && f.grab == null) {
                    val arr = idleLines[f.mood]
                    say(f, arr[(rnd() * arr.size).toInt().coerceIn(0, arr.size - 1)], 110)
                }
            }
            if (f.mood == 2 && !sit && rnd() < .004f && hip.y > g - 50f * s) { for (q in p) q.py += 11f * s }
            if (dir != 0f) f.t += .22f
            for (sd in 0..1) {
                val ph = f.t + sd * Math.PI.toFloat()
                val lift = if (dir != 0f) max(0f, sin(ph)) * 12f * s else 0f
                val ft = p[if (sd == 1) 10 else 8]
                val kn = p[if (sd == 1) 9 else 7]
                val tx = if (sit) hip.x + f.face * (if (sd == 1) 26f else 20f) * s
                         else hip.x + (if (sd == 1) 9f else -9f) * s + (if (dir != 0f) cos(ph) * 10f * s * dir else 0f)
                ft.x += (tx - ft.x) * .3f; ft.y += ((g - lift) - ft.y) * .4f
                if (sit) {
                    kn.x += (((hip.x + ft.x) / 2f) - kn.x) * .25f
                    kn.y += (((hip.y + ft.y) / 2f - 8f * s) - kn.y) * .25f
                } else {
                    kn.x += (((hip.x + ft.x) / 2f + dir * 5f * s) - kn.x) * .25f
                    kn.y += (((hip.y + ft.y) / 2f) - kn.y) * .25f
                }
            }
            val sh = p[1]
            for (sd in 0..1) {
                val el = p[if (sd == 1) 5 else 3]
                val ha = p[if (sd == 1) 6 else 4]
                var ex = sh.x + (if (sd == 1) 10f else -10f) * s
                var ey = sh.y + 12f * s
                var hx = sh.x + (if (sd == 1) 15f else -15f) * s
                var hy = sh.y + 26f * s
                var k = .25f
                if (f.atk > 0 && sd == 1 && tg != null) {
                    hx = tg.p[1].x - f.face * 10f * s; hy = tg.p[1].y
                    ex = (sh.x + hx) / 2f; ey = (sh.y + hy) / 2f; k = .6f
                }
                el.x += (ex - el.x) * .3f; el.y += (ey - el.y) * .3f
                ha.x += (hx - ha.x) * k; ha.y += (hy - ha.y) * k
            }
            if (f.atk > 0) {
                f.atk--
                if (f.atk == 7 && tg != null && tg.hp > 0 && abs(tg.p[2].x - hip.x) < 64f * s) {
                    hit(tg, dmg, f.face * 3.5f * s, -3f * s, 14)
                    for (q in p) q.px += f.face * 1.2f * s
                    part(tg.p[1].x, tg.p[1].y, 0xFFFFCF33.toInt(), 6, 5f)
                }
            }
        }
        repeat(6) {
            for (c in f.c) {
                val a = p[c[0].toInt()]; val b = p[c[1].toInt()]
                val dx = b.x - a.x; val dy = b.y - a.y
                val d = max(hypot(dx, dy), .001f)
                val df = (d - c[2]) / d * .5f
                a.x += dx * df; a.y += dy * df; b.x -= dx * df; b.y -= dy * df
            }
            for (q in p) {
                if (q.y > g) { q.y = g; q.px += (q.x - q.px) * .4f }
                if (q.x < 8f) { q.x = 8f; q.px = q.x }
                if (q.x > w - 8f) { q.x = w - 8f; q.px = q.x }
            }
        }
    }

    private fun tick() {
        for (f in figs) {
            if (f.hp <= 0) { f.dead++; if (f.handle != null) removeHandle(f) }
            step(f)
        }
        figs.removeAll { it.dead > 260 }
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
        for (f in figs) {
            val p = f.p
            val a = if (f.dead > 200) max(0f, 1f - (f.dead - 200) / 60f) else 1f
            paint.color = when {
                f.frz > 0 -> 0xFF7FD4FF.toInt()
                f.burn > 0 -> 0xFFFF8A1F.toInt()
                else -> col[f.team]
            }
            paint.alpha = (a * 255).toInt()
            paint.strokeWidth = 6f * s
            for (l in lk) cv.drawLine(p[l[0]].x, p[l[0]].y, p[l[1]].x, p[l[1]].y, paint)
            cv.drawCircle(p[0].x, p[0].y, 10f * s, paint)
            if (f.hp > 0) {
                if (fight) {
                    fill.color = col[f.team]
                    val x0 = p[0].x - 17f * s
                    cv.drawRect(x0, p[0].y - 26f * s, x0 + 34f * s * f.hp.coerceIn(0f, 100f) / 100f, p[0].y - 22f * s, fill)
                }
                cv.drawText(f.name, p[0].x, p[0].y - 31f * s, tp)
                if (f.sayT > 0) {
                    val tw = bt.measureText(f.say)
                    val half = tw / 2f + 14f * dn
                    val bx = p[0].x.coerceIn(half, max(half, width - half))
                    val by = p[0].y - 56f * s
                    cv.drawRoundRect(RectF(bx - half, by - 18f * dn, bx + half, by + 8f * dn), 14f * dn, 14f * dn, bg)
                    cv.drawText(f.say, bx, by, bt)
                }
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
