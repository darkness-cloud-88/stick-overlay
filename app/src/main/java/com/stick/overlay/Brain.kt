package com.stick.overlay
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    val acts = "none, walk_left, walk_right, sit, stand, wave, jump, bolt, fire, ice, blast"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/"
    private var dbg = ""

    private fun call(url: String, key: String, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("x-goog-api-key", key)
        if (body != null) {
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return Pair(code, txt)
    }

    private fun msgOf(txt: String): String =
        try { JSONObject(txt).getJSONObject("error").optString("message") } catch (e: Exception) { txt }

    private fun discover(key: String): List<String> {
        val out = ArrayList<String>()
        try {
            val r = call(BASE + "models?pageSize=200", key, null)
            if (r.first !in 200..299) { dbg = "models " + r.first + ": " + msgOf(r.second); return out }
            val arr = JSONObject(r.second).getJSONArray("models")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.optString("name").removePrefix("models/")
                val m = o.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (m != null) for (j in 0 until m.length()) if (m.getString(j) == "generateContent") ok = true
                if (!ok || !n.startsWith("gemini") || !n.contains("flash")) continue
                if (listOf("image", "tts", "live", "audio", "robotics", "computer", "embedding", "native", "learn").any { n.contains(it) }) continue
                out.add(n)
            }
        } catch (e: Exception) { dbg = "net: " + e.javaClass.simpleName }
        return out.sortedWith(
            compareBy<String>({ if (it.contains("preview") || it.contains("exp")) 1 else 0 }, { if (it.contains("lite")) 1 else 0 })
                .thenByDescending { it })
    }

    fun ask(ctx: Context, key: String, system: String, user: String, cb: (String?, String?) -> Unit) {
        Thread {
            var res: String? = null
            var err: String? = null
            var lastCode = 0
            var lastMsg = ""
            try {
                val prefs = ctx.getSharedPreferences("stickfx", Context.MODE_PRIVATE)
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", system + "\n\nرسالة صديقك: " + user)))))
                    .put("generationConfig", JSONObject().put("maxOutputTokens", 1024))
                    .toString()
                val tried = HashSet<String>()
                fun attempt(m: String): Boolean {
                    if (!tried.add(m)) return false
                    val r = call(BASE + "models/" + m + ":generateContent", key, body)
                    if (r.first in 200..299) {
                        val t = try {
                            val parts = JSONObject(r.second).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                            val sb = StringBuilder()
                            for (k in 0 until parts.length()) sb.append(parts.getJSONObject(k).optString("text"))
                            sb.toString()
                        } catch (e: Exception) { "" }
                        if (t.isNotEmpty()) {
                            res = t
                            prefs.edit().putString("gmodel", m).apply()
                            return true
                        }
                        lastCode = 200
                        lastMsg = m + " empty"
                        return false
                    }
                    lastCode = r.first
                    lastMsg = msgOf(r.second)
                    if ((r.first == 400 || r.first == 401 || r.first == 403) && lastMsg.contains("API key", true)) { err = "key"; return true }
                    return false
                }
                val saved = prefs.getString("gmodel", null)
                var done = false
                if (saved != null) done = attempt(saved)
                if (!done) for (m in discover(key).take(6)) { if (attempt(m)) break }
                if (res == null && err == null) {
                    if (lastCode == 0) lastMsg = dbg
                    err = if (lastCode == 429) "quota" else "diag"
                }
            } catch (e: Exception) { err = "net" }
            if (res == null && err == "diag") {
                val m = ("خطأ " + lastCode + ": " + lastMsg).replace("\"", "'").replace("\\", "/").replace("\n", " ").take(80)
                res = "{\"say\":\"" + m + "\",\"do\":\"none\"}"
                err = null
            }
            cb(res, err)
        }.start()
    }
}
