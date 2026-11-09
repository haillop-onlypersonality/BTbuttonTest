package kr.haillop.btbuttontest
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    private fun file(context: Context) = File(context.applicationContext.filesDir, "diagnostic_logs.json")
    @Synchronized fun add(context: Context, message: String) {
        val lines = readLines(context).toMutableList()
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        lines.add("$stamp $message")
        while (lines.size > 1000) lines.removeAt(0)
        write(context, lines)
    }
    @Synchronized fun json(context: Context): String {
        val arr = JSONArray()
        readLines(context).forEach { arr.put(it) }
        return JSONObject().put("schemaVersion", 2).put("updatedAt", System.currentTimeMillis()).put("logs", arr).toString(2)
    }
    @Synchronized fun clear(context: Context) {
        write(context, emptyList())
        add(context, "LOG cleared")
    }
    private fun readLines(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(f.readText()).optJSONArray("logs") ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }
        }.getOrDefault(emptyList())
    }
    private fun write(context: Context, lines: List<String>) {
        val arr = JSONArray()
        lines.forEach { arr.put(it) }
        file(context).writeText(JSONObject().put("schemaVersion", 2).put("updatedAt", System.currentTimeMillis()).put("logs", arr).toString(2))
    }
}
