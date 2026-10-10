package pl.spacerlogger.app

/**
 * Model spalania kalorii podczas marszu (wartość brutto, czyli razem z energią spoczynkową).
 *
 *  1. BMR (Mifflin-St Jeor) z wagi, wzrostu, wieku i płci → energia spoczynkowa na minutę.
 *  2. Koszt marszu zależy od prędkości (równanie ACSM: 0,1 ml O2/kg/min na m/min;
 *     między 6 a 8 km/h płynne przejście do kosztu biegu) i jest skalowany wagą.
 *  3. Współczynnik wytrenowania (±3%) z stylu życia i dziennego ruchu.
 *  4. Poprawka na długi marsz: +0,05% za każdą minutę ponad 30 (maks. +6%).
 *
 * Kalorie liczone są odcinek po odcinku (prędkość × czas), więc uwzględniają tempo,
 * przebyty dystans i czas trwania spaceru. Stałe poniżej to heurystyki – można je stroić.
 */
object CalorieCalc {
    private val PAL = doubleArrayOf(1.2, 1.4, 1.6, 1.8)

    private fun pal(p: Profile) = PAL[p.lifestyle.coerceIn(0, 3)]

    fun bmr(p: Profile): Double =
        10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + (if (p.male) 5 else -161)

    fun restPerMin(p: Profile): Double = bmr(p) / 1440.0

    /** Szacowane dzienne zapotrzebowanie: BMR × styl życia + dzienny ruch (ok. 3,5 MET). */
    fun tdee(p: Profile): Double =
        bmr(p) * pal(p) + p.dailyMinutes * 2.5 * restPerMin(p)

    private fun conditioning(p: Profile): Double {
        val palNorm = (pal(p) - 1.2) / 0.6
        val score = 0.5 * palNorm + 0.5 * (p.dailyMinutes.coerceAtMost(90) / 90.0)
        return 1.03 - 0.06 * score
    }

    private fun fatigue(activeMin: Double): Double =
        1.0 + ((activeMin - 30.0).coerceAtLeast(0.0) * 0.0005).coerceAtMost(0.06)

    /** Koszt tlenowy ponad spoczynek [ml/kg/min] dla prędkości v [m/min]. */
    private fun walkVo2(v: Double): Double = when {
        v <= 100.0 -> 0.1 * v
        v >= 134.0 -> 0.2 * v
        else -> 10.0 + (v - 100.0) / 34.0 * (26.8 - 10.0)
    }

    fun segmentKcal(p: Profile, speedKmh: Double, seconds: Double, activeMin: Double): Double {
        val v = speedKmh * 1000.0 / 60.0
        val active = walkVo2(v) * p.weightKg / 1000.0 * 5.0          // kcal/min ponad spoczynek
        return (restPerMin(p) + active * conditioning(p) * fatigue(activeMin)) * seconds / 60.0
    }
}
