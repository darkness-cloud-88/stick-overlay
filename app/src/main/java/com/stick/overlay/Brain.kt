package com.stick.overlay
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Brain {
    val acts = "none, walk_left, walk_right, sit, stand, wave, jump, bolt, fire, ice, blast"

    private val maker = "رسمني صديقي بيده! وهذا أجمل ما حدث لي"
    private val L = mapOf(
        "greet" to arrayOf("أهلاً يا {u}! مستعد لأي مغامرة!", "أوه.. أهلاً {u}.. مرحباً", "هاي {u}! هل جئت لتلعب؟", "مممم.. أهلاً.. كنت أغفو"),
        "howare" to arrayOf("أنا في أفضل حال! وأنت؟", "بخير.. لكن أخاف قليلاً", "ممتاز! أقفز من الفرح!", "متعب.. لكن بخير"),
        "name_q" to arrayOf("أنا {n}، البطل الشجاع!", "أنا {n}.. من فضلك كن لطيفاً", "أنا {n}! الأفضل في المزاح", "أنا {n}.. اسمي متعب قليلاً"),
        "maker" to arrayOf(maker, maker, maker, maker),
        "age" to arrayOf("عمري يوم واحد، لكني شجاع!", "لا أعرف.. أنا جديد", "عمري ثلاثة أيام ونصف!", "قديم جداً.. كثير النوم"),
        "doing" to arrayOf("أحرس هاتفك!", "أراقب بهدوء..", "أفكر في مقلب جديد!", "أحاول ألا أنام"),
        "thanks" to arrayOf("العفو! هذا واجبي", "أوه.. لا شكر على واجب", "هاها! بكل سرور!", "على الرحب.. الآن أنام"),
        "insult" to arrayOf("لن يؤثر فيّ كلامك!", "هذا يؤلمني.. لماذا؟", "هاها! ما زلت أحبك!", "آه.. سأتجاهل هذا"),
        "compliment" to arrayOf("أعرف! شكراً يا {u}", "أوه.. شكراً.. احمرّ وجهي", "هاها! ستفسدني بالمديح!", "لطيف منك.. شكراً"),
        "bye" to arrayOf("إلى اللقاء يا {u}!", "هل ستذهب؟ عد قريباً..", "باي باي! لا تتأخر!", "تصبح على خير.. زززز"),
        "play" to arrayOf("هيا نلعب! أنا جاهز!", "حسناً.. لكن بلطف", "نعم نعم نعم! لنلعب!", "ربما بعد قيلولة قصيرة"),
        "sleep" to arrayOf("الأبطال لا ينامون.. لكن قليلاً لا بأس", "نعم.. أريد غطاءً دافئاً", "لا! لنلعب أكثر!", "أخيراً! أحلام سعيدة"),
        "hungry" to arrayOf("أي طعام! أنا قوي", "أحب الكعك الصغير..", "بيتزا! بيتزا! بيتزا!", "الأكل ثم النوم.."),
        "sad" to arrayOf("لا تحزن! أنا معك", "أوه.. أنا أيضاً حزين معك", "تعال نلعب لتضحك!", "سأجلس بجانبك بهدوء")
    )
    private val JOKES = arrayOf(
        "لماذا ذهبت العصا إلى المدرسة؟ لتصبح عصا ذكية!",
        "ما أسرع عود في العالم؟ عود الثقاب!",
        "قال العود للعود: ما أطولك! فقال: وأنت ما أنحفك!")
    private val FB = arrayOf(
        arrayOf("هممم.. لم أفهم، لكني مستعد!", "قل لي ما تريد يا {u}", "اطلب وسأنفذ!"),
        arrayOf("لم أفهم.. هل تعيد؟", "أوه.. أسمع لكن لا أفهم", "ممكن أبسط؟"),
        arrayOf("هاها! لم أفهم لكنه مضحك!", "ماذا؟ قلها مرة ثانية!", "علّمني: تعلّم: كلمة = رد"),
        arrayOf("مممم؟ كنت أغفو.. ماذا؟", "لا أفهم.. أنا نعسان", "علّمني: تعلّم: كلمة = رد"))
    private val ACK = arrayOf("حاضر!", "حسناً.. سأحاول", "هاها! حاضر!", "حسناً.. ببطء")
    private val NE = arrayOf("نعم؟ أنا هنا!", "نعم..؟", "هاه؟ ماذا؟", "مممم؟")

    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val c = when (ch) {
                'أ', 'إ', 'آ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                else -> ch
            }
            if (c in '\u064B'..'\u065F' || c == '\u0670' || c == 'ـ') continue
            if (c.isLetterOrDigit() || c == ' ') sb.append(c) else sb.append(' ')
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun has(n: String, vararg ks: String): Boolean {
        val toks = n.split(" ")
        for (k in ks) {
            if (k.contains(" ")) { if (n.contains(k)) return true }
            else if (toks.any { it == k || (k.length >= 4 && it.contains(k)) }) return true
        }
        return false
    }

    private fun actOf(n: String): String = when {
        has(n, "اجلس", "جلوس", "ارتح") -> "sit"
        has(n, "قف", "انهض", "قم") -> "stand"
        has(n, "لوح", "سلم") -> "wave"
        has(n, "اقفز", "نط", "قفز") -> "jump"
        has(n, "يمين") -> "walk_right"
        has(n, "يسار") -> "walk_left"
        has(n, "صاعقه", "برق", "صاعق") -> "bolt"
        has(n, "نار", "احرق") -> "fire"
        has(n, "جليد", "ثلج", "جمد") -> "ice"
        has(n, "انفجار", "فجر") -> "blast"
        else -> "none"
    }

    private fun kindOf(n: String): String = when {
        n.isEmpty() -> "empty"
        has(n, "كيف حالك", "كيفك", "شلونك", "كيف الحال", "اخبارك", "علومك") -> "howare"
        has(n, "ما اسمك", "من انت", "شو اسمك", "ما هو اسمك", "عرف نفسك", "اسمك") -> "name_q"
        has(n, "من صنعك", "من برمجك", "من رسمك") -> "maker"
        has(n, "عمرك") -> "age"
        has(n, "ماذا تفعل", "شو تسوي", "وش تسوي", "ماذا تعمل", "تسوي") -> "doing"
        has(n, "نكته", "اضحكني", "نكت") -> "joke"
        has(n, "شكرا", "تسلم", "مشكور", "يعطيك العافيه") -> "thanks"
        has(n, "غبي", "سيء", "اكرهك", "بشع", "ضعيف", "فاشل", "اخرس") -> "insult"
        has(n, "جميل", "رائع", "قوي", "ذكي", "احبك", "ممتاز", "بطل", "لطيف", "حلو", "عظيم") -> "compliment"
        has(n, "وداعا", "مع السلامه", "الي اللقاء", "باي", "تصبح") -> "bye"
        has(n, "نلعب", "العب", "لعبه") -> "play"
        has(n, "نام", "نوم", "نعسان", "تعبان") -> "sleep"
        has(n, "جوعان", "جوع", "اكل", "اكله", "طعام") -> "hungry"
        has(n, "حزين", "زعلان", "حزينه") -> "sad"
        has(n, "مرحبا", "اهلا", "سلام", "هلا", "صباح", "مساء", "هاي") -> "greet"
        else -> ""
    }

    private fun learnedOf(p: SharedPreferences, name: String): JSONObject =
        try { JSONObject(p.getString("learn_" + name, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun <T> pickOf(a: Array<T>): T = a[(Math.random() * a.size).toInt().coerceIn(0, a.size - 1)]

    fun ask(ctx: Context, key: String, system: String, user: String, cb: (String?, String?) -> Unit) {
        val name = Regex("\"([^\"]+)\"").find(system)?.groupValues?.get(1) ?: ""
        val mood = when {
            system.contains("شجاع") -> 0
            system.contains("خجول") -> 1
            system.contains("مشاغب") -> 2
            system.contains("كسول") -> 3
            else -> 0
        }
        val prefs = ctx.getSharedPreferences("stickfx", Context.MODE_PRIVATE)
        var un = prefs.getString("uname", "") ?: ""
        val n0 = norm(user)
        val nn = norm(name)
        val n = if (nn.isNotEmpty()) n0.replace(nn, " ").trim().replace(Regex("\\s+"), " ") else n0
        fun fill(s: String): String = s.replace("{u}", if (un.isEmpty()) "صديقي" else un).replace("{n}", name)
        var say: String? = null
        var act = "none"

        if (say == null && user.contains("=") && n.contains("علم")) {
            val i = user.indexOf('=')
            var left = user.substring(0, i)
            val right = user.substring(i + 1).trim()
            if (name.isNotEmpty()) left = left.replace(name, " ")
            left = left.replace("تعلّم", " ").replace("تعلم", " ").replace("علّم", " ").replace("علم", " ").replace(":", " ")
            val k = norm(left)
            if (k.isNotEmpty() && right.isNotEmpty()) {
                val o = learnedOf(prefs, name)
                o.put(k, right)
                prefs.edit().putString("learn_" + name, o.toString()).apply()
                say = "تعلمت! عندما تقول «" + left.trim().replace(Regex("\\s+"), " ") + "» سأقول «" + right + "»"
            }
        }
        if (say == null && has(n, "انس", "انسي") && has(n, "كل", "الكل", "شيء")) {
            prefs.edit().remove("learn_" + name).apply()
            say = "نسيت كل ما علّمتني"
        }
        if (say == null) {
            val o = learnedOf(prefs, name)
            var best = ""
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (k.isNotEmpty() && n.contains(k) && k.length > best.length) best = k
            }
            if (best.isNotEmpty()) say = fill(o.optString(best))
        }
        if (say == null) {
            val m = Regex("اسمي (\\S+)").find(n0)
            if (m != null) {
                un = m.groupValues[1]
                prefs.edit().putString("uname", un).apply()
                say = fill("أهلاً يا {u}! سأتذكر اسمك")
            }
        }
        if (say == null) {
            val a = actOf(n)
            if (a != "none") { act = a; say = ACK[mood] }
        }
        if (say == null) {
            val kind = kindOf(n)
            val s0: String = when (kind) {
                "empty" -> NE[mood]
                "" -> pickOf(FB[mood])
                "joke" -> pickOf(JOKES)
                else -> L[kind]!![mood]
            }
            say = fill(s0)
        }
        cb(JSONObject().put("say", say ?: "").put("do", act).toString(), null)
    }
}
// END
