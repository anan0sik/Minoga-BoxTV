package com.minogatv.box.feature.channels

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Russian date labels for EPG programmes (side panel, details card, player archive OSD).
 *
 * - today → only time `HH:mm–HH:mm`;
 * - yesterday / tomorrow → `Вчера` / `Завтра` prefix;
 * - other days → `Пн 10.11` prefix (by the programme's start day, so 23:30–01:00 belongs to its start day).
 *
 * Formatters are not thread-safe — call from the main (UI) thread only.
 */
object EpgDateFormat {

    private val ru = Locale("ru", "RU")
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayMonthFmt = SimpleDateFormat("dd.MM", ru)
    private val longDateFmt = SimpleDateFormat("d MMMM", ru)
    private val weekdayFullFmt = SimpleDateFormat("EEEE", ru)
    private val shortWeekdays = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    /** Comparable day key (year * 1000 + day of year) in the device time zone. */
    fun dayKey(ms: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /** Calendar day offset of [ms] relative to [nowMs] (0 = today, -1 = yesterday, 1 = tomorrow). */
    fun dayOffset(ms: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val a = Calendar.getInstance().apply { timeInMillis = nowMs; truncateToDay() }
        val b = Calendar.getInstance().apply { timeInMillis = ms; truncateToDay() }
        val diff = b.timeInMillis - a.timeInMillis
        // Round to tolerate DST shifts (23h / 25h days)
        return Math.round(diff / 86_400_000.0).toInt()
    }

    private fun Calendar.truncateToDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun time(ms: Long): String = timeFmt.format(Date(ms))

    /** `HH:mm–HH:mm` */
    fun timeRange(startMs: Long, endMs: Long, separator: String = "–"): String =
        "${time(startMs)}$separator${time(endMs)}"

    /** Day prefix for a programme start: "" (today), "Вчера", "Завтра" or "Пн 10.11". */
    fun dayPrefix(startMs: Long, nowMs: Long = System.currentTimeMillis()): String =
        when (dayOffset(startMs, nowMs)) {
            0 -> ""
            -1 -> "Вчера"
            1 -> "Завтра"
            else -> {
                val c = Calendar.getInstance().apply { timeInMillis = startMs }
                "${shortWeekdays[c.get(Calendar.DAY_OF_WEEK) - 1]} ${dayMonthFmt.format(Date(startMs))}"
            }
        }

    /** Full label: "21:00–22:30", "Вчера 21:00–22:30", "Пн 10.11 21:00–22:30". */
    fun programLabel(startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis(), separator: String = "–"): String {
        val prefix = dayPrefix(startMs, nowMs)
        val range = timeRange(startMs, endMs, separator)
        return if (prefix.isEmpty()) range else "$prefix $range"
    }

    /** Single timestamp with day prefix: "21:05:10", "Вчера 21:05:10", "Пн 10.11 21:05:10". */
    fun timestampLabel(ms: Long, withSeconds: Boolean = true, nowMs: Long = System.currentTimeMillis()): String {
        val t = SimpleDateFormat(if (withSeconds) "HH:mm:ss" else "HH:mm", Locale.getDefault()).format(Date(ms))
        val prefix = dayPrefix(ms, nowMs)
        return if (prefix.isEmpty()) t else "$prefix $t"
    }

    /** Day divider text: "Сегодня, среда 12 ноября", "Завтра, четверг 13 ноября", "Понедельник, 10 ноября". */
    fun dayDividerLabel(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
        val weekday = weekdayFullFmt.format(Date(ms)).lowercase(ru)
        val date = longDateFmt.format(Date(ms))
        return when (dayOffset(ms, nowMs)) {
            0 -> "Сегодня, $weekday $date"
            -1 -> "Вчера, $weekday $date"
            1 -> "Завтра, $weekday $date"
            else -> "${weekday.replaceFirstChar { it.titlecase(ru) }}, $date"
        }
    }
}
