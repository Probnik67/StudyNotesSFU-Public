package ru.studynotes.sfu.ai

import ru.studynotes.sfu.security.BackendRequestSigner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import ru.studynotes.sfu.model.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class AiBackendClient(private val baseUrl: String) {
    data class UsageInfo(
        val inputTokens: Int = 0,
        val cachedInputTokens: Int = 0,
        val outputTokens: Int = 0,
        val costUsd: Double = 0.0
    )

    data class DraftResult(
        val text: String,
        val uncertainRanges: List<UncertainRange>,
        val pageLinks: List<PageLink>,
        val sourceAnchors: List<SourceAnchor>,
        val operationId: String,
        val usage: UsageInfo
    )

    class BillableAnalyzeException(
        message: String,
        val operationId: String,
        val usage: UsageInfo
    ) : Exception(message)

    data class FinalizeResult(val status: String, val usage: UsageInfo)

    data class UsageSummary(
        val todayUsd: Double,
        val monthUsd: Double,
        val allTimeUsd: Double,
        val monthRequests: Int,
        val analyzeUsd: Double,
        val finalizeUsd: Double,
        val inputTokens: Int,
        val outputTokens: Int,
        val month: String,
        val estimatedRemainingUsd: Double,
        val remainingInputTokenEquivalent: Long,
        val remainingOutputTokenEquivalent: Long
    )

    private data class PreparedImage(val pageIndex: Int, val bytes: ByteArray)
    private data class NormalizedText(val text: String, val offsetMap: IntArray)

    fun health(): String {
        val conn = open("GET", "/health")
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    fun analyze(note: Note, lesson: Lesson?, onProgress: (String) -> Unit = {}): DraftResult {
        val prepared = note.imagePaths.mapIndexed { index, path ->
            onProgress("Подготовка фотографии ${index + 1}/${note.imagePaths.size}…")
            PreparedImage(index, optimizeForUpload(File(path)))
        }

        val operationId = UUID.randomUUID().toString()
        onProgress("Отправка на сервер…")
        val root = try {
            postMultipartAnalyze(note, lesson, prepared, operationId) {
                onProgress("OpenAI анализирует конспект…")
            }
        } catch (e: BillableAnalyzeException) {
            throw e
        } catch (e: Exception) {
            // If the HTTP connection itself broke after OpenAI had already answered, try to
            // recover the server-side financial checkpoint before reporting the error.
            val recovered = runCatching { operationUsage(operationId) }.getOrNull() ?: UsageInfo()
            throw BillableAnalyzeException(e.message ?: "Ошибка соединения", operationId, recovered)
        }
        onProgress("Получение черновика…")

        val normalized = normalizeHyphenatedLineBreaks(root.getString("draftText"))
        val text = normalized.text
        val uncertainJson = root.optJSONArray("uncertain") ?: JSONArray()
        val pageLinksJson = root.optJSONArray("pageLinks") ?: JSONArray()
        val anchorsJson = root.optJSONArray("sourceAnchors") ?: JSONArray()
        val uncertain = emptyList<UncertainRange>()
        val pageLinks = List(pageLinksJson.length()) { i ->
            val o = pageLinksJson.getJSONObject(i)
            val pageIndex = o.getInt("pageIndex")
            PageLink(
                note.imagePaths.getOrElse(pageIndex) { note.imagePaths.first() },
                remapOffset(normalized.offsetMap, o.getInt("textStart")),
                remapOffset(normalized.offsetMap, o.getInt("textEnd"))
            )
        }
        val sourceAnchors = List(anchorsJson.length()) { i ->
            val o = anchorsJson.getJSONObject(i)
            val pageIndex = o.getInt("pageIndex")
            SourceAnchor(
                note.imagePaths.getOrElse(pageIndex) { note.imagePaths.first() },
                remapOffset(normalized.offsetMap, o.getInt("textStart")),
                remapOffset(normalized.offsetMap, o.getInt("textEnd")),
                o.optInt("yTop", 0),
                o.optInt("yBottom", 1000)
            )
        }
        return DraftResult(
            text,
            uncertain,
            pageLinks,
            sourceAnchors,
            root.optString("operationId", operationId).ifBlank { operationId },
            parseUsage(root.optJSONObject("usage"))
        )
    }

    fun finalize(note: Note, lesson: Lesson?, finalText: String): FinalizeResult {
        val body = JSONObject()
            .put("noteId", note.id)
            .put("subject", note.subject)
            .put("date", note.date.toString())
            .put("lessonType", lesson?.kind ?: "")
            .put("teacher", lesson?.teacher ?: "")
            .put("finalText", finalText)
        val root = postJson("/notes/finalize", body)
        return FinalizeResult(root.optString("status", "ok"), parseUsage(root.optJSONObject("usage")))
    }

    fun usage(): UsageSummary {
        val conn = open("GET", "/usage")
        val text = try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Backend HTTP $code: $body")
            body
        } finally { conn.disconnect() }
        val o = JSONObject(text)
        return UsageSummary(
            todayUsd = o.optDouble("todayUsd", 0.0),
            monthUsd = o.optDouble("monthUsd", 0.0),
            allTimeUsd = o.optDouble("allTimeUsd", 0.0),
            monthRequests = o.optInt("monthRequests", 0),
            analyzeUsd = o.optDouble("analyzeUsd", 0.0),
            finalizeUsd = o.optDouble("finalizeUsd", 0.0),
            inputTokens = o.optInt("inputTokens", 0),
            outputTokens = o.optInt("outputTokens", 0),
            month = o.optString("month", ""),
            estimatedRemainingUsd = o.optDouble("estimatedRemainingUsd", 0.0),
            remainingInputTokenEquivalent = o.optLong("remainingInputTokenEquivalent", 0L),
            remainingOutputTokenEquivalent = o.optLong("remainingOutputTokenEquivalent", 0L)
        )
    }

    private fun postMultipartAnalyze(
        note: Note,
        lesson: Lesson?,
        images: List<PreparedImage>,
        operationId: String,
        onUploadComplete: () -> Unit
    ): JSONObject {
        val fields = listOf(
            "noteId" to note.id,
            "subject" to note.subject,
            "date" to note.date.toString(),
            "lessonType" to (lesson?.kind ?: ""),
            "teacher" to (lesson?.teacher ?: ""),
            "operationId" to operationId
        )

        val contentSha256 = BackendRequestSigner.analyzeContentSha256(
            fields = fields,
            images = images.map { it.pageIndex to it.bytes }
        )

        val boundary = "StudyNotesSFU-${UUID.randomUUID()}"
        val conn = open("POST", "/notes/analyze", contentSha256).apply {
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
        }

        try {
            DataOutputStream(conn.outputStream.buffered()).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
                    out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("noteId", note.id)
                field("subject", note.subject)
                field("date", note.date.toString())
                field("lessonType", lesson?.kind ?: "")
                field("teacher", lesson?.teacher ?: "")
                field("operationId", operationId)

                images.sortedBy { it.pageIndex }.forEach { img ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"images\"; filename=\"page_${img.pageIndex + 1}.jpg\"\r\n")
                    out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    out.write(img.bytes)
                    out.writeBytes("\r\n")
                }
                out.writeBytes("--$boundary--\r\n")
                out.flush()
            }
            onUploadComplete()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val root = runCatching { JSONObject(text) }.getOrNull()
                val detail = root?.optJSONObject("detail")
                val message = detail?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Backend HTTP $code: $text"
                val billedOperationId = detail?.optString("operationId")?.takeIf { it.isNotBlank() } ?: operationId
                val usage = parseUsage(detail?.optJSONObject("usage"))
                throw BillableAnalyzeException(message, billedOperationId, usage)
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun operationUsage(operationId: String): UsageInfo {
        val conn = open(
            "GET",
            "/operations/$operationId",
            BackendRequestSigner.sha256Hex(
                operationId.toByteArray(Charsets.UTF_8)
            )
        )
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return UsageInfo()
            val root = JSONObject(text)
            parseUsage(root.optJSONObject("usage"))
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)
        val contentSha256 = BackendRequestSigner.sha256Hex(bodyBytes)
        val conn = open("POST", path, contentSha256)
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        return try {
            conn.outputStream.use { it.write(bodyBytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Backend HTTP $code: $text")
            JSONObject(text)
        } finally { conn.disconnect() }
    }

    private fun open(method: String, path: String, contentSha256: String = BackendRequestSigner.emptyContentSha256()): HttpURLConnection {
        require(baseUrl.isNotBlank()) { "Адрес backend не настроен" }
        return (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 240_000
            useCaches = false

            if (path != "/health") {
                val auth = BackendRequestSigner.create(method, path, contentSha256)
                setRequestProperty("X-StudyNotes-Key-Id", auth.keyId)
                setRequestProperty("X-StudyNotes-Timestamp", auth.timestamp)
                setRequestProperty("X-StudyNotes-Nonce", auth.nonce)
                setRequestProperty("X-StudyNotes-Content-SHA256", auth.contentSha256)
                setRequestProperty("X-StudyNotes-Signature", auth.signature)
            }
        }
    }

    private fun parseUsage(o: JSONObject?): UsageInfo = if (o == null) UsageInfo() else UsageInfo(
        inputTokens = o.optInt("inputTokens", 0),
        cachedInputTokens = o.optInt("cachedInputTokens", 0),
        outputTokens = o.optInt("outputTokens", 0),
        costUsd = o.optDouble("costUsd", 0.0)
    )

    private fun remapOffset(map: IntArray, value: Int): Int {
        if (map.isEmpty()) return value.coerceAtLeast(0)
        return map[value.coerceIn(0, map.lastIndex)]
    }

    /**
     * Safety net for OCR models that still copy a purely physical end-of-line split such as
     * "кон-\nцентрации". Real hyphenated words that the model already returned on one logical
     * line are untouched.
     */
    private fun normalizeHyphenatedLineBreaks(source: String): NormalizedText {
        val remove = BooleanArray(source.length)
        var i = 1
        while (i < source.length - 1) {
            val c = source[i]
            if ((c == '-' || c == '‐' || c == '‑') && source[i - 1].isLetterOrDigit()) {
                var j = i + 1
                while (j < source.length && (source[j] == ' ' || source[j] == '\t')) j++
                if (j < source.length && source[j] == '\r') j++
                if (j < source.length && source[j] == '\n') {
                    var k = j + 1
                    while (k < source.length && (source[k] == ' ' || source[k] == '\t')) k++
                    if (k < source.length && source[k].isLetterOrDigit()) {
                        for (x in i until k) remove[x] = true
                        i = k
                        continue
                    }
                }
            }
            i++
        }
        if (remove.none { it }) return NormalizedText(source, IntArray(source.length + 1) { it })

        val out = StringBuilder(source.length)
        val map = IntArray(source.length + 1)
        var dst = 0
        for (src in source.indices) {
            map[src] = dst
            if (!remove[src]) {
                out.append(source[src])
                dst++
            }
        }
        map[source.length] = dst
        return NormalizedText(out.toString(), map)
    }

    private fun optimizeForUpload(file: File): ByteArray {
        require(file.exists()) { "Файл страницы не найден: ${file.name}" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать ${file.name}" }

        val maxSide = 2400
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Не удалось подготовить ${file.name}")

        val oriented = applyExifOrientation(decoded, file)
        val largest = max(oriented.width, oriented.height)
        val scaled = if (largest > maxSide) {
            val factor = maxSide.toFloat() / largest.toFloat()
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * factor).roundToInt().coerceAtLeast(1),
                (oriented.height * factor).roundToInt().coerceAtLeast(1),
                true
            )
        } else oriented

        val first = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, first)
        var bytes = first.toByteArray()
        if (bytes.size > 1_500_000) {
            val second = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 76, second)
            bytes = second.toByteArray()
        }

        if (scaled !== oriented) scaled.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        return bytes
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
