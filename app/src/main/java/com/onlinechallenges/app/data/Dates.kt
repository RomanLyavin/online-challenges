package com.onlinechallenges.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    private val ru = Locale.forLanguageTag("ru")
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", ru)
    private val longFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)

    fun short(date: LocalDate): String = shortFormatter.format(date).replace(".", "")

    fun long(date: LocalDate): String = longFormatter.format(date).replace(".", "")

    fun day(date: LocalDate): String = dayFormatter.format(date).replace(".", "")

    fun range(start: LocalDate, end: LocalDate): String = when {
        start == end -> long(start)
        start.year == end.year -> "${day(start)} — ${long(end)}"
        else -> "${long(start)} — ${long(end)}"
    }

    fun shortRange(start: LocalDate, end: LocalDate): String =
        if (start == end) short(start) else "${short(start)} – ${short(end)}"

    fun daysInclusive(start: LocalDate, end: LocalDate): Long =
        ChronoUnit.DAYS.between(start, end) + 1

    fun daysBetween(start: LocalDate, end: LocalDate): Long =
        ChronoUnit.DAYS.between(start, end)

    fun todayIso(): String = LocalDate.now().toString()

    fun clamp(value: LocalDate, min: LocalDate, max: LocalDate): LocalDate = when {
        value.isBefore(min) -> min
        value.isAfter(max) -> max
        else -> value
    }
}
