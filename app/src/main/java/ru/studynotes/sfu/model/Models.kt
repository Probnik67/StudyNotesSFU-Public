package ru.studynotes.sfu.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

enum class WeekKind { ODD, EVEN, UNKNOWN }
enum class NoteStatus { LOCAL, WAITING_CONNECTION, UPLOADING, PROCESSING, DRAFT_READY, FINAL, FINAL_SYNCED }

data class Lesson(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val subject: String,
    val kind: String,
    val start: LocalTime,
    val end: LocalTime,
    val room: String = "",
    val teacher: String = "",
    val week: WeekKind = WeekKind.UNKNOWN,
    val source: String = "SFU"
)

data class UncertainRange(val start: Int, val end: Int)

data class PageLink(
    val imagePath: String,
    val textStart: Int,
    val textEnd: Int
)

data class SourceAnchor(
    val imagePath: String,
    val textStart: Int,
    val textEnd: Int,
    val yTop: Int,
    val yBottom: Int
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val lessonId: String?,
    val subject: String,
    val date: LocalDate,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val imagePaths: List<String> = emptyList(),
    val draftText: String = "",
    val finalText: String = "",
    val uncertainRanges: List<UncertainRange> = emptyList(),
    val pageLinks: List<PageLink> = emptyList(),
    val sourceAnchors: List<SourceAnchor> = emptyList(),
    val status: NoteStatus = NoteStatus.LOCAL
)

data class Homework(
    val id: String = UUID.randomUUID().toString(),
    val lessonId: String,
    val subject: String,
    val assignedDate: LocalDate,
    val dueDate: LocalDate,
    val text: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completed: Boolean = false
)
