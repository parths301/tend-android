package com.tend.app.domain

object Streaks {
    /** Consecutive done days ending today, or ending yesterday when today is unchecked. */
    fun current(doneDays: Set<Long>, today: Long): Int {
        var day = if (today in doneDays) today else today - 1
        var streak = 0
        while (day in doneDays) {
            streak++
            day--
        }
        return streak
    }

    fun best(doneDays: Set<Long>): Int {
        if (doneDays.isEmpty()) return 0
        val sorted = doneDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Completion percentage over the trailing [window] days (inclusive of today). */
    fun rate(doneDays: Set<Long>, today: Long, window: Int = 30): Int {
        val done = ((today - window + 1)..today).count { it in doneDays }
        return (done * 100) / window
    }
}
