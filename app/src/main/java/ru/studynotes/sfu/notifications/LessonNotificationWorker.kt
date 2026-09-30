package ru.studynotes.sfu.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import ru.studynotes.sfu.MainActivity
import ru.studynotes.sfu.model.Lesson
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class LessonNotificationWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val subject = inputData.getString("subject") ?: return Result.failure()
        val lessonId = inputData.getString("lessonId") ?: ""
        createChannel(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("lessonId", lessonId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(applicationContext, lessonId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Пара закончилась")
            .setContentText("$subject — добавить фотографии конспекта?")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(lessonId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "lesson_finished"

        fun schedule(context: Context, lessons: List<Lesson>) {
            val wm = WorkManager.getInstance(context)
            lessons.forEach { lesson ->
                val end = LocalDateTime.of(lesson.date, lesson.end).plusMinutes(2)
                val delay = Duration.between(LocalDateTime.now(), end)
                if (!delay.isNegative && delay.toDays() <= 8) {
                    val request = OneTimeWorkRequestBuilder<LessonNotificationWorker>()
                        .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf("subject" to lesson.subject, "lessonId" to lesson.id))
                        .build()
                    wm.enqueueUniqueWork("lesson_${lesson.id}", ExistingWorkPolicy.REPLACE, request)
                }
            }
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "После окончания пары", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }
}
