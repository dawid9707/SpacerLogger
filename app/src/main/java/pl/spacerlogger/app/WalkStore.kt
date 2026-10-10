package pl.spacerlogger.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Walk(
    val startedAt: Long,
    val durationMs: Long,
    val distanceM: Double,
    val calories: Double,
    val points: List<Pt>
)

/** Prosta historia spacerów zapisywana w pliku JSON w pamięci prywatnej aplikacji. */
object WalkStore {
    private fun file(c: Context) = File(c.filesDir, "walks.json")

    fun load(c: Context): MutableList<Walk> {
        val f = file(c)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val p = o.getJSONArray("p")
                val pts = ArrayList<Pt>(p.length() / 2)
                var k = 0
                while (k + 1 < p.length()) {
                    pts.add(Pt(p.getDouble(k), p.getDouble(k + 1)))
                    k += 2
                }
                Walk(
                    o.getLong("t"), o.getLong("d"),
                    o.getDouble("m"), o.getDouble("c"), pts
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun add(c: Context, w: Walk) {
        val list = load(c)
        list.add(w)
        val arr = JSONArray()
        list.forEach { x ->
            val flat = JSONArray()
            x.points.forEach { flat.put(it.lat); flat.put(it.lon) }
            arr.put(
                JSONObject()
                    .put("t", x.startedAt).put("d", x.durationMs)
                    .put("m", x.distanceM).put("c", x.calories).put("p", flat)
            )
        }
        file(c).writeText(arr.toString())
    }

    fun clear(c: Context) {
        file(c).delete()
    }
}
