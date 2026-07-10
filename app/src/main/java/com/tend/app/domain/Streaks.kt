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

    /**
     * Completion percentage over the trailing [window] days (inclusive of today).
     * [sinceDay] clips the window to the habit's creation day so young habits
     * aren't penalised for days on which they didn't exist yet.
     */
    fun rate(doneDays: Set<Long>, today: Long, window: Int = 30, sinceDay: Long = Long.MIN_VALUE): Int {
        val first = maxOf(today - window + 1, sinceDay)
        if (first > today) return 0
        val span = (today - first + 1).toInt().coerceAtLeast(1)
        val done = (first..today).count { it in doneDays }
        return (done * 100) / span
    }
}
