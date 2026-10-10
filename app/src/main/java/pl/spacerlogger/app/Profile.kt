package pl.spacerlogger.app

import android.content.Context

data class Profile(
    val weightKg: Double = 70.0,
    val heightCm: Double = 170.0,
    val age: Int = 30,
    val male: Boolean = true,
    /** 0 = siedzący, 1 = lekko aktywny, 2 = aktywny, 3 = bardzo aktywny */
    val lifestyle: Int = 0,
    /** średni dzienny ruch (minuty), poza rejestrowanymi spacerami */
    val dailyMinutes: Int = 30
)

object ProfileStore {
    fun load(c: Context): Profile {
        val sp = c.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val d = Profile()
        return Profile(
            sp.getFloat("weight", d.weightKg.toFloat()).toDouble(),
            sp.getFloat("height", d.heightCm.toFloat()).toDouble(),
            sp.getInt("age", d.age),
            sp.getBoolean("male", d.male),
            sp.getInt("lifestyle", d.lifestyle),
            sp.getInt("daily", d.dailyMinutes)
        )
    }

    fun save(c: Context, p: Profile) {
        c.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
            .putFloat("weight", p.weightKg.toFloat())
            .putFloat("height", p.heightCm.toFloat())
            .putInt("age", p.age)
            .putBoolean("male", p.male)
            .putInt("lifestyle", p.lifestyle)
            .putInt("daily", p.dailyMinutes)
            .apply()
    }
}
