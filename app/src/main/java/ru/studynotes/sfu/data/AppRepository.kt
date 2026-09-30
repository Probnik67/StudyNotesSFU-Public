package ru.studynotes.sfu.data

import ru.studynotes.sfu.schedule.SfuScheduleClient
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.studynotes.sfu.model.*
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import kotlin.math.abs

data class OpenAiTopUp(val time: LocalDateTime, val amountUsd: Double)
data class OpenAiOperationUsage(val inputTokens: Long, val cachedInputTokens: Long, val outputTokens: Long, val costUsd: Double)

class AppRepository(private val context: Context) {
    private val stateFile = File(context.filesDir, "state.json")

    var lessons: MutableList<Lesson> = mutableListOf()
        private set
    private var schedulesBySubgroup: MutableMap<Int, MutableList<Lesson>> = mutableMapOf()
    private var originalSchedulesBySubgroup: MutableMap<Int, MutableList<Lesson>> = mutableMapOf()
    // Последние реальные изменения, обнаруженные между двумя выдачами СФУ.
    // Для изменённого слота храним предыдущую официальную пару; для новой пары — только ключ слота.
    private var officialPreviousLessonsBySubgroup: MutableMap<Int, MutableMap<String, Lesson>> = mutableMapOf()
    private var officialAddedSlotsBySubgroup: MutableMap<Int, MutableSet<String>> = mutableMapOf()
    // Новые будущие даты, впервые опубликованные СФУ. Это не считаем изменением уже составленного расписания.
    private var officialPublishedSlotsBySubgroup: MutableMap<Int, MutableSet<String>> = mutableMapOf()
    private var lessonOverridesBySubgroup: MutableMap<Int, MutableMap<String, Lesson>> = mutableMapOf()
    private var manualLessonsBySubgroup: MutableMap<Int, MutableMap<String, Lesson>> = mutableMapOf()
    private var lastScheduleSyncBySubgroup: MutableMap<Int, LocalDateTime> = mutableMapOf()
    var selectedSubgroup: Int = 1
        private set
    var notes: MutableList<Note> = mutableListOf()
        private set
    var noNotesLessonIds: MutableSet<String> = mutableSetOf()
        private set
    var homeworks: MutableList<Homework> = mutableListOf()
        private set
    var noHomeworkLessonIds: MutableSet<String> = mutableSetOf()
        private set
    var lessonNotes: MutableMap<String, String> = mutableMapOf()
        private set
    private var lessonNoteDrafts: MutableMap<String, String> = mutableMapOf()
    private var homeworkDraftTexts: MutableMap<String, String> = mutableMapOf()
    private var homeworkDraftDueDates: MutableMap<String, LocalDate> = mutableMapOf()
    var currentWeek: WeekKind = WeekKind.UNKNOWN
        private set
    var lastScheduleSync: LocalDateTime? = null
        private set
    var backendUrl: String = "https://study-notes-sfu-backend.vercel.app"
        private set
    var updateChannel: String = "stable"
        private set

    fun setUpdateChannel(channel: String) {
        updateChannel = if (channel.equals("test", ignoreCase = true)) "test" else "stable"
        save()
    }

    // Локальная статистика обращений именно к нашему Vercel backend.
    // Hobby использует rolling 30-day window, поэтому храним отдельные события и
    // автоматически исключаем всё, что старше 30 дней.
    private var hobbyRequestEvents: MutableList<Pair<LocalDateTime, Double>> = mutableListOf()
    val hobbyEstimatedInvocations: Long
        get() { pruneHobbyEvents(); return hobbyRequestEvents.size.toLong() }
    val hobbyEstimatedWallSeconds: Double
        get() { pruneHobbyEvents(); return hobbyRequestEvents.sumOf { it.second } }
    var localOpenAiInputTokens: Long = 0
        private set
    var localOpenAiCachedInputTokens: Long = 0
        private set
    var localOpenAiOutputTokens: Long = 0
        private set
    var localOpenAiCostUsd: Double = 0.0
        private set
    // Cumulative usage last seen for each backend operation. Recording only the positive
    // delta makes success/error reconciliation idempotent and prevents double charging.
    private var openAiOperationUsage: MutableMap<String, OpenAiOperationUsage> = mutableMapOf()

    // The user originally funded the API with $10.00. This is only the local ledger's
    // starting point; the user can calibrate it to the real OpenAI balance at any time.
    val openAiInitialBalanceUsd: Double = 10.0
    private var openAiBalanceCorrectionUsd: Double = 0.0
    private var openAiTopUps: MutableList<OpenAiTopUp> = mutableListOf()
    val openAiTopUpHistory: List<OpenAiTopUp>
        get() = openAiTopUps.sortedByDescending { it.time }
    val openAiTotalTopUpsUsd: Double
        get() = openAiTopUps.sumOf { it.amountUsd }
    val estimatedOpenAiBalanceUsd: Double
        get() = (openAiInitialBalanceUsd + openAiTotalTopUpsUsd + openAiBalanceCorrectionUsd - localOpenAiCostUsd).coerceAtLeast(0.0)

    val selectedGroupName: String
        get() = "ИТ00-00БЭК (${selectedSubgroup} подгруппа)"

    init { load() }

    fun setSelectedSubgroup(subgroup: Int) {
        require(subgroup == 1 || subgroup == 2)
        if (selectedSubgroup == subgroup) return
        selectedSubgroup = subgroup
        lessons = schedulesBySubgroup[subgroup]?.toMutableList() ?: mutableListOf()
        lastScheduleSync = lastScheduleSyncBySubgroup[subgroup]
        save()
    }

    fun setBackendUrl(url: String) {
        backendUrl = url.trim().trimEnd('/')
        save()
    }

    private fun pruneHobbyEvents(now: LocalDateTime = LocalDateTime.now()) {
        val cutoff = now.minusDays(30)
        hobbyRequestEvents.removeAll { it.first.isBefore(cutoff) }
    }

    fun recordCloudRequest(elapsedSeconds: Double) {
        if (!backendUrl.contains(".vercel.app", ignoreCase = true)) return
        pruneHobbyEvents()
        hobbyRequestEvents += LocalDateTime.now() to elapsedSeconds.coerceAtLeast(0.0)
        save()
    }

    fun recordOpenAiUsage(inputTokens: Int, cachedInputTokens: Int, outputTokens: Int, costUsd: Double) {
        localOpenAiInputTokens += inputTokens.coerceAtLeast(0)
        localOpenAiCachedInputTokens += cachedInputTokens.coerceAtLeast(0)
        localOpenAiOutputTokens += outputTokens.coerceAtLeast(0)
        localOpenAiCostUsd += costUsd.coerceAtLeast(0.0)
        save()
    }

    fun recordOpenAiOperationUsage(operationId: String, inputTokens: Int, cachedInputTokens: Int, outputTokens: Int, costUsd: Double) {
        if (operationId.isBlank()) {
            recordOpenAiUsage(inputTokens, cachedInputTokens, outputTokens, costUsd)
            return
        }
        val current = OpenAiOperationUsage(
            inputTokens.coerceAtLeast(0).toLong(),
            cachedInputTokens.coerceAtLeast(0).toLong(),
            outputTokens.coerceAtLeast(0).toLong(),
            costUsd.coerceAtLeast(0.0)
        )
        val previous = openAiOperationUsage[operationId] ?: OpenAiOperationUsage(0, 0, 0, 0.0)
        localOpenAiInputTokens += (current.inputTokens - previous.inputTokens).coerceAtLeast(0)
        localOpenAiCachedInputTokens += (current.cachedInputTokens - previous.cachedInputTokens).coerceAtLeast(0)
        localOpenAiOutputTokens += (current.outputTokens - previous.outputTokens).coerceAtLeast(0)
        localOpenAiCostUsd += (current.costUsd - previous.costUsd).coerceAtLeast(0.0)
        openAiOperationUsage[operationId] = current
        // Bound persistent dedup history without affecting accounting totals.
        if (openAiOperationUsage.size > 1000) {
            openAiOperationUsage.entries.take(openAiOperationUsage.size - 1000).map { it.key }.forEach { openAiOperationUsage.remove(it) }
        }
        save()
    }

    /** Calibrate the local ledger to the balance currently shown by OpenAI.
     *  Existing token/cost statistics and top-up history are preserved. */
    fun setCurrentOpenAiBalance(balanceUsd: Double) {
        val target = balanceUsd.coerceAtLeast(0.0)
        val beforeCorrection = openAiInitialBalanceUsd + openAiTotalTopUpsUsd - localOpenAiCostUsd
        openAiBalanceCorrectionUsd = target - beforeCorrection
        save()
    }

    /** Record a real account top-up. It immediately increases the estimated balance and
     *  remains permanently visible in the top-up history. */
    fun addOpenAiTopUp(amountUsd: Double) {
        val amount = amountUsd.coerceAtLeast(0.0)
        if (amount < 0.005) return
        openAiTopUps += OpenAiTopUp(LocalDateTime.now(), amount)
        save()
    }

    val hobbyEstimatedProvisionedMemoryGbHours: Double
        get() = 2.0 * hobbyEstimatedWallSeconds / 3600.0

    // Active CPU фактически меньше wall time, потому что ожидание ответа OpenAI не расходует Active CPU.
    // Поэтому здесь intentionally conservative upper-bound: если даже он далёк от лимита, запас точно есть.
    val hobbyEstimatedCpuHoursUpperBound: Double
        get() = hobbyEstimatedWallSeconds / 3600.0


    fun scheduleForSubgroup(subgroup: Int): List<Lesson> = schedulesBySubgroup[subgroup]?.toList() ?: emptyList()

    private fun slotKey(lesson: Lesson): String =
        "${lesson.date}|${lesson.start}|${lesson.end}"

    private fun slotKey(date: LocalDate, start: LocalTime, end: LocalTime): String =
        "$date|$start|$end"

    fun lessonsForDate(subgroup: Int, date: LocalDate): List<Lesson> =
        scheduleForSubgroup(subgroup).filter { it.date == date }.sortedBy { it.start }

    fun subjectTeacherOptions(subgroup: Int = selectedSubgroup): List<Pair<String, String>> =
        (originalSchedulesBySubgroup[subgroup].orEmpty() + scheduleForSubgroup(subgroup))
            .map { it.subject.trim() to it.teacher.trim() }
            .filter { it.first.isNotBlank() }
            .distinct()
            .sortedWith(compareBy<Pair<String, String>> { it.first.lowercase() }.thenBy { it.second.lowercase() })

    fun isLessonOverridden(subgroup: Int, lessonId: String): Boolean {
        val current = scheduleForSubgroup(subgroup).firstOrNull { it.id == lessonId } ?: return false
        return lessonOverridesBySubgroup[subgroup]?.containsKey(slotKey(current)) == true
    }

    fun originalLessonFor(subgroup: Int, lessonId: String): Lesson? {
        val current = scheduleForSubgroup(subgroup).firstOrNull { it.id == lessonId } ?: return null
        return originalSchedulesBySubgroup[subgroup]?.firstOrNull { slotKey(it) == slotKey(current) }
    }

    fun applyLessonOverride(subgroup: Int, lessonId: String, subject: String, teacher: String): Lesson? {
        val schedule = schedulesBySubgroup[subgroup] ?: return null
        val index = schedule.indexOfFirst { it.id == lessonId }
        if (index < 0) return null
        val current = schedule[index]
        val original = originalSchedulesBySubgroup[subgroup]?.firstOrNull { slotKey(it) == slotKey(current) } ?: current
        val replacement = original.copy(
            id = current.id,
            subject = subject.trim().ifBlank { current.subject },
            teacher = teacher.trim(),
            source = "LOCAL_OVERRIDE"
        )
        lessonOverridesBySubgroup.getOrPut(subgroup) { mutableMapOf() }[slotKey(original)] = replacement
        schedule[index] = replacement
        if (subgroup == selectedSubgroup) lessons = schedule.toMutableList()
        save()
        return replacement
    }

    fun restoreLessonOverride(subgroup: Int, lessonId: String): Lesson? {
        val schedule = schedulesBySubgroup[subgroup] ?: return null
        val index = schedule.indexOfFirst { it.id == lessonId }
        if (index < 0) return null
        val current = schedule[index]
        val key = slotKey(current)
        val original = originalSchedulesBySubgroup[subgroup]?.firstOrNull { slotKey(it) == key } ?: return null
        lessonOverridesBySubgroup[subgroup]?.remove(key)
        val restored = original.copy(id = current.id)
        schedule[index] = restored
        if (subgroup == selectedSubgroup) lessons = schedule.toMutableList()
        save()
        return restored
    }

    fun addManualLesson(
        subgroup: Int,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        subject: String,
        teacher: String
    ): Lesson {
        val key = slotKey(date, start, end)
        val lesson = Lesson(
            date = date,
            subject = subject.trim(),
            kind = "занятие",
            start = start,
            end = end,
            teacher = teacher.trim(),
            source = "LOCAL_INSERT"
        )
        manualLessonsBySubgroup.getOrPut(subgroup) { mutableMapOf() }[key] = lesson
        val schedule = schedulesBySubgroup.getOrPut(subgroup) { mutableListOf() }
        schedule.removeAll { slotKey(it) == key }
        schedule.add(lesson)
        schedule.sortWith(compareBy<Lesson> { it.date }.thenBy { it.start })
        if (subgroup == selectedSubgroup) lessons = schedule.toMutableList()
        save()
        return lesson
    }

    fun isManualLesson(subgroup: Int, lessonId: String): Boolean {
        val current = scheduleForSubgroup(subgroup).firstOrNull { it.id == lessonId } ?: return false
        return manualLessonsBySubgroup[subgroup]?.containsKey(slotKey(current)) == true
    }

    fun removeManualLesson(subgroup: Int, lessonId: String): Boolean {
        val schedule = schedulesBySubgroup[subgroup] ?: return false
        val lesson = schedule.firstOrNull { it.id == lessonId } ?: return false
        val key = slotKey(lesson)
        val removed = manualLessonsBySubgroup[subgroup]?.remove(key) != null
        if (removed) {
            schedule.removeAll { it.id == lessonId || slotKey(it) == key }
            originalSchedulesBySubgroup[subgroup]?.firstOrNull { slotKey(it) == key }?.let { schedule.add(it) }
            schedule.sortWith(compareBy<Lesson> { it.date }.thenBy { it.start })
            if (subgroup == selectedSubgroup) lessons = schedule.toMutableList()
            save()
        }
        return removed
    }

    fun lessonByIdAnySubgroup(lessonId: String): Lesson? =
        schedulesBySubgroup.values.asSequence().flatten().firstOrNull { it.id == lessonId }

    fun subgroupForLessonId(lessonId: String?): Int? {
        if (lessonId == null) return null
        return schedulesBySubgroup.entries.firstOrNull { (_, list) -> list.any { it.id == lessonId } }?.key
    }

    fun notesForSubgroup(subgroup: Int): List<Note> = notes.filter { note ->
        val sg = subgroupForLessonId(note.lessonId)
        sg == subgroup || (sg == null && note.lessonId == null)
    }


    fun noNoteLessonsForSubgroup(subgroup: Int): List<Lesson> =
        scheduleForSubgroup(subgroup).filter { it.id in noNotesLessonIds }

    fun futureLessonDatesForSubject(lesson: Lesson): Set<LocalDate> {
        val subgroup = subgroupForLessonId(lesson.id) ?: selectedSubgroup

        fun norm(value: String): String = value.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        fun equivalent(a: String, b: String): Boolean {
            val x = norm(a)
            val y = norm(b)
            if (x.isBlank() || y.isBlank()) return false
            if (x == y || x.contains(y) || y.contains(x)) return true
            val xt = x.split(' ').filter { it.length >= 4 }.toSet()
            val yt = y.split(' ').filter { it.length >= 4 }.toSet()
            if (xt.isEmpty() || yt.isEmpty()) return false
            val common = xt.intersect(yt).size.toDouble()
            return common / minOf(xt.size, yt.size) >= 0.6
        }

        // Календарь ДЗ использует только занятия, которые уже присутствуют в
        // актуально загруженном расписании СФУ, плюс локальные ручные правки.
        // Никаких вычислений повторяемости, чётности или продолжения серий дат.
        return scheduleForSubgroup(subgroup)
            .asSequence()
            .filter { equivalent(it.subject, lesson.subject) }
            .map { it.date }
            .filter { it.isAfter(lesson.date) }
            .toSet()
    }

    private fun officialContentSignature(lesson: Lesson): String = listOf(
        lesson.subject.trim(),
        lesson.kind.trim(),
        lesson.teacher.trim(),
        lesson.room.trim(),
        lesson.week.name
    ).joinToString("|")

    private fun recordOfficialScheduleChanges(newLessons: List<Lesson>, subgroup: Int) {
        val previous = originalSchedulesBySubgroup[subgroup].orEmpty()
        if (previous.isEmpty()) return

        val oldBySlot = previous.associateBy(::slotKey)
        val newBySlot = newLessons.associateBy(::slotKey)
        val changedPrevious = mutableMapOf<String, Lesson>()
        val addedSlots = mutableSetOf<String>()
        val publishedSlots = mutableSetOf<String>()
        val previousDates = previous.map { it.date }.toSet()

        newBySlot.forEach { (key, current) ->
            val old = oldBySlot[key]
            when {
                // Если самой даты раньше вообще не было в загруженном расписании, это публикация
                // нового дня/недели, а не изменение уже составленного расписания.
                old == null && current.date !in previousDates -> publishedSlots += key
                old == null -> addedSlots += key
                officialContentSignature(old) != officialContentSignature(current) -> changedPrevious[key] = old
            }
        }

        // Если на сайте действительно появилась новая ревизия, заменяем маркеры последнего изменения.
        // При обычном обновлении без изменений старые зелёные маркеры не исчезают.
        val removedCount = oldBySlot.keys.count { it !in newBySlot }
        if (changedPrevious.isNotEmpty() || addedSlots.isNotEmpty() || removedCount > 0 || publishedSlots.isNotEmpty()) {
            officialPreviousLessonsBySubgroup[subgroup] = changedPrevious
            officialAddedSlotsBySubgroup[subgroup] = addedSlots
            officialPublishedSlotsBySubgroup[subgroup] = publishedSlots
        }
    }

    fun isOfficiallyChanged(subgroup: Int, lesson: Lesson): Boolean {
        val key = slotKey(lesson)
        // «Изменено» только когда в уже существовавшем слоте поменялась сама пара.
        // Новая пара в пустом слоте отображается отдельно как «Добавлено СФУ».
        return officialPreviousLessonsBySubgroup[subgroup]?.containsKey(key) == true
    }

    fun previousOfficialLesson(subgroup: Int, lesson: Lesson): Lesson? =
        officialPreviousLessonsBySubgroup[subgroup]?.get(slotKey(lesson))

    fun wasOfficiallyAdded(subgroup: Int, lesson: Lesson): Boolean =
        officialAddedSlotsBySubgroup[subgroup]?.contains(slotKey(lesson)) == true

    fun wasOfficiallyPublished(subgroup: Int, lesson: Lesson): Boolean =
        officialPublishedSlotsBySubgroup[subgroup]?.contains(slotKey(lesson)) == true

    fun officialScheduleChangeMessage(newLessons: List<Lesson>, subgroup: Int = selectedSubgroup): String {
        val previous = originalSchedulesBySubgroup[subgroup].orEmpty()
        if (previous.isEmpty()) return "Расписание СФУ загружено. Предыдущей версии для сравнения нет."

        fun signature(lesson: Lesson): String = listOf(
            lesson.date.toString(),
            lesson.start.toString(),
            lesson.end.toString(),
            lesson.subject.trim(),
            lesson.kind.trim(),
            lesson.teacher.trim(),
            lesson.room.trim(),
            lesson.week.name
        ).joinToString("|")

        val oldSet = previous.map(::signature).toSet()
        val newSet = newLessons.map(::signature).toSet()
        if (oldSet == newSet) return "Расписание обновлено. На сайте СФУ изменений нет."

        val added = (newSet - oldSet).size
        val removed = (oldSet - newSet).size
        return buildString {
            append("Расписание обновлено. СФУ изменил расписание")
            if (added > 0 || removed > 0) {
                append(": ")
                val parts = mutableListOf<String>()
                if (added > 0) parts += "новых/изменённых записей — $added"
                if (removed > 0) parts += "убрано/заменено — $removed"
                append(parts.joinToString(", "))
            }
            append(".")
        }
    }

    fun replaceSchedule(newLessons: List<Lesson>, week: WeekKind, subgroup: Int = selectedSubgroup) {
        recordOfficialScheduleChanges(newLessons, subgroup)
        val originals = newLessons.toMutableList()
        originalSchedulesBySubgroup[subgroup] = originals
        val overrides = lessonOverridesBySubgroup[subgroup].orEmpty()
        val effective = originals.map { original ->
            val override = overrides[slotKey(original)]
            if (override != null) override.copy(
                date = original.date,
                start = original.start,
                end = original.end,
                kind = original.kind,
                room = original.room,
                week = original.week
            ) else original
        }.toMutableList()
        manualLessonsBySubgroup[subgroup].orEmpty().forEach { (key, manual) ->
            if (effective.none { slotKey(it) == key }) effective.add(manual)
        }
        effective.sortWith(compareBy<Lesson> { it.date }.thenBy { it.start })
        schedulesBySubgroup[subgroup] = effective
        val syncedAt = LocalDateTime.now()
        lastScheduleSyncBySubgroup[subgroup] = syncedAt
        currentWeek = week
        if (subgroup == selectedSubgroup) {
            lessons = effective.toMutableList()
            lastScheduleSync = syncedAt
        }
        save()
    }

    fun createNoteForLesson(lesson: Lesson): Note {
        notes.firstOrNull { it.lessonId == lesson.id }?.let { return it }
        noNotesLessonIds.remove(lesson.id)
        val note = Note(lessonId = lesson.id, subject = lesson.subject, date = lesson.date)
        notes.add(note)
        ensureNoteFolder(note.id)
        save()
        return note
    }

    fun createManualNote(subject: String, date: LocalDate = LocalDate.now()): Note {
        val note = Note(lessonId = null, subject = subject, date = date)
        notes.add(note)
        ensureNoteFolder(note.id)
        save()
        return note
    }

    fun deleteNote(noteId: String): Boolean {
        val note = notes.firstOrNull { it.id == noteId } ?: return false
        notes.removeAll { it.id == noteId }
        runCatching { File(context.filesDir, "notes/${note.id}").deleteRecursively() }
        save()
        return true
    }

    fun markNoNotes(lessonId: String) {
        if (notes.none { it.lessonId == lessonId }) {
            noNotesLessonIds.add(lessonId)
            save()
        }
    }

    fun clearNoNotes(lessonId: String) {
        if (noNotesLessonIds.remove(lessonId)) save()
    }

    /**
     * Сбрасывает только пользовательские отметки «Записей не было».
     * Конспекты, фотографии, ДЗ, заметки, черновики, расписание и настройки не затрагиваются.
     * Возвращает количество снятых отметок.
     */
    fun resetNoNotesMarks(): Int {
        val count = noNotesLessonIds.size
        if (count > 0) {
            noNotesLessonIds.clear()
            save()
        }
        return count
    }

    fun noNotesMarksCount(): Int = noNotesLessonIds.size

    fun hasNoNotes(lessonId: String): Boolean = lessonId in noNotesLessonIds

    fun addHomework(lesson: Lesson, text: String, dueDate: LocalDate): Homework {
        noHomeworkLessonIds.remove(lesson.id)
        val homework = Homework(
            lessonId = lesson.id,
            subject = lesson.subject,
            assignedDate = lesson.date,
            dueDate = dueDate,
            text = text.trim()
        )
        homeworks.add(homework)
        save()
        return homework
    }

    fun updateHomework(id: String, text: String, dueDate: LocalDate) {
        val index = homeworks.indexOfFirst { it.id == id }
        if (index >= 0) {
            homeworks[index] = homeworks[index].copy(text = text.trim(), dueDate = dueDate)
            save()
        }
    }

    fun setHomeworkCompleted(id: String, completed: Boolean) {
        val index = homeworks.indexOfFirst { it.id == id }
        if (index >= 0) {
            homeworks[index] = homeworks[index].copy(completed = completed)
            save()
        }
    }

    fun deleteHomework(id: String) {
        if (homeworks.removeAll { it.id == id }) save()
    }

    fun homeworkForLesson(lessonId: String): List<Homework> = homeworks.filter { it.lessonId == lessonId }

    fun markNoHomework(lessonId: String) {
        if (homeworks.none { it.lessonId == lessonId }) {
            noHomeworkLessonIds.add(lessonId)
            save()
        }
    }

    fun clearNoHomework(lessonId: String) {
        if (noHomeworkLessonIds.remove(lessonId)) save()
    }

    fun hasNoHomework(lessonId: String): Boolean = lessonId in noHomeworkLessonIds

    fun setLessonNote(lessonId: String, text: String) {
        val value = text.trim()
        if (value.isBlank()) lessonNotes.remove(lessonId) else lessonNotes[lessonId] = value
        save()
    }

    fun lessonNote(lessonId: String): String = lessonNotes[lessonId].orEmpty()

    fun setLessonNoteDraft(lessonId: String, text: String) {
        if (text.isBlank()) lessonNoteDrafts.remove(lessonId) else lessonNoteDrafts[lessonId] = text
        save()
    }

    fun lessonNoteDraft(lessonId: String): String = lessonNoteDrafts[lessonId].orEmpty()

    fun clearLessonNoteDraft(lessonId: String) {
        if (lessonNoteDrafts.remove(lessonId) != null) save()
    }

    fun setHomeworkDraft(lessonId: String, text: String, dueDate: LocalDate?) {
        if (text.isBlank()) homeworkDraftTexts.remove(lessonId) else homeworkDraftTexts[lessonId] = text
        if (dueDate == null) homeworkDraftDueDates.remove(lessonId) else homeworkDraftDueDates[lessonId] = dueDate
        save()
    }

    fun homeworkDraftText(lessonId: String): String = homeworkDraftTexts[lessonId].orEmpty()
    fun homeworkDraftDueDate(lessonId: String): LocalDate? = homeworkDraftDueDates[lessonId]

    fun clearHomeworkDraft(lessonId: String) {
        val changed = homeworkDraftTexts.remove(lessonId) != null || homeworkDraftDueDates.remove(lessonId) != null
        if (changed) save()
    }

    fun nextImageFile(noteId: String): File {
        val original = File(ensureNoteFolder(noteId), "original").apply { mkdirs() }
        val count = original.listFiles()?.size ?: 0
        return File(original, "page_${(count + 1).toString().padStart(3, '0')}.jpg")
    }

    fun addImage(noteId: String, path: String) = updateNote(noteId) { n ->
        n.copy(imagePaths = n.imagePaths + path, status = NoteStatus.LOCAL)
    }

    /** Reorders original pages and, when a draft already exists, reorders the corresponding
     * text blocks and source mappings immediately without another OpenAI request. */
    fun moveNotePage(noteId: String, fromIndex: Int, toIndex: Int) {
        val note = noteById(noteId) ?: return
        if (fromIndex !in note.imagePaths.indices || toIndex !in note.imagePaths.indices || fromIndex == toIndex) return
        val oldPaths = note.imagePaths
        val newPaths = oldPaths.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }

        if (note.draftText.isBlank() || note.pageLinks.isEmpty()) {
            updateNote(noteId) { it.copy(imagePaths = newPaths) }
            return
        }

        // Each pageLink describes the logical text belonging to an original page. Build page
        // chunks from the old draft, then concatenate them in the new image order.
        data class Chunk(val path: String, val start: Int, val end: Int, val text: String)
        val chunks = oldPaths.mapNotNull { path ->
            val links = note.pageLinks.filter { it.imagePath == path }
            if (links.isEmpty()) null else {
                val start = links.minOf { it.textStart }.coerceIn(0, note.draftText.length)
                val end = links.maxOf { it.textEnd }.coerceIn(start, note.draftText.length)
                Chunk(path, start, end, note.draftText.substring(start, end).trim())
            }
        }.associateBy { it.path }
        if (chunks.size != oldPaths.size) {
            updateNote(noteId) { it.copy(imagePaths = newPaths) }
            return
        }

        val out = StringBuilder()
        val newLinks = mutableListOf<PageLink>()
        val newAnchors = mutableListOf<SourceAnchor>()
        newPaths.forEachIndexed { index, path ->
            val chunk = chunks.getValue(path)
            if (index > 0) out.append("\n\n")
            val newStart = out.length
            out.append(chunk.text)
            val newEnd = out.length
            newLinks += PageLink(path, newStart, newEnd)
            note.sourceAnchors.filter { it.imagePath == path }.forEach { a ->
                val relStart = (a.textStart - chunk.start).coerceAtLeast(0)
                val relEnd = (a.textEnd - chunk.start).coerceAtLeast(relStart)
                newAnchors += a.copy(
                    textStart = (newStart + relStart).coerceAtMost(newEnd),
                    textEnd = (newStart + relEnd).coerceAtMost(newEnd)
                )
            }
        }
        updateNote(noteId) { it.copy(imagePaths = newPaths, draftText = out.toString(), pageLinks = newLinks, sourceAnchors = newAnchors) }
    }

    fun updateDraft(
        noteId: String,
        text: String,
        uncertain: List<UncertainRange>,
        pageLinks: List<PageLink>,
        sourceAnchors: List<SourceAnchor>
    ) = updateNote(noteId) { n ->
        n.copy(
            draftText = text,
            uncertainRanges = uncertain,
            pageLinks = pageLinks,
            sourceAnchors = sourceAnchors,
            status = NoteStatus.DRAFT_READY
        )
    }

    fun saveEditedDraft(noteId: String, text: String, uncertain: List<UncertainRange>, sourceAnchors: List<SourceAnchor>) =
        updateNote(noteId) { n -> n.copy(draftText = text, uncertainRanges = uncertain, sourceAnchors = sourceAnchors) }

    fun makeFinal(noteId: String, text: String) = updateNote(noteId) { n ->
        n.copy(finalText = text, uncertainRanges = emptyList(), status = NoteStatus.FINAL)
    }

    fun setStatus(noteId: String, status: NoteStatus) = updateNote(noteId) { it.copy(status = status) }

    fun noteById(id: String) = notes.firstOrNull { it.id == id }
    fun noteForLesson(lessonId: String) = notes.firstOrNull { it.lessonId == lessonId }

    private fun updateNote(id: String, transform: (Note) -> Note) {
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            notes[index] = transform(notes[index])
            save()
        }
    }

    private fun ensureNoteFolder(id: String): File = File(context.filesDir, "notes/$id").apply { mkdirs() }

    private fun lessonToJson(l: Lesson): JSONObject = JSONObject().apply {
        put("id", l.id); put("date", l.date.toString()); put("subject", l.subject); put("kind", l.kind)
        put("start", l.start.toString()); put("end", l.end.toString()); put("room", l.room); put("teacher", l.teacher)
        put("week", l.week.name); put("source", l.source)
    }

    private fun lessonFromJson(o: JSONObject): Lesson = Lesson(
        id = o.getString("id"), date = LocalDate.parse(o.getString("date")), subject = o.getString("subject"), kind = o.optString("kind"),
        start = LocalTime.parse(o.getString("start")), end = LocalTime.parse(o.getString("end")), room = o.optString("room"), teacher = o.optString("teacher"),
        week = WeekKind.valueOf(o.optString("week", WeekKind.UNKNOWN.name)), source = o.optString("source", "SFU")
    )

    private fun save() {
        val root = JSONObject()
        root.put("currentWeek", currentWeek.name)
        root.put("lastScheduleSync", lastScheduleSync?.toString())
        root.put("backendUrl", backendUrl)
        root.put("updateChannel", updateChannel)
        root.put("selectedSubgroup", selectedSubgroup)
        pruneHobbyEvents()
        root.put("hobbyRequestEvents", JSONArray().apply {
            hobbyRequestEvents.forEach { (time, seconds) ->
                put(JSONObject().put("time", time.toString()).put("seconds", seconds))
            }
        })
        root.put("localOpenAiInputTokens", localOpenAiInputTokens)
        root.put("localOpenAiCachedInputTokens", localOpenAiCachedInputTokens)
        root.put("localOpenAiOutputTokens", localOpenAiOutputTokens)
        root.put("localOpenAiCostUsd", localOpenAiCostUsd)
        root.put("openAiBalanceCorrectionUsd", openAiBalanceCorrectionUsd)
        root.put("openAiOperationUsage", JSONObject().apply {
            openAiOperationUsage.forEach { (id, u) ->
                put(id, JSONObject()
                    .put("inputTokens", u.inputTokens)
                    .put("cachedInputTokens", u.cachedInputTokens)
                    .put("outputTokens", u.outputTokens)
                    .put("costUsd", u.costUsd))
            }
        })
        root.put("openAiTopUps", JSONArray().apply {
            openAiTopUps.forEach { item ->
                put(JSONObject().put("time", item.time.toString()).put("amountUsd", item.amountUsd))
            }
        })
        root.put("lastScheduleSyncBySubgroup", JSONObject().apply { lastScheduleSyncBySubgroup.forEach { (k, v) -> put(k.toString(), v.toString()) } })
        root.put("schedulesBySubgroup", JSONObject().apply {
            schedulesBySubgroup.forEach { (subgroup, schedule) ->
                put(subgroup.toString(), JSONArray().apply { schedule.forEach { l -> put(lessonToJson(l)) } })
            }
        })
        root.put("originalSchedulesBySubgroup", JSONObject().apply {
            originalSchedulesBySubgroup.forEach { (subgroup, schedule) ->
                put(subgroup.toString(), JSONArray().apply { schedule.forEach { l -> put(lessonToJson(l)) } })
            }
        })
        root.put("officialPreviousLessonsBySubgroup", JSONObject().apply {
            officialPreviousLessonsBySubgroup.forEach { (subgroup, changes) ->
                put(subgroup.toString(), JSONObject().apply {
                    changes.forEach { (key, lesson) -> put(key, lessonToJson(lesson)) }
                })
            }
        })
        root.put("officialAddedSlotsBySubgroup", JSONObject().apply {
            officialAddedSlotsBySubgroup.forEach { (subgroup, slots) ->
                put(subgroup.toString(), JSONArray(slots.toList()))
            }
        })
        root.put("officialPublishedSlotsBySubgroup", JSONObject().apply {
            officialPublishedSlotsBySubgroup.forEach { (subgroup, slots) ->
                put(subgroup.toString(), JSONArray(slots.toList()))
            }
        })
        root.put("lessonOverridesBySubgroup", JSONObject().apply {
            lessonOverridesBySubgroup.forEach { (subgroup, overrides) ->
                put(subgroup.toString(), JSONObject().apply {
                    overrides.forEach { (key, lesson) -> put(key, lessonToJson(lesson)) }
                })
            }
        })
        root.put("manualLessonsBySubgroup", JSONObject().apply {
            manualLessonsBySubgroup.forEach { (subgroup, manualLessons) ->
                put(subgroup.toString(), JSONObject().apply {
                    manualLessons.forEach { (key, lesson) -> put(key, lessonToJson(lesson)) }
                })
            }
        })
        root.put("noNotesLessonIds", JSONArray(noNotesLessonIds.toList()))
        root.put("noHomeworkLessonIds", JSONArray(noHomeworkLessonIds.toList()))
        root.put("lessonNotes", JSONObject().apply { lessonNotes.forEach { (k, v) -> put(k, v) } })
        root.put("lessonNoteDrafts", JSONObject().apply { lessonNoteDrafts.forEach { (k, v) -> put(k, v) } })
        root.put("homeworkDraftTexts", JSONObject().apply { homeworkDraftTexts.forEach { (k, v) -> put(k, v) } })
        root.put("homeworkDraftDueDates", JSONObject().apply { homeworkDraftDueDates.forEach { (k, v) -> put(k, v.toString()) } })

        val lessonsJson = JSONArray()
        lessons.forEach { l -> lessonsJson.put(lessonToJson(l)) }
        root.put("lessons", lessonsJson)

        val notesJson = JSONArray()
        notes.forEach { n -> notesJson.put(JSONObject().apply {
            put("id", n.id); put("lessonId", n.lessonId); put("subject", n.subject); put("date", n.date.toString())
            put("createdAt", n.createdAt.toString()); put("status", n.status.name); put("draftText", n.draftText); put("finalText", n.finalText)
            put("images", JSONArray(n.imagePaths))
            put("uncertain", JSONArray().apply { n.uncertainRanges.forEach { r -> put(JSONObject().put("start", r.start).put("end", r.end)) } })
            put("pageLinks", JSONArray().apply { n.pageLinks.forEach { p -> put(JSONObject().put("imagePath", p.imagePath).put("textStart", p.textStart).put("textEnd", p.textEnd)) } })
            put("sourceAnchors", JSONArray().apply { n.sourceAnchors.forEach { a -> put(JSONObject().put("imagePath", a.imagePath).put("textStart", a.textStart).put("textEnd", a.textEnd).put("yTop", a.yTop).put("yBottom", a.yBottom)) } })
        }) }
        root.put("notes", notesJson)

        val homeworkJson = JSONArray()
        homeworks.forEach { h -> homeworkJson.put(JSONObject().apply {
            put("id", h.id); put("lessonId", h.lessonId); put("subject", h.subject); put("assignedDate", h.assignedDate.toString())
            put("dueDate", h.dueDate.toString()); put("text", h.text); put("createdAt", h.createdAt.toString()); put("completed", h.completed)
        }) }
        root.put("homeworks", homeworkJson)

        stateFile.writeText(root.toString(2))
    }

    private fun load() {
        if (!stateFile.exists()) return
        runCatching {
            val root = JSONObject(stateFile.readText())
            currentWeek = WeekKind.valueOf(root.optString("currentWeek", WeekKind.UNKNOWN.name))
            root.optString("lastScheduleSync").takeIf { it.isNotBlank() && it != "null" }?.let { lastScheduleSync = LocalDateTime.parse(it) }
            backendUrl = root.optString("backendUrl", "https://study-notes-sfu-backend.vercel.app").ifBlank { "https://study-notes-sfu-backend.vercel.app" }
            updateChannel = root.optString("updateChannel", "stable").let { if (it.equals("test", ignoreCase = true)) "test" else "stable" }
            selectedSubgroup = root.optInt("selectedSubgroup", 1).takeIf { it == 1 || it == 2 } ?: 1
            hobbyRequestEvents = mutableListOf<Pair<LocalDateTime, Double>>().apply {
                val events = root.optJSONArray("hobbyRequestEvents")
                if (events != null) {
                    for (i in 0 until events.length()) {
                        val e = events.getJSONObject(i)
                        runCatching { LocalDateTime.parse(e.getString("time")) }.getOrNull()?.let {
                            add(it to e.optDouble("seconds", 0.0))
                        }
                    }
                }
                // v0.8.7 had only aggregate counters, which cannot be mapped correctly onto a
                // rolling 30-day window. Start precise tracking fresh from v0.8.8.
            }
            pruneHobbyEvents()
            localOpenAiInputTokens = root.optLong("localOpenAiInputTokens", 0L)
            localOpenAiCachedInputTokens = root.optLong("localOpenAiCachedInputTokens", 0L)
            localOpenAiOutputTokens = root.optLong("localOpenAiOutputTokens", 0L)
            localOpenAiCostUsd = root.optDouble("localOpenAiCostUsd", 0.0)
            openAiBalanceCorrectionUsd = root.optDouble("openAiBalanceCorrectionUsd", 0.0)
            openAiOperationUsage = mutableMapOf<String, OpenAiOperationUsage>().apply {
                val obj = root.optJSONObject("openAiOperationUsage") ?: JSONObject()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val u = obj.optJSONObject(id) ?: continue
                    put(id, OpenAiOperationUsage(
                        u.optLong("inputTokens", 0L),
                        u.optLong("cachedInputTokens", 0L),
                        u.optLong("outputTokens", 0L),
                        u.optDouble("costUsd", 0.0)
                    ))
                }
            }
            openAiTopUps = mutableListOf<OpenAiTopUp>().apply {
                val arr = root.optJSONArray("openAiTopUps") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val time = runCatching { LocalDateTime.parse(o.optString("time")) }.getOrNull() ?: continue
                    val amount = o.optDouble("amountUsd", 0.0)
                    if (amount >= 0.005) add(OpenAiTopUp(time, amount))
                }
            }
            val syncByGroupJson = root.optJSONObject("lastScheduleSyncBySubgroup") ?: JSONObject()
            lastScheduleSyncBySubgroup = mutableMapOf<Int, LocalDateTime>().apply {
                for (subgroup in 1..2) {
                    syncByGroupJson.optString(subgroup.toString()).takeIf { it.isNotBlank() }?.let { put(subgroup, LocalDateTime.parse(it)) }
                }
            }

            val noNotes = root.optJSONArray("noNotesLessonIds") ?: JSONArray()
            noNotesLessonIds = mutableSetOf<String>().apply {
                for (i in 0 until noNotes.length()) add(noNotes.getString(i))
            }

            val noHomework = root.optJSONArray("noHomeworkLessonIds") ?: JSONArray()
            noHomeworkLessonIds = mutableSetOf<String>().apply {
                for (i in 0 until noHomework.length()) add(noHomework.getString(i))
            }

            val lessonNotesJson = root.optJSONObject("lessonNotes") ?: JSONObject()
            lessonNotes = mutableMapOf<String, String>().apply {
                val keys = lessonNotesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, lessonNotesJson.optString(key))
                }
            }

            val lessonDraftsJson = root.optJSONObject("lessonNoteDrafts") ?: JSONObject()
            lessonNoteDrafts = mutableMapOf<String, String>().apply {
                val keys = lessonDraftsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, lessonDraftsJson.optString(key))
                }
            }

            val homeworkDraftsJson = root.optJSONObject("homeworkDraftTexts") ?: JSONObject()
            homeworkDraftTexts = mutableMapOf<String, String>().apply {
                val keys = homeworkDraftsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, homeworkDraftsJson.optString(key))
                }
            }

            val homeworkDatesJson = root.optJSONObject("homeworkDraftDueDates") ?: JSONObject()
            homeworkDraftDueDates = mutableMapOf<String, LocalDate>().apply {
                val keys = homeworkDatesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    homeworkDatesJson.optString(key).takeIf { it.isNotBlank() }?.let { put(key, LocalDate.parse(it)) }
                }
            }

            val legacyLessonsJson = root.optJSONArray("lessons") ?: JSONArray()
            val legacyLessons = MutableList(legacyLessonsJson.length()) { i -> lessonFromJson(legacyLessonsJson.getJSONObject(i)) }.toMutableList()

            val schedulesJson = root.optJSONObject("schedulesBySubgroup")
            schedulesBySubgroup = mutableMapOf()
            if (schedulesJson != null) {
                for (subgroup in 1..2) {
                    val array = schedulesJson.optJSONArray(subgroup.toString()) ?: continue
                    schedulesBySubgroup[subgroup] = MutableList(array.length()) { i -> lessonFromJson(array.getJSONObject(i)) }.toMutableList()
                }
            }
            val originalsJson = root.optJSONObject("originalSchedulesBySubgroup")
            originalSchedulesBySubgroup = mutableMapOf()
            if (originalsJson != null) {
                for (subgroup in 1..2) {
                    val array = originalsJson.optJSONArray(subgroup.toString()) ?: continue
                    originalSchedulesBySubgroup[subgroup] = MutableList(array.length()) { i -> lessonFromJson(array.getJSONObject(i)) }.toMutableList()
                }
            }
            val officialPreviousJson = root.optJSONObject("officialPreviousLessonsBySubgroup")
            officialPreviousLessonsBySubgroup = mutableMapOf()
            if (officialPreviousJson != null) {
                for (subgroup in 1..2) {
                    val obj = officialPreviousJson.optJSONObject(subgroup.toString()) ?: continue
                    val map = mutableMapOf<String, Lesson>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = lessonFromJson(obj.getJSONObject(key))
                    }
                    officialPreviousLessonsBySubgroup[subgroup] = map
                }
            }
            val officialAddedJson = root.optJSONObject("officialAddedSlotsBySubgroup")
            officialAddedSlotsBySubgroup = mutableMapOf()
            if (officialAddedJson != null) {
                for (subgroup in 1..2) {
                    val array = officialAddedJson.optJSONArray(subgroup.toString()) ?: continue
                    officialAddedSlotsBySubgroup[subgroup] = mutableSetOf<String>().apply {
                        for (i in 0 until array.length()) add(array.getString(i))
                    }
                }
            }

            val officialPublishedJson = root.optJSONObject("officialPublishedSlotsBySubgroup")
            officialPublishedSlotsBySubgroup = mutableMapOf()
            if (officialPublishedJson != null) {
                for (subgroup in 1..2) {
                    val array = officialPublishedJson.optJSONArray(subgroup.toString()) ?: continue
                    officialPublishedSlotsBySubgroup[subgroup] = mutableSetOf<String>().apply {
                        for (i in 0 until array.length()) add(array.getString(i))
                    }
                }
            }

            val overridesJson = root.optJSONObject("lessonOverridesBySubgroup")
            lessonOverridesBySubgroup = mutableMapOf()
            if (overridesJson != null) {
                for (subgroup in 1..2) {
                    val obj = overridesJson.optJSONObject(subgroup.toString()) ?: continue
                    val map = mutableMapOf<String, Lesson>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = lessonFromJson(obj.getJSONObject(key))
                    }
                    lessonOverridesBySubgroup[subgroup] = map
                }
            }
            val manualJson = root.optJSONObject("manualLessonsBySubgroup")
            manualLessonsBySubgroup = mutableMapOf()
            if (manualJson != null) {
                for (subgroup in 1..2) {
                    val obj = manualJson.optJSONObject(subgroup.toString()) ?: continue
                    val map = mutableMapOf<String, Lesson>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = lessonFromJson(obj.getJSONObject(key))
                    }
                    manualLessonsBySubgroup[subgroup] = map
                }
            }
            // Миграция со старых версий: всё ранее сохранённое расписание относилось к 1-й подгруппе.
            if (schedulesBySubgroup.isEmpty() && legacyLessons.isNotEmpty()) schedulesBySubgroup[1] = legacyLessons
            if (lastScheduleSyncBySubgroup.isEmpty() && lastScheduleSync != null) lastScheduleSyncBySubgroup[1] = lastScheduleSync!!
            // v0.7.0 migration: before local schedule overrides existed, the effective schedule was also the original one.
            for (subgroup in 1..2) {
                if (originalSchedulesBySubgroup[subgroup].isNullOrEmpty() && !schedulesBySubgroup[subgroup].isNullOrEmpty()) {
                    originalSchedulesBySubgroup[subgroup] = schedulesBySubgroup[subgroup]!!.toMutableList()
                }
            }
            // v0.7.6 migration: v0.7.3-v0.7.5 могли сохранить в state.json
            // локально спрогнозированные недели далеко вперёд. Удаляем только
            // такие будущие SFU-derived записи из кэша. Ручные занятия пользователя
            // не трогаем, прошлые занятия тоже сохраняем.
            val today = LocalDate.now()
            val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val nextWeekSunday = currentMonday.plusWeeks(1).plusDays(6)
            for (subgroup in 1..2) {
                originalSchedulesBySubgroup[subgroup]?.removeAll { it.date.isAfter(nextWeekSunday) }
                val schedule = schedulesBySubgroup.getOrPut(subgroup) { mutableListOf() }
                schedule.removeAll { it.date.isAfter(nextWeekSunday) && it.source != "LOCAL_INSERT" }
                manualLessonsBySubgroup[subgroup].orEmpty().forEach { (key, manual) ->
                    if (schedule.none { slotKey(it) == key }) schedule.add(manual)
                }
                schedule.sortWith(compareBy<Lesson> { it.date }.thenBy { it.start })
            }
            lessons = schedulesBySubgroup[selectedSubgroup]?.toMutableList() ?: mutableListOf()
            lastScheduleSync = lastScheduleSyncBySubgroup[selectedSubgroup]

            val nj = root.optJSONArray("notes") ?: JSONArray()
            notes = MutableList(nj.length()) { i ->
                val o = nj.getJSONObject(i)
                val images = o.optJSONArray("images") ?: JSONArray()
                val uncertain = o.optJSONArray("uncertain") ?: JSONArray()
                val pageLinks = o.optJSONArray("pageLinks") ?: JSONArray()
                val sourceAnchors = o.optJSONArray("sourceAnchors") ?: JSONArray()
                Note(
                    id = o.getString("id"), lessonId = if (o.isNull("lessonId")) null else o.optString("lessonId").takeIf { it.isNotBlank() && it != "null" },
                    subject = o.getString("subject"), date = LocalDate.parse(o.getString("date")), createdAt = LocalDateTime.parse(o.getString("createdAt")),
                    imagePaths = List(images.length()) { images.getString(it) }, draftText = o.optString("draftText"), finalText = o.optString("finalText"),
                    uncertainRanges = List(uncertain.length()) { j -> uncertain.getJSONObject(j).let { UncertainRange(it.getInt("start"), it.getInt("end")) } },
                    pageLinks = List(pageLinks.length()) { j -> pageLinks.getJSONObject(j).let { PageLink(it.getString("imagePath"), it.getInt("textStart"), it.getInt("textEnd")) } },
                    sourceAnchors = List(sourceAnchors.length()) { j -> sourceAnchors.getJSONObject(j).let { SourceAnchor(it.getString("imagePath"), it.getInt("textStart"), it.getInt("textEnd"), it.optInt("yTop", 0), it.optInt("yBottom", 1000)) } },
                    status = NoteStatus.valueOf(o.optString("status", NoteStatus.LOCAL.name))
                )
            }.toMutableList()

            val hj = root.optJSONArray("homeworks") ?: JSONArray()
            homeworks = MutableList(hj.length()) { i ->
                val o = hj.getJSONObject(i)
                Homework(
                    id = o.getString("id"), lessonId = o.getString("lessonId"), subject = o.getString("subject"),
                    assignedDate = LocalDate.parse(o.getString("assignedDate")), dueDate = LocalDate.parse(o.getString("dueDate")),
                    text = o.optString("text"), createdAt = LocalDateTime.parse(o.optString("createdAt", LocalDateTime.now().toString())),
                    completed = o.optBoolean("completed", false)
                )
            }.toMutableList()
        }
    }
}
