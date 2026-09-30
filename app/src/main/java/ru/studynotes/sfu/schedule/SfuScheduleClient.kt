package ru.studynotes.sfu.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.studynotes.sfu.model.Lesson
import ru.studynotes.sfu.model.WeekKind
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.UUID

/**
 * Клиент расписания СФУ v0.2.
 *
 * Новый сайт newtimetable.sfu-kras.ru является React-оболочкой. Само расписание
 * он получает из JSON API edu.sfu-kras.ru, поэтому HTML больше не парсится.
 *
 * Алгоритм недели повторяет сайт СФУ. В API:
 * ISO-номер недели чётный -> API week=1 -> на сайте подписано «нечётная»;
 * ISO-номер недели нечётный -> API week=2 -> на сайте подписано «чётная».
 */
class SfuScheduleClient {
    data class Result(
        val lessons: List<Lesson>,
        val currentWeek: WeekKind,
        val diagnostics: String
    )

    private val defaultGroup = "ИТ00-00БЭК (1 подгруппа)"
    private val httpsApiBase = "https://edu.sfu-kras.ru/api/timetable/get&target="
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun fetch(context: Context, group: String = defaultGroup, today: LocalDate = LocalDate.now()): Result {
        val encoded = URLEncoder.encode(group, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        val httpsUrl = httpsApiBase + encoded
        val raw = fetchJson(context, httpsUrl)
        val transport = "https-api"
        val entries = extractObjects(raw)

        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Не прогнозируем расписание на будущие недели. В приложение попадают
        // только три недели, которые непосредственно формируются из актуального
        // ответа API СФУ: предыдущая, текущая и следующая. Никакого локального
        // разворачивания шаблона на месяцы вперёд нет.
        val weekStarts = listOf(
            currentMonday.minusWeeks(1),
            currentMonday,
            currentMonday.plusWeeks(1)
        )

        val lessons = weekStarts.flatMap { monday ->
            val week = currentWeekKind(monday)
            val apiWeek = apiWeekForDate(monday)
            entries.mapNotNull { entry -> parseEntry(entry, apiWeek, week, monday, group) }
        }.distinctBy { it.id }
            .sortedWith(compareBy<Lesson> { it.date }.thenBy { it.start }.thenBy { it.subject })

        val currentWeek = currentWeekKind(today)
        return Result(
            lessons = lessons,
            currentWeek = currentWeek,
            diagnostics = "transport=$transport; entries=${entries.size}; lessons=${lessons.size}"
        )
    }

    internal fun apiWeekForDate(date: LocalDate): Int {
        val isoWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        return if (isoWeek % 2 == 0) 1 else 2
    }

    internal fun currentWeekKind(date: LocalDate): WeekKind =
        if (apiWeekForDate(date) == 1) WeekKind.ODD else WeekKind.EVEN

    /**
     * edu.sfu-kras.ru может присылать неполную TLS-цепочку без промежуточного
     * сертификата GlobalSign. Для этого хоста добавляем только известный
     * промежуточный сертификат и повторно передаём восстановленную цепочку
     * системному Android TrustManager.
     *
     * Корнем доверия по-прежнему остаётся системное хранилище Android.
     * Стандартная проверка имени хоста HttpsURLConnection не изменяется.
     * HTTP, trust-all и отключение проверки имени хоста не используются.
     */
    private fun fetchJson(context: Context, urlString: String): String {
        val url = URL(urlString)
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS required" }
        require(url.host.equals("edu.sfu-kras.ru", ignoreCase = true)) { "Unexpected SFU API host" }

        val connection = (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = SfuTlsChainRepair.socketFactory(context.applicationContext, url.host)
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "StudyNotesSFU/0.3 Android")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
            instanceFollowRedirects = true
            useCaches = false
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                error("API СФУ ответил HTTP $code${if (errorText.isNotBlank()) ": ${errorText.take(180)}" else ""}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * API сейчас возвращает массив объектов, но делаем разбор устойчивым к
     * обёртке вида { data:[...] } / { timetable:[...] }, чтобы приложение не
     * ломалось от небольшого изменения формы ответа.
     */
    private fun extractObjects(raw: String): List<JSONObject> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when (trimmed.first()) {
            '[' -> jsonArrayToObjects(JSONArray(trimmed))
            '{' -> {
                val root = JSONObject(trimmed)
                val direct = listOf("data", "timetable", "lessons", "result", "items")
                    .firstNotNullOfOrNull { key -> root.optJSONArray(key) }
                if (direct != null) jsonArrayToObjects(direct)
                else if (looksLikeLesson(root)) listOf(root)
                else collectLessonObjects(root)
            }
            else -> error("API СФУ вернул неожиданный формат данных")
        }
    }

    private fun jsonArrayToObjects(array: JSONArray): List<JSONObject> = buildList {
        for (i in 0 until array.length()) {
            when (val item = array.opt(i)) {
                is JSONObject -> if (looksLikeLesson(item)) add(item) else addAll(collectLessonObjects(item))
                is JSONArray -> addAll(jsonArrayToObjects(item))
            }
        }
    }

    private fun collectLessonObjects(obj: JSONObject): List<JSONObject> = buildList {
        val keys = obj.keys()
        while (keys.hasNext()) {
            when (val value = obj.opt(keys.next())) {
                is JSONObject -> if (looksLikeLesson(value)) add(value) else addAll(collectLessonObjects(value))
                is JSONArray -> addAll(jsonArrayToObjects(value))
            }
        }
    }

    private fun looksLikeLesson(o: JSONObject): Boolean =
        o.has("day") && o.has("week") && o.has("time") && o.has("subject")

    private fun parseEntry(
        o: JSONObject,
        currentApiWeek: Int,
        currentWeek: WeekKind,
        monday: LocalDate,
        group: String
    ): Lesson? {
        val entryWeek = o.optString("week").trim().toIntOrNull()
        if (entryWeek != null && entryWeek in 1..2 && entryWeek != currentApiWeek) return null

        // JavaScript Date.getDay(): 0=воскресенье, 1=понедельник ... 6=суббота.
        val apiDay = o.optString("day").trim().toIntOrNull() ?: return null
        if (apiDay !in 0..6) return null
        val isoOffset = if (apiDay == 0) 6 else apiDay - 1
        val date = monday.plusDays(isoOffset.toLong())

        val (start, end) = parseTimeRange(o.optString("time")) ?: return null
        val subject = decodeText(o.optString("subject")).ifBlank { return null }
        val kind = decodeText(o.optString("type")).ifBlank { "занятие" }
        val teacher = decodeText(o.optString("teacher"))
        val place = decodeText(o.optString("place"))
        val building = decodeText(o.optString("building"))
        val room = decodeText(o.optString("room"))
        val sync = decodeText(o.optString("sync"))

        // API нередко дублирует корпус/аудиторию одновременно в place и
        // building+room. Если place уже заполнено, считаем его готовой строкой
        // местоположения и не повторяем те же данные второй раз.
        val primaryLocation = if (place.isNotBlank()) {
            place
        } else {
            listOf(building, room).filter { it.isNotBlank() }.joinToString(", ")
        }
        val location = buildList {
            if (primaryLocation.isNotBlank()) add(primaryLocation)
            if (sync.isNotBlank() && none { it.equals(sync, ignoreCase = true) }) add(sync)
        }.joinToString(" · ")

        val stableParts = mutableListOf(
            date.toString(), subject, kind, start.toString(), end.toString(), teacher,
            place, building, room, currentApiWeek.toString()
        )
        // Не меняем ID 1-й подгруппы, чтобы старые конспекты сохранили связь с занятиями.
        // Для 2-й подгруппы добавляем группу в ключ, чтобы одинаковые пары двух подгрупп не сливались.
        if (group.contains("(2 подгруппа)")) stableParts.add(group)
        val stableKey = stableParts.joinToString("|")
        val stableId = UUID.nameUUIDFromBytes(stableKey.toByteArray(Charsets.UTF_8)).toString()

        return Lesson(
            id = stableId,
            date = date,
            subject = subject,
            kind = kind,
            start = start,
            end = end,
            room = location,
            teacher = teacher,
            week = currentWeek,
            source = "SFU_API"
        )
    }

    private fun parseTimeRange(raw: String): Pair<LocalTime, LocalTime>? {
        val normalized = raw.replace('–', '-').replace('—', '-').replace(" ", "")
        val parts = normalized.split('-')
        if (parts.size != 2) return null
        return runCatching {
            LocalTime.parse(parts[0], timeFormatter) to LocalTime.parse(parts[1], timeFormatter)
        }.getOrNull()
    }

    private fun decodeText(value: String): String =
        value.replace("\\u00a0", " ").replace(Regex("\\s+"), " ").trim()
}
