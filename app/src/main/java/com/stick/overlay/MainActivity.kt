package com.stick.overlay
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File
import kotlin.math.*

object Store {
    val W = intArrayOf(200, 200, 100, 100, 100, 100)
    val H = intArrayOf(200, 300, 200, 200, 200, 200)
    val AX = floatArrayOf(100f, 100f, 50f, 50f, 50f, 50f)
    val AY = floatArrayOf(170f, 25f, 20f, 20f, 20f, 20f)
    val BX = floatArrayOf(100f, 100f, 50f, 50f, 50f, 50f)
    val BY = floatArrayOf(170f, 275f, 180f, 180f, 180f, 180f)
    val DW = floatArrayOf(30f, 50f, 64f, 64f, 48f, 48f)
    val NAMES = arrayOf("الرأس", "الجذع", "الذراع العلوي", "الساعد", "الفخذ", "الساق")

    private fun prefs(c: Context) = c.getSharedPreferences("chars", Context.MODE_PRIVATE)
    fun ids(c: Context): List<String> = (prefs(c).getString("ids", "") ?: "").split(",").filter { it.isNotEmpty() }
    fun name(c: Context, id: String): String = prefs(c).getString("name_$id", "صديق") ?: "صديق"

    fun save(c: Context, id: String, name: String, bm: Array<Bitmap>) {
        for (i in 0..5) File(c.filesDir, "c_${id}_$i.png").outputStream().use { bm[i].compress(Bitmap.CompressFormat.PNG, 100, it) }
        val l = ids(c).toMutableList()
        if (id !in l) l.add(id)
        prefs(c).edit().putString("ids", l.joinToString(",")).putString("name_$id", name).apply()
    }

    fun delete(c: Context, id: String) {
        for (i in 0..5) File(c.filesDir, "c_${id}_$i.png").delete()
        val l = ids(c).toMutableList()
        l.remove(id)
        prefs(c).edit().putString("ids", l.joinToString(",")).remove("name_$id").apply()
    }

    fun def(i: Int): Bitmap {
        val b = Bitmap.createBitmap(W[i], H[i], Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2F7DE1.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = DW[i]; p.strokeCap = Paint.Cap.ROUND
        if (i == 0) cv.drawCircle(100f, 115f, 50f, p) else cv.drawLine(AX[i], AY[i], BX[i], BY[i], p)
        return b
    }

    fun load(c: Context, id: String): Array<Bitmap> = Array(6) { i ->
        val f = File(c.filesDir, "c_${id}_$i.png")
        (if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null)?.copy(Bitmap.Config.ARGB_8888, true) ?: def(i)
    }
}

class DrawPad(ctx: Context) : View(ctx) {
    var bm: Bitmap = Store.def(0)
    var idx = 0
    var color = 0xFF1B2430.toInt()
    var size = 20f
    var erase = false
    var onChange: (() -> Unit)? = null
    private val hist = ArrayList<Bitmap>()
    private var bc = Canvas(bm)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val white = Paint().apply { color = Color.WHITE }
    private var lx = 0f
    private var ly = 0f
    private var sc = 1f
    private var ox = 0f
    private var oy = 0f

    fun set(i: Int, b: Bitmap) { idx = i; bm = b; bc = Canvas(b); hist.clear(); invalidate() }

    fun undo() {
        if (hist.isEmpty()) return
        val b = hist.removeAt(hist.size - 1)
        bm.eraseColor(0)
        bc.drawBitmap(b, 0f, 0f, null)
        invalidate(); onChange?.invoke()
    }

    fun clearAll() {
        hist.add(Bitmap.createBitmap(bm))
        bm.eraseColor(0)
        invalidate(); onChange?.invoke()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFFF2F4F7.toInt())
        sc = min(width.toFloat() / bm.width, height.toFloat() / bm.height)
        ox = (width - bm.width * sc) / 2f
        oy = (height - bm.height * sc) / 2f
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        c.drawRect(0f, 0f, bm.width.toFloat(), bm.height.toFloat(), white)
        c.drawBitmap(bm, 0f, 0f, null)
        fillP.color = 0xFFE5484D.toInt()
        c.drawCircle(Store.AX[idx], Store.AY[idx], 7f, fillP)
        if (idx != 0) {
            fillP.color = 0xFF2F7DE1.toInt()
            c.drawCircle(Store.BX[idx], Store.BY[idx], 7f, fillP)
        }
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = (e.x - ox) / sc
        val y = (e.y - oy) / sc
        paint.strokeWidth = size
        if (erase) paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        else { paint.xfermode = null; paint.color = color }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                hist.add(Bitmap.createBitmap(bm))
                if (hist.size > 12) hist.removeAt(0)
                lx = x; ly = y
                bc.drawPoint(x, y, paint)
            }
            MotionEvent.ACTION_MOVE -> { bc.drawLine(lx, ly, x, y, paint); lx = x; ly = y }
        }
        invalidate()
        onChange?.invoke()
        return true
    }
}

class PreviewView(ctx: Context) : View(ctx) {
    var bm: Array<Bitmap>? = null
    private val jx = FloatArray(11)
    private val jy = FloatArray(11)
    override fun onDraw(c: Canvas) {
        val b = bm ?: return
        c.drawColor(0xFFE9EDF2.toInt())
        val u = height / 100f
        Puppet.standing(jx, jy, width / 2f, height / 2f + 3f * u, u)
        Puppet.draw(c, b, jx, jy, 1f, u)
    }
}

class MainActivity : Activity() {
    private val MATCH = -1
    private val WRAP = -2
    private var inDraw = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, wt: Float = 0f, m: Int = 4): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, wt)
        p.setMargins(dp(m), dp(m), dp(m), dp(m))
        return p
    }

    private fun btn(t: String, bgc: Int = 0xFFDDE3EA.toInt(), fg: Int = 0xFF1B2430.toInt(), f: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t; v.setTextColor(fg); v.textSize = 15f; v.gravity = Gravity.CENTER
        v.setPadding(dp(14), dp(10), dp(14), dp(10))
        val g = GradientDrawable()
        g.setColor(bgc); g.cornerRadius = dp(12).toFloat()
        v.background = g
        v.setOnClickListener { f() }
        return v
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        showList()
    }

    override fun onBackPressed() {
        if (inDraw) showList() else super.onBackPressed()
    }

    private fun spawn(id: String) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل «العرض فوق التطبيقات» ثم اضغط تحرك", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java).putExtra("spawn", id))
        moveTaskToBack(true)
    }

    private fun showList() {
        inDraw = false
        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFFFFFFFF.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(24), dp(16), dp(24))
        val t = TextView(this)
        t.text = "أصدقاء العصا"; t.textSize = 26f; t.gravity = Gravity.CENTER; t.setTextColor(0xFF1B2430.toInt())
        col.addView(t)
        val h = TextView(this)
        h.text = "ارسم شخصيتك واحفظها، ثم اضغط «تحرك» لتظهر على شاشتك."
        h.gravity = Gravity.CENTER; h.setPadding(0, dp(8), 0, dp(12)); h.setTextColor(0xFF4A5565.toInt())
        col.addView(h)
        col.addView(btn("+ شخصية جديدة", 0xFF2F7DE1.toInt(), Color.WHITE) { showDraw(null) }, lp(MATCH, WRAP))
        col.addView(btn("إيقاف الأصدقاء") { stopService(Intent(this, OverlayService::class.java)) }, lp(MATCH, WRAP))
        for (id in Store.ids(this)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val n = TextView(this)
            n.text = Store.name(this, id); n.textSize = 18f; n.setTextColor(0xFF1B2430.toInt())
            row.addView(n, lp(0, WRAP, 1f))
            row.addView(btn("تحرك", 0xFF2FB36B.toInt(), Color.WHITE) { spawn(id) }, lp(WRAP, WRAP))
            row.addView(btn("تعديل") { showDraw(id) }, lp(WRAP, WRAP))
            row.addView(btn("حذف", 0xFFFBD5D5.toInt()) {
                AlertDialog.Builder(this).setMessage("حذف " + Store.name(this, id) + "؟")
                    .setPositiveButton("حذف") { _, _ -> Store.delete(this, id); showList() }
                    .setNegativeButton("إلغاء", null).show()
            }, lp(WRAP, WRAP))
            col.addView(row, lp(MATCH, WRAP, 0f, 6))
        }
        sv.addView(col)
        setContentView(sv)
    }

    private fun showDraw(id: String?) {
        inDraw = true
        val bms = if (id != null) Store.load(this, id) else Array(6) { Store.def(it) }
        var cur = 0
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(8), dp(8), dp(8), dp(8))
        root.setBackgroundColor(0xFFFFFFFF.toInt())
        val prev = PreviewView(this)
        prev.bm = bms
        root.addView(prev, lp(MATCH, dp(150), 0f, 2))
        val pad = DrawPad(this)
        pad.set(0, bms[0])
        pad.onChange = { prev.invalidate() }
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val chipViews = ArrayList<TextView>()
        fun mark() {
            for (i in chipViews.indices) {
                (chipViews[i].background as GradientDrawable).setColor(if (i == cur) 0xFF2F7DE1.toInt() else 0xFFDDE3EA.toInt())
                chipViews[i].setTextColor(if (i == cur) Color.WHITE else 0xFF1B2430.toInt())
            }
        }
        for (i in 0..5) {
            val c = btn(Store.NAMES[i]) { cur = i; pad.set(i, bms[i]); mark() }
            chipViews.add(c)
            chips.addView(c, lp(WRAP, WRAP))
        }
        mark()
        val hs = HorizontalScrollView(this)
        hs.addView(chips)
        root.addView(hs, lp(MATCH, WRAP, 0f, 0))
        val hint = TextView(this)
        hint.text = "🔴 مكان المفصل (ارسم نازلاً منه)   🔵 نهاية القطعة"
        hint.textSize = 12f; hint.setTextColor(0xFF4A5565.toInt()); hint.gravity = Gravity.CENTER
        root.addView(hint, lp(MATCH, WRAP, 0f, 2))
        root.addView(pad, lp(MATCH, 0, 1f, 2))

        val cols = longArrayOf(0xFF1B2430, 0xFF2F7DE1, 0xFFE5484D, 0xFF2FB36B, 0xFFF59E0B, 0xFF8B5CF6, 0xFFFFFFFF, 0xFFFF8AD8).map { it.toInt() }
        val sw = LinearLayout(this)
        sw.orientation = LinearLayout.HORIZONTAL
        for (cl in cols) {
            val v = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL; d.setColor(cl); d.setStroke(dp(2), 0xFF9AA5B1.toInt())
            v.background = d
            v.setOnClickListener { pad.color = cl; pad.erase = false }
            sw.addView(v, lp(dp(36), dp(36), 0f, 4))
        }
        val hs2 = HorizontalScrollView(this)
        hs2.addView(sw)
        root.addView(hs2, lp(MATCH, WRAP, 0f, 0))

        val tools = LinearLayout(this)
        tools.orientation = LinearLayout.HORIZONTAL
        tools.addView(btn("رفيع") { pad.size = 8f }, lp(WRAP, WRAP, 0f, 2))
        tools.addView(btn("وسط") { pad.size = 20f }, lp(WRAP, WRAP, 0f, 2))
        tools.addView(btn("سميك") { pad.size = 40f }, lp(WRAP, WRAP, 0f, 2))
        tools.addView(btn("ممحاة") { pad.erase = true }, lp(WRAP, WRAP, 0f, 2))
        tools.addView(btn("تراجع") { pad.undo() }, lp(WRAP, WRAP, 0f, 2))
        tools.addView(btn("مسح") { pad.clearAll() }, lp(WRAP, WRAP, 0f, 2))
        val hs3 = HorizontalScrollView(this)
        hs3.addView(tools)
        root.addView(hs3, lp(MATCH, WRAP, 0f, 0))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val ed = EditText(this)
        ed.hint = "اسم الشخصية"; ed.isSingleLine = true
        if (id != null) ed.setText(Store.name(this, id))
        row.addView(ed, lp(0, WRAP, 1f))
        row.addView(btn("حفظ", 0xFF2FB36B.toInt(), Color.WHITE) {
            val n = ed.text.toString().trim().ifEmpty { "صديق" }
            Store.save(this, id ?: System.currentTimeMillis().toString(), n, bms)
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
            showList()
        }, lp(WRAP, WRAP))
        row.addView(btn("رجوع") { showList() }, lp(WRAP, WRAP))
        root.addView(row, lp(MATCH, WRAP, 0f, 2))
        setContentView(root)
    }
}
