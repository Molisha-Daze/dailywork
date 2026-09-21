package io.github.molishadaze.weijing.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun today(): String {
        return LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMATTER)
    }

    fun todayDate(): LocalDate {
        return LocalDate.now(ZoneId.systemDefault())
    }

    fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, DATE_FORMATTER)
    }

    fun formatDate(date: LocalDate): String {
        return date.format(DATE_FORMATTER)
    }
}
