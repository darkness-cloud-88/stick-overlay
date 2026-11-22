package com.stick.overlay
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class NetActivity : Activity() {
    private val MATCH = -1
    private val WRAP = -2
    private var sub = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(m: Int = 6): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(MATCH, WRAP)
        p.setMargins(dp(m), dp(m), dp(m), dp(m))
        return p
    }

    private fun btn(t: String, bgc: Int = 0xFFDDE3EA.toInt(), fg: Int = 0xFF1B2430.toInt(), f: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t; v.setTextColor(fg); v.textSize = 16f; v.gravity = Gravity.CENTER
        v.setPadding(dp(14), dp(12), dp(14), dp(12))
        val g = GradientDrawable()
        g.setColor(bgc); g.cornerRadius = dp(12).toFloat()
        v.background = g
        v.setOnClickListener { f() }
        return v
    }

    private fun txt(t: String, size: Float = 15f): TextView {
        val v = TextView(this)
        v.text = t; v.textSize = size; v.setTextColor(0xFF1B2430.toInt()); v.gravity = Gravity.CENTER
        v.setPadding(0, dp(6), 0, dp(6))
        return v
    }

    private fun newCol(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(24), dp(16), dp(24))
        return c
    }

    private fun show(col: LinearLayout) {
        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.WHITE)
        sv.addView(col)
        setContentView(sv)
    }

    private fun needOverlay(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل «العرض فوق التطبيقات» ثم ارجع وأعد المحاولة", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return true
        }
        return false
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        home()
    }

    override fun onBackPressed() {
        if (sub) home() else super.onBackPressed()
    }

    private fun home() {
        sub = false
        val col = newCol()
        col.addView(txt("اللاعبون المتعددون", 26f))
        col.addView(txt("زر شخصيتك إلى هاتف صديقك، أو استقبل زواراً. يجب أن يكون الهاتفان على الواي فاي نفسه أو أحدهما على هوت سبوت الآخر."))
        col.addView(btn("استقبال زوار", 0xFF2FB36B.toInt(), Color.WHITE) { host() }, lp(10))
        col.addView(btn("زيارة صديق", 0xFF2F7DE1.toInt(), Color.WHITE) { visit() }, lp(10))
        col.addView(btn("إغلاق") { finish() }, lp(10))
        show(col)
    }

    private fun host() {
        sub = true
        val code = (1000 + (Math.random() * 9000).toInt()).toString()
        val col = newCol()
        col.addView(txt("استقبال الزوار", 24f))
        val ip = Net.ip()
        col.addView(txt(if (ip.isEmpty()) "لا يوجد اتصال بواي فاي أو هوت سبوت" else "عنوان هاتفك:  " + ip + "\nالرمز:  " + code, 20f), lp(12))
        col.addView(txt("أعطِ العنوان والرمز لصديقك. سيظهر لك سؤال قبل أن يدخل أي زائر."))
        col.addView(btn("بدء الاستقبال", 0xFF2FB36B.toInt(), Color.WHITE) {
            if (!needOverlay()) {
                startForegroundService(Intent(this, OverlayService::class.java).putExtra("host", code))
                Toast.makeText(this, "بدأ الاستقبال", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
        }, lp())
        col.addView(btn("رجوع") { home() }, lp())
        show(col)
    }

    private fun visit() {
        sub = true
        val col = newCol()
        col.addView(txt("زيارة صديق", 24f))
        val sp = getSharedPreferences("net", MODE_PRIVATE)
        val ip = EditText(this)
        ip.hint = "عنوان صديقك (مثل 192.168.1.20)"
        ip.setText(sp.getString("ip", ""))
        ip.isSingleLine = true
        val code = EditText(this)
        code.hint = "الرمز (4 أرقام)"
        code.inputType = InputType.TYPE_CLASS_NUMBER
        code.isSingleLine = true
        col.addView(ip, lp())
        col.addView(code, lp())
        col.addView(txt("اختر الشخصية التي تذهب بها:"))
        val ids = Store.ids(this)
        if (ids.isEmpty()) col.addView(txt("ارسم شخصية أولاً من التطبيق الرئيسي"))
        for (id in ids) {
            col.addView(btn("اذهب بـ " + Store.name(this, id), 0xFF2F7DE1.toInt(), Color.WHITE) {
                val a = ip.text.toString().trim()
                val c = code.text.toString().trim()
                if (a.isEmpty() || c.length != 4) {
                    Toast.makeText(this, "اكتب العنوان والرمز (4 أرقام)", Toast.LENGTH_SHORT).show()
                } else if (!needOverlay()) {
                    sp.edit().putString("ip", a).apply()
                    startForegroundService(Intent(this, OverlayService::class.java)
                        .putExtra("visit_ip", a).putExtra("visit_code", c).putExtra("visit_id", id))
                    moveTaskToBack(true)
                }
            }, lp())
        }
        col.addView(btn("رجوع") { home() }, lp())
        show(col)
    }
}
// END
