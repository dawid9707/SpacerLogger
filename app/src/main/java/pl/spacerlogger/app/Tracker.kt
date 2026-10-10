package pl.spacerlogger.app

import android.location.Location
import android.os.SystemClock

data class Pt(val lat: Double, val lon: Double)

enum class WalkState { IDLE, RUNNING, PAUSED }

/** Wspólny stan bieżącego spaceru (współdzielony przez Activity i usługę). Wszystko działa na głównym wątku. */
object Tracker {
    var state = WalkState.IDLE
        private set
    val points = ArrayList<Pt>()
    var distanceM = 0.0
        private set
    var calories = 0.0
        private set
    var startedAt = 0L
        private set
    var version = 0
        private set
    var weightKg = 70.0

    private var activeMs = 0L
    private var resumedAt = 0L
    private var last: Location? = null
    private val listeners = LinkedHashSet<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun changed() {
        version++
        ArrayList(listeners).forEach { it() }
    }

    private fun now() = SystemClock.elapsedRealtime()

    fun elapsedMs(): Long =
        activeMs + if (state == WalkState.RUNNING) now() - resumedAt else 0L

    fun start() {
        points.clear()
        distanceM = 0.0
        calories = 0.0
        activeMs = 0L
        last = null
        startedAt = System.currentTimeMillis()
        resumedAt = now()
        state = WalkState.RUNNING
        changed()
    }

    fun pause() {
        if (state != WalkState.RUNNING) return
        activeMs += now() - resumedAt
        last = null
        state = WalkState.PAUSED
        changed()
    }

    fun resume() {
        if (state != WalkState.PAUSED) return
        resumedAt = now()
        state = WalkState.RUNNING
        changed()
    }

    fun reset() {
        state = WalkState.IDLE
        points.clear()
        distanceM = 0.0
        calories = 0.0
        activeMs = 0L
        last = null
        changed()
    }

    fun onLocation(loc: Location) {
        if (state != WalkState.RUNNING) return
        if (loc.hasAccuracy() && loc.accuracy > 25f) return   // zbyt niedokładny odczyt

        val prev = last
        if (prev == null) {
            last = loc
            points.add(Pt(loc.latitude, loc.longitude))
            changed()
            return
        }
        val d = prev.distanceTo(loc).toDouble()
        if (d < 4.0) return                                    // drżenie GPS w miejscu
        val dt = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
        if (dt <= 0.0) return
        val v = d / dt
        if (v > 12.0) return                                   // nierealny „skok" pozycji

        distanceM += d
        calories += CalorieCalc.kcal(v * 3.6, weightKg, dt)
        last = loc
        points.add(Pt(loc.latitude, loc.longitude))
        changed()
    }

    fun snapshot() = Walk(startedAt, elapsedMs(), distanceM, calories, ArrayList(points))
}
