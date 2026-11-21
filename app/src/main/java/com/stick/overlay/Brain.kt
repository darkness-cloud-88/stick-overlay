package com.stick.overlay
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    private val models = arrayOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite")
    val acts = "none, walk_left, walk_right, sit, stand, wave, jump, bolt, fire, ice, blast"

    fun ask(ctx: Context, key: String, system: String, user: String, cb: (String?, String?) -> Unit) {
        Thread {
            var res: String? = null
            var err: String? = null
            try {
                val prefs = ctx.getSharedPreferences("stickfx", Context.MODE_PRIVATE)
                val saved = prefs.getString("gmodel", null)
                val list = ArrayList<String>()
                if (saved != null) list.add(saved)
                for (m in models) if (m !in list) list.add(m)
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                    .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 512).put("temperature", 0.9))
                    .toString()
                for (m in list) {
                    val c = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent").openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 15000
                    c.readTimeout = 25000
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("x-goog-api-key", key)
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = c.responseCode
                    val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    c.disconnect()
                    if (code in 200..299) {
                        val parts = JSONObject(txt).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                        res = sb.toString()
                        prefs.edit().putString("gmodel", m).apply()
                        break
                    }
                    if (code == 404 || (code == 400 && txt.contains("model", true) && !txt.contains("API key", true))) continue
                    err = when (code) { 429 -> "quota"; 400, 401, 403 -> "key"; else -> "http" + code }
                    break
                }
                if (res == null && err == null) err = "model"
            } catch (e: Exception) { err = "net" }
            cb(res, err)
        }.start()
    }
}
