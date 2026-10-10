package pl.spacerlogger.app

/** Kalorie = MET × waga [kg] × czas [h]; MET dobierany do prędkości marszu (wartości wg Compendium of Physical Activities). */
object CalorieCalc {
    fun met(speedKmh: Double): Double = when {
        speedKmh < 3.2 -> 2.3
        speedKmh < 4.0 -> 2.8
        speedKmh < 4.8 -> 3.0
        speedKmh < 5.6 -> 3.5
        speedKmh < 6.4 -> 4.3
        speedKmh < 8.0 -> 5.0
        else -> 8.0
    }

    fun kcal(speedKmh: Double, weightKg: Double, seconds: Double): Double =
        met(speedKmh) * weightKg * seconds / 3600.0
}
