package ru.studynotes.sfu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import ru.studynotes.sfu.data.AppRepository
import ru.studynotes.sfu.ui.StudyNotesApp

class MainActivity : ComponentActivity() {
    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        repository = AppRepository(applicationContext)

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val requestedLessonId = intent?.getStringExtra("lessonId")
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(950)
                showSplash = false
            }
            if (showSplash) StudyNotesSplash() else StudyNotesApp(repository = repository, initiallyOpenLessonId = requestedLessonId)
        }
    }
}

@Composable
private fun StudyNotesSplash() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(if (started) 1f else 0.86f, tween(520), label = "logoScale")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFBFAFF), Color(0xFFF6F2FF), Color(0xFFFDFDFF))
                )
            )
    ) {
        Box(
            Modifier
                .size(270.dp)
                .offset(x = (-105).dp, y = (-80).dp)
                .clip(RoundedCornerShape(90.dp))
                .background(Color(0x16764DDC))
        )
        Box(
            Modifier
                .size(310.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 120.dp, y = 115.dp)
                .clip(RoundedCornerShape(110.dp))
                .background(Color(0x12764DDC))
        )

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "StudyNotesSFU",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF211D2A)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Твои конспекты. Всегда под рукой.",
                fontSize = 15.sp,
                color = Color(0xFF777184),
                letterSpacing = 1.1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(210.dp).height(6.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFF7146D9),
                trackColor = Color(0xFFE7E1F3)
            )
            Spacer(Modifier.height(14.dp))
            Text("Загрузка…", fontSize = 14.sp, color = Color(0xFF6D6678), letterSpacing = 1.4.sp)
        }

        Text(
            text = "Учись  •  Удобно  •  Эффективно",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp),
            color = Color(0xFF9C8CC8),
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
    }
}
