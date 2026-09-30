package ru.studynotes.sfu.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ru.studynotes.sfu.model.UncertainRange

private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == 'ё' || c == 'Ё'

fun normalizedUncertainRanges(text: String, ranges: List<UncertainRange>): List<UncertainRange> {
    if (text.isEmpty()) return emptyList()
    val expanded = ranges.mapNotNull { r ->
        var s = r.start.coerceIn(0, text.length)
        var e = r.end.coerceIn(s, text.length)
        if (s >= e) return@mapNotNull null
        while (s > 0 && isWordChar(text[s - 1])) s--
        while (e < text.length && isWordChar(text[e])) e++
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s < e) UncertainRange(s, e) else null
    }.sortedBy { it.start }

    if (expanded.isEmpty()) return emptyList()
    val merged = mutableListOf<UncertainRange>()
    for (r in expanded) {
        val last = merged.lastOrNull()
        if (last != null && r.start <= last.end) merged[merged.lastIndex] = UncertainRange(last.start, maxOf(last.end, r.end))
        else merged += r
    }
    return merged
}

class UncertainHighlightTransformation(private val ranges: List<UncertainRange>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text)
        normalizedUncertainRanges(text.text, ranges).forEach { r ->
            val s = r.start.coerceIn(0, text.length)
            val e = r.end.coerceIn(s, text.length)
            if (s < e) b.addStyle(
                SpanStyle(background = Color(0xFFFFF176), color = Color.Black),
                s, e
            )
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

fun adjustUncertainRanges(old: String, new: String, ranges: List<UncertainRange>): List<UncertainRange> {
    if (old == new) return ranges
    var prefix = 0
    val min = minOf(old.length, new.length)
    while (prefix < min && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < min - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++

    val oldEnd = old.length - suffix
    val newEnd = new.length - suffix
    val delta = newEnd - oldEnd

    return ranges.mapNotNull { r ->
        when {
            r.end <= prefix -> r
            r.start >= oldEnd -> UncertainRange((r.start + delta).coerceAtLeast(0), (r.end + delta).coerceAtLeast(0))
            else -> null
        }
    }
}

fun adjustSourceAnchors(
    old: String,
    new: String,
    anchors: List<ru.studynotes.sfu.model.SourceAnchor>
): List<ru.studynotes.sfu.model.SourceAnchor> {
    if (old == new) return anchors
    var prefix = 0
    val min = minOf(old.length, new.length)
    while (prefix < min && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < min - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++

    val oldEnd = old.length - suffix
    val newEnd = new.length - suffix
    val delta = newEnd - oldEnd

    return anchors.map { a ->
        when {
            a.textEnd <= prefix -> a
            a.textStart >= oldEnd -> a.copy(
                textStart = (a.textStart + delta).coerceAtLeast(0),
                textEnd = (a.textEnd + delta).coerceAtLeast(0)
            )
            else -> a.copy(
                textStart = minOf(a.textStart, prefix).coerceAtLeast(0),
                textEnd = maxOf(prefix, newEnd).coerceAtLeast(0)
            )
        }
    }
}
