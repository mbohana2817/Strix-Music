package com.blazify.music.utils

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Lightweight local listening streak. No account or network data is required. */
object StreakManager {
    private const val PREFS = "strix_streak"
    private const val LAST_DATE = "last_date"
    private const val CURRENT = "current"
    private const val BEST = "best"

    data class State(val current: Int, val best: Int)

    fun touchToday(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val last = prefs.getString(LAST_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var current = prefs.getInt(CURRENT, 0)
        var best = prefs.getInt(BEST, 0)
        current = when {
            last == today -> current.coerceAtLeast(1)
            last != null && ChronoUnit.DAYS.between(last, today) == 1L -> (current + 1).coerceAtMost(99)
            else -> 1
        }
        best = maxOf(best, current)
        prefs.edit().putString(LAST_DATE, today.toString()).putInt(CURRENT, current).putInt(BEST, best).apply()
        return State(current, best)
    }

    fun read(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(prefs.getInt(CURRENT, 0), prefs.getInt(BEST, 0))
    }
}
