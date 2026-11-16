package com.onlinechallenges.app.ui

import com.onlinechallenges.app.data.ChallengeGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun formatNumber(value: Double): String {
    val negative = value < 0
    val absolute = abs(value)
    val isWhole = abs(absolute - absolute.roundToLong()) < 0.001
    val text = if (isWhole) {
        absolute.roundToLong().toString()
    } else {
        String.format(Locale.US, "%.2f", absolute).trimEnd('0').trimEnd('.')
    }
    val parts = text.split(".")
    val grouped = parts[0].reversed().chunked(3).joinToString(" ").reversed()
    val result = if (parts.size > 1) "$grouped,${parts[1]}" else grouped
    return if (negative) "−$result" else result
}

fun formatTarget(goal: ChallengeGoal): String = "${formatNumber(goal.target)} ${goal.unit}".trim()

fun formatTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault(iso)
}

fun formatBirthDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "не указана"
    return runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))
    }.getOrDefault(iso)
}

fun ageFromBirthDate(iso: String?): Int? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val birth = LocalDate.parse(iso)
        val today = LocalDate.now()
        var age = today.year - birth.year
        if (today.dayOfYear < birth.dayOfYear) age--
        age.takeIf { it in 1..120 }
    }.getOrNull()
}
