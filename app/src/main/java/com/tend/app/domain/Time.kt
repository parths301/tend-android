package com.tend.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Time {
    private val kickerFmt = DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.ENGLISH)
    private val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val stampFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)

    /** Epoch millis -> "Aug 5, 2:00 AM" */
    fun stamp(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(stampFmt)

    fun kicker(date: LocalDate): String = date.format(kickerFmt).uppercase(Locale.ENGLISH)
    fun shortDay(date: LocalDate): String = date.format(dayFmt)
    fun monthTitle(date: LocalDate): String = date.format(monthFmt)

    /** 450 -> "7:30" (no meridiem, matches the plan's left rail) */
    fun clock(min: Int): String {
        val h24 = min / 60
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "%d:%02d".format(h, min % 60)
    }

    /** 465 -> "7:45 AM" */
    fun clockAmPm(min: Int): String {
        val suffix = if (min / 60 >= 12) "PM" else "AM"
        return "${clock(min)} $suffix"
    }

    /** "7:30 – 7:45 AM · 15 min" */
    fun range(startMin: Int, endMin: Int): String =
        "${clock(startMin)} – ${clockAmPm(endMin)} · ${duration(endMin - startMin)}"

    fun duration(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    /** Hours label for minute totals: 576 -> "9.6h" */
    fun hours(minutes: Int): String = "%.1fh".format(minutes / 60.0)
}
