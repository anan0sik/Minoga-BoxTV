package com.minogatv.box.feature.channels

import android.content.Context
import java.text.Collator
import java.util.Locale

/**
 * Global channel sort mode (one for the whole app), persisted in
 * `minoga_tv_prefs` → `channel_sort_mode`.
 */
enum class ChannelSortMode(val buttonLabel: String, val menuLabel: String) {
    NONE("⇅ Как в плейлисте", "Без сортировки (как в плейлисте)"),
    AZ("⇅ А–Я", "А → Я"),
    ZA("⇅ Я–А", "Я → А"),
}

/**
 * Natural, locale-aware channel sorting shared by the channel list and the player
 * (so ▲/▼ zapping follows exactly the same order).
 *
 * - case-insensitive, `ё` = `е`;
 * - Cyrillic and Latin ordered alphabetically via [Collator] (ru);
 * - digit runs compared as numbers (`Канал 2` < `Канал 10`);
 * - stable: equal names keep playlist order.
 *
 * Must be called off the main thread for large lists.
 */
object ChannelSorter {

    private const val PREFS = "minoga_tv_prefs"
    private const val KEY_MODE = "channel_sort_mode"

    fun readMode(context: Context): ChannelSortMode {
        val raw = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        }.getOrNull()
        return ChannelSortMode.values().firstOrNull { it.name == raw } ?: ChannelSortMode.NONE
    }

    fun saveMode(context: Context, mode: ChannelSortMode) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
        }
    }

    /** Sorts [items] by the name returned from [nameOf] according to [mode]. */
    fun <T> sort(items: List<T>, mode: ChannelSortMode, nameOf: (T) -> String): List<T> {
        if (mode == ChannelSortMode.NONE || items.size < 2) return items
        // Collator is not thread-safe: create a fresh instance per sort call.
        val collator = Collator.getInstance(Locale("ru", "RU")).apply { strength = Collator.SECONDARY }
        val keyed = items.map { it to tokenize(nameOf(it)) }
        val cmp = Comparator<Pair<T, List<String>>> { a, b -> compareTokens(a.second, b.second, collator) }
        val sorted = keyed.sortedWith(if (mode == ChannelSortMode.ZA) cmp.reversed() else cmp)
        return sorted.map { it.first }
    }

    private fun tokenize(raw: String): List<String> {
        val s = raw.trim().lowercase(Locale("ru", "RU")).replace('ё', 'е')
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<String>(4)
        val sb = StringBuilder()
        var digitRun = s[0].isDigit()
        for (c in s) {
            val isDigit = c.isDigit()
            if (isDigit != digitRun && sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            digitRun = isDigit
            sb.append(c)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun compareTokens(a: List<String>, b: List<String>, collator: Collator): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i]
            val y = b[i]
            val xd = x[0].isDigit()
            val yd = y[0].isDigit()
            val c = when {
                xd && yd -> compareNumeric(x, y)
                xd -> -1 // numbers before letters
                yd -> 1
                else -> collator.compare(x, y)
            }
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }

    private fun compareNumeric(x: String, y: String): Int {
        val xs = x.trimStart('0')
        val ys = y.trimStart('0')
        if (xs.length != ys.length) return xs.length.compareTo(ys.length)
        val c = xs.compareTo(ys)
        return if (c != 0) c else x.length.compareTo(y.length)
    }
}
