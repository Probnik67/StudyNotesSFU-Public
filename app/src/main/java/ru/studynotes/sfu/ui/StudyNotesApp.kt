@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.studynotes.sfu.ui
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border

import androidx.compose.foundation.gestures.detectTapGestures

import android.graphics.BitmapFactory
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.studynotes.sfu.data.AppRepository
import ru.studynotes.sfu.ai.AiBackendClient
import ru.studynotes.sfu.model.*
import ru.studynotes.sfu.notifications.LessonNotificationWorker
import ru.studynotes.sfu.schedule.SfuScheduleClient
import ru.studynotes.sfu.updates.AppUpdateInfo
import ru.studynotes.sfu.updates.AppUpdateManager
import java.io.File
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private sealed interface Screen {
    data object Home : Screen
    data object Schedule : Screen
    data object Homework : Screen
    data object Settings : Screen
    data object Archive : Screen
    data object AddNoteSubjects : Screen
    data class AddNoteDates(val subject: String) : Screen
    data object Statistics : Screen
    data class SubjectArchive(val subject: String, val subgroup: Int) : Screen
    data class Capture(val noteId: String) : Screen
    data class Editor(val noteId: String) : Screen
}

private enum class BottomTab { SCHEDULE, HOMEWORK, NOTES }

// OVERNIGHT_VISUAL_DESIGN_V1
private val VisualCanvas = Color(0xFF071522)
private val VisualCard = Color(0xFF0D2031)
private val VisualRaised = Color(0xFF12283D)
private val VisualPurple = Color(0xFF7540F8)
private val VisualMuted = Color(0xFF9AAAC3)
private val VisualRadius = 10.dp
private val VisualHorizontalPadding = 24.dp
private val VisualRowHeight = 74.dp
private val VisualSectionGap = 12.dp
private val VisualCardPadding = 12.dp
private val VisualTitleSp = 24.sp
private val VisualBodySp = 15.sp
private val VisualQuickActionHeight = 72.dp

@Composable
private fun AppBottomNavigation(
    selected: BottomTab?,
    onSchedule: () -> Unit,
    onHomework: () -> Unit,
    onNotes: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit
) {
    var showMore by remember { mutableStateOf(false) }
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    NavigationBar(
        containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White),
        tonalElevation = 6.dp
    ) {
        NavigationBarItem(
            selected = selected == BottomTab.SCHEDULE,
            onClick = onSchedule,
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Расписание", modifier = Modifier.size(30.dp)) },
            label = { Text("Расписание", maxLines = 1, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedColor,
                selectedTextColor = selectedColor,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor
            )
        )
        NavigationBarItem(
            selected = selected == BottomTab.HOMEWORK,
            onClick = onHomework,
            icon = { Icon(Icons.Default.Assignment, contentDescription = "Домашние задания", modifier = Modifier.size(30.dp)) },
            label = { Text("ДЗ", maxLines = 1, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedColor,
                selectedTextColor = selectedColor,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor
            )
        )
        NavigationBarItem(
            selected = selected == BottomTab.NOTES,
            onClick = onNotes,
            icon = { Icon(Icons.Default.NoteAlt, contentDescription = "Заметки", modifier = Modifier.size(30.dp)) },
            label = { Text("Заметки", maxLines = 1, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedColor,
                selectedTextColor = selectedColor,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor
            )
        )
        NavigationBarItem(
            selected = showMore || selected == null,
            onClick = { showMore = true },
            icon = {
                Box {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Ещё", modifier = Modifier.size(30.dp))
                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                            .background(if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.background else Color.White),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                        containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Архив конспектов") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                            onClick = { showMore = false; onArchive() }
                        )
                        DropdownMenuItem(
                            text = { Text("Настройки", modifier = Modifier.semantics { contentDescription = "VISUAL_SETTINGS" }) },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { showMore = false; onSettings() }
                        )
                    }
                }
            },
            label = { Text("Ещё", maxLines = 1, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedColor,
                selectedTextColor = selectedColor,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor
            )
        )
    }
}

private val appDarkThemeStateV0849 = androidx.compose.runtime.mutableStateOf(false) // DARK_THEME_GLOBAL_V0849

@Composable
fun StudyNotesApp(repository: AppRepository, initiallyOpenLessonId: String?) {
    val themeContext = androidx.compose.ui.platform.LocalContext.current
    val themePrefs = remember(themeContext) { themeContext.getSharedPreferences("studynotes_theme", android.content.Context.MODE_PRIVATE) }
    val themeLoadedV0849 = remember(themePrefs) { appDarkThemeStateV0849.value = themePrefs.getBoolean("dark", false); true }
    val appDarkTheme = appDarkThemeStateV0849.value // DARK_THEME_STATE_V0849
    androidx.compose.runtime.LaunchedEffect(appDarkTheme) {
        val activity = themeContext as? android.app.Activity
        activity?.window?.navigationBarColor = if (appDarkTheme) android.graphics.Color.rgb(6,17,31) else android.graphics.Color.WHITE
        activity?.window?.statusBarColor = if (appDarkTheme) android.graphics.Color.rgb(6,17,31) else android.graphics.Color.WHITE
        androidx.core.view.WindowCompat.getInsetsController(activity?.window ?: return@LaunchedEffect, activity.window.decorView).isAppearanceLightNavigationBars = !appDarkTheme
    } // SYSTEM_BARS_V0853

    val updateScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(-1) }
    var dismissedUpdateVersion by remember { mutableIntStateOf(-1) }

    fun checkForUpdates(showNoUpdateMessage: Boolean = false) {
        if (updateBusy) return
        updateBusy = true
        updateScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { AppUpdateManager.check(repository.updateChannel) }
            }
            val found = result.getOrNull()
            availableUpdate = if (found?.versionCode == dismissedUpdateVersion) null else found
            updateMessage = when {
                result.isFailure -> "Не удалось проверить обновления: ${result.exceptionOrNull()?.message ?: "ошибка сети"}"
                availableUpdate == null && showNoUpdateMessage -> "Установлена актуальная версия."
                else -> null
            }
            updateBusy = false
        }
    }

    // AUTO_UPDATE_ON_RESUME_V0838
    val updateResumeContext = androidx.compose.ui.platform.LocalContext.current
    val updateLifecycleOwner = updateResumeContext as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(updateLifecycleOwner, repository.updateChannel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checkForUpdates(false)
            }
        }
        updateLifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { updateLifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    // LIVE_UPDATE_MONITOR_V0829
    LaunchedEffect("live-update-monitor-v0829", repository.updateChannel) {
        while (true) {
            checkForUpdates(false)
            delay(20_000L) // AUTO_UPDATE_INTERVAL_V0838
        }
    }


    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var editorReturnScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val homeListState = rememberLazyListState()
    val homeworkListState = rememberLazyListState()

    // SCHEDULE_RECOVERY_V0826
    LaunchedEffect("schedule-recovery-v0826", repository.selectedSubgroup) {
        repeat(3) { attempt ->
            val result = runCatching {
                withContext(Dispatchers.IO) { SfuScheduleClient().fetch(context, repository.selectedGroupName) }
            }
            val loaded = result.getOrNull()
            if (loaded != null && loaded.lessons.isNotEmpty()) {
                repository.replaceSchedule(loaded.lessons, loaded.currentWeek, repository.selectedSubgroup)
                LessonNotificationWorker.schedule(context, loaded.lessons)
                refresh++
                return@LaunchedEffect
            }
            if (attempt < 2) delay(1200L * (attempt + 1))
        }
    }

    // LIVE_SCHEDULE_REFRESH_V0829
    LaunchedEffect("live-schedule-refresh-v0829", repository.selectedSubgroup) {
        while (true) {
            val result = runCatching {
                withContext(Dispatchers.IO) { SfuScheduleClient().fetch(context, repository.selectedGroupName) }
            }
            val loaded = result.getOrNull()
            if (loaded != null && loaded.lessons.isNotEmpty()) {
                repository.replaceSchedule(loaded.lessons, loaded.currentWeek, repository.selectedSubgroup)
                LessonNotificationWorker.schedule(context, loaded.lessons)
                refresh++
            }
            delay(15 * 60_000L)
        }
    }

    LaunchedEffect(initiallyOpenLessonId) {
        val lesson = repository.lessons.firstOrNull { it.id == initiallyOpenLessonId }
        if (lesson != null) screen = Screen.Capture(repository.createNoteForLesson(lesson).id)
    }

    val appColors = lightColorScheme(
        primary = Color(0xFF6F45D7),
        onPrimary = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White),
        primaryContainer = Color(0xFFECE5FF),
        onPrimaryContainer = Color(0xFF2D175F),
        secondary = Color(0xFF746B80),
        secondaryContainer = Color(0xFFECE8F1),
        background = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.background else Color(0xFFFCFBFF)),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF4F1FA),
        outline = Color(0xFFD6D0DF)
    )
    val appShapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    )
    MaterialTheme(colorScheme = appColors, shapes = appShapes) {
        androidx.compose.material3.MaterialTheme(
        colorScheme = if (appDarkTheme) androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF7B32F6), secondary = Color(0xFFB69CFF),
            background = Color(0xFF071522), surface = Color(0xFF0D2031),
            surfaceVariant = Color(0xFF12283D), onBackground = Color(0xFFF7F8FC),
            onSurface = Color(0xFFF7F8FC), onSurfaceVariant = Color(0xFF9AAAC3),
            outline = Color(0xFF29384D)
        ) else androidx.compose.material3.lightColorScheme(primary = Color(0xFF7044E1))
    ) {
        Surface(Modifier.fillMaxSize()) {
            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    repository = repository,
                    refreshKey = refresh,
                    onRefresh = { refresh++ },
                    onSettings = { screen = Screen.Settings },
                    onAddNote = { screen = Screen.AddNoteSubjects },
                    onArchive = { screen = Screen.Archive },
                    onHomework = { screen = Screen.Homework },
                    onStatistics = { screen = Screen.Statistics },
                    listState = homeListState
                )
                Screen.Homework -> HomeworkScreen(
                    repository = repository,
                    refreshKey = refresh,
                    onRefresh = { refresh++ },
                    onBack = { screen = Screen.Home },
                    listState = homeworkListState
                )
                Screen.Settings -> SettingsScreen(repository, { refresh++ }, { screen = Screen.Home })
                Screen.Archive -> ArchiveScreen(repository, refresh, { screen = Screen.Home }, { subject, subgroup -> screen = Screen.SubjectArchive(subject, subgroup) })
                Screen.AddNoteSubjects -> AddNoteSubjectsScreen(repository, { screen = Screen.Home }, { screen = Screen.AddNoteDates(it) })
                is Screen.AddNoteDates -> AddNoteDatesScreen(
                    repository = repository,
                    subject = current.subject,
                    onBack = { screen = Screen.AddNoteSubjects },
                    onCapture = { noteId -> editorReturnScreen = Screen.Home; screen = Screen.Capture(noteId) }
                )
                Screen.Statistics -> StatisticsScreen(repository, { refresh++ }, { screen = Screen.Home })
                is Screen.SubjectArchive -> SubjectArchiveScreen(repository, current.subject, current.subgroup, refresh, { screen = Screen.Archive }, { editorReturnScreen = current; screen = Screen.Editor(it) })
                is Screen.Capture -> CaptureScreen(repository, current.noteId, { refresh++; screen = Screen.Home }, { editorReturnScreen = Screen.Home; screen = Screen.Editor(current.noteId) })
                is Screen.Editor -> EditorScreen(repository, current.noteId, { refresh++; screen = editorReturnScreen })
                Screen.Schedule -> HomeScreen(
                    repository = repository,
                    refreshKey = refresh,
                    onRefresh = { refresh++ },
                    onSettings = { screen = Screen.Settings },
                    onAddNote = { screen = Screen.AddNoteSubjects },
                    onArchive = { screen = Screen.Archive },
                    onHomework = { screen = Screen.Homework },
                    onStatistics = { screen = Screen.Statistics },
                    listState = homeListState
                )
            }
        }
    }


    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!update.required && !updateBusy) availableUpdate = null },
            title = { Text("Доступно обновление ${update.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(update.notes)
                    if (updateBusy && updateProgress >= 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(
                                progress = updateProgress.coerceIn(0, 100) / 100f,
                                modifier = Modifier.size(38.dp),
                                strokeWidth = 4.dp
                            )
                            Text("Загрузка: $updateProgress% · осталось ${100 - updateProgress.coerceIn(0, 100)}%", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !updateBusy,
                    onClick = {
                        if (!AppUpdateManager.canInstallPackages(context)) {
                            updateMessage = "Разреши StudyNotesSFU устанавливать приложения, затем вернись и нажми «Обновить» ещё раз."
                            AppUpdateManager.openInstallPermission(context)
                        } else {
                            updateBusy = true
                            updateProgress = 0
                            updateScope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        AppUpdateManager.downloadAndStartInstall(context, update) { progress ->
                                            updateScope.launch { updateProgress = progress }
                                        }
                                    }
                                }
                                updateMessage = result.exceptionOrNull()?.let { "Не удалось обновить приложение: ${it.message}" }
                                updateBusy = false
                                if (result.isFailure) updateProgress = -1
                            }
                        }
                    }
                ) { Text(if (updateBusy) "Загрузка…" else "Обновить") }
            },
            dismissButton = if (update.required) null else ({
                TextButton(onClick = { dismissedUpdateVersion = update.versionCode; availableUpdate = null }) { Text("Позже") }
            })
        )
    }
    updateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updateMessage = null },
            title = { Text("Обновления") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { updateMessage = null }) { Text("ОК") } }
        )
    }
}

    } // DARK_THEME_WRAPPER_V0849

@Composable
private fun HomeScreen(
    repository: AppRepository,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onAddNote: () -> Unit,
    onArchive: () -> Unit,
    onHomework: () -> Unit,
    onStatistics: () -> Unit,
    listState: LazyListState
) {
    refreshKey.hashCode()
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }
    var weekStart by remember { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    val lessons = repository.lessonsForDate(repository.selectedSubgroup, selectedDate)
    val ru = Locale("ru")
    val dayLabel = if (selectedDate == today) "Пары сегодня" else "Пары на ${selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yy"))}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StudyNotesSFU", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Настройки", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White))
            )
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize().background(if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.background else Color(0xFFFCFBFF)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ChevronLeft, "Предыдущая неделя")
                            }
                            Text(
                                weekStart.format(DateTimeFormatter.ofPattern("LLLL yyyy", ru)).replaceFirstChar { it.uppercase() },
                                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ChevronRight, "Следующая неделя")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (0..6).forEach { index ->
                                val date = weekStart.plusDays(index.toLong())
                                val selected = date == selectedDate
                                val isToday = date == java.time.LocalDate.now() // TODAY_HIGHLIGHT_V0848
                                val hasLessons = repository.lessonsForDate(repository.selectedSubgroup, date).isNotEmpty()
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .semantics { contentDescription = "VISUAL_PAIR" }.clickable { selectedDate = date }
                                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .padding(vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, ru).replace(".", ""), fontSize = 11.sp, color = if (selected) (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White) else Color(0xFF5F5870))
                                    Text(date.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White) else Color(0xFF25212D))
                                    Box(Modifier.padding(top = 3.dp).size(4.dp).clip(androidx.compose.foundation.shape.CircleShape /* HOMEWORK_DOT_V0849 HOMEWORK_ONLY_V0853 */).background(if (hasLessons) (if (selected) (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White) else MaterialTheme.colorScheme.primary) else Color.Transparent))
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Быстрые действия", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard("Добавить\nконспект", Icons.Default.PhotoCamera, Color(0xFF42A5F5), Color(0xFFEAF5FF), onAddNote, Modifier.weight(1f))
                        QuickActionCard("Мои\nконспекты", Icons.Default.Description, Color(0xFF21C99A), Color(0xFFEAFBF5), onArchive, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard("Домашнее\nзадание", Icons.Default.CalendarMonth, Color(0xFF7546E8), Color(0xFFF2ECFF), onHomework, Modifier.weight(1f))
                        QuickActionCard("Статистика", Icons.Default.BarChart, Color(0xFF2783F3), Color(0xFFEAF3FF), onStatistics, Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(dayLabel, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("• ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM.", ru)).replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, color = Color(0xFF766E84))
                }
            }

            if (lessons.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White))) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(62.dp), tint = Color(0xFFC6B8F5))
                            Spacer(Modifier.height(12.dp))
                            Text("Расписание еще не сформировано", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text("На выбранный день занятий нет или расписание пока отсутствует.", fontSize = 13.sp, color = Color(0xFF7A7286), textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(lessons, key = { it.id }) { lesson ->
                    ScheduleLessonCard(lesson)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EDFF))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (selectedDate == today) "Хороший день для обучения!\nВсе пары на сегодня отображены." else "Все пары на выбранный день отображены.", fontSize = 13.sp, color = Color(0xFF4F3B9A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    // QUICK_ACTION_PARENT_SANITIZED_V0847 // card_removed=1, parent_removed=0
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(112.dp)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(31.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 16.sp, color = Color(0xFF25212D))
        }
    }
}

@Composable
private fun ScheduleLessonCard(lesson: Lesson) {
    Card(
        colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(lesson.start.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(lesson.end.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            VerticalDivider(Modifier.height(52.dp), color = Color(0xFFE4DFEA))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(lesson.subject, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lesson.kind.isNotBlank()) {
                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp), color = Color(0xFFEDE6FF)) {
                            Text(lesson.kind, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (lesson.room.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = Color(0xFF8B82A0))
                            Spacer(Modifier.width(3.dp))
                            Text(lesson.room, fontSize = 11.sp, color = Color(0xFF6E667B))
                        }
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF7C6FC0))
        }
    }
}


@Composable
private fun ManualNoteOrScheduleDialog(
    repository: AppRepository,
    initialDateText: String,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onOpenCapture: (String) -> Unit
) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var dateText by remember { mutableStateOf(initialDateText) }
    val parsedDate = runCatching { LocalDate.parse(dateText.trim(), dateFmt) }.getOrNull()
    var selectedLessonId by remember { mutableStateOf<String?>(null) }
    var selectedPairNumber by remember { mutableStateOf<Int?>(null) }
    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var subjectMenu by remember { mutableStateOf(false) }
    var teacherMenu by remember { mutableStateOf(false) }
    var pairMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val subgroup = repository.selectedSubgroup
    val pairOptions = parsedDate?.let { repository.lessonsForDate(subgroup, it) }.orEmpty()
    val pairSlots = remember {
        listOf(
            1 to (LocalTime.of(8, 30) to LocalTime.of(10, 5)),
            2 to (LocalTime.of(10, 15) to LocalTime.of(11, 50)),
            3 to (LocalTime.of(12, 0) to LocalTime.of(13, 35)),
            4 to (LocalTime.of(14, 10) to LocalTime.of(15, 45)),
            5 to (LocalTime.of(15, 55) to LocalTime.of(17, 30)),
            6 to (LocalTime.of(17, 40) to LocalTime.of(19, 15)),
            7 to (LocalTime.of(19, 25) to LocalTime.of(21, 0))
        )
    }
    val selectedLesson = pairOptions.firstOrNull { it.id == selectedLessonId }
    val selectedSlot = pairSlots.firstOrNull { it.first == selectedPairNumber }
    val subjectTeacherOptions = repository.subjectTeacherOptions(subgroup)
    val subjectOptions = subjectTeacherOptions.map { it.first }.distinct()
    val teacherOptions = subjectTeacherOptions
        .filter { subject.isBlank() || it.first.equals(subject, ignoreCase = true) }
        .map { it.second }.filter { it.isNotBlank() }.distinct()
        .ifEmpty { subjectTeacherOptions.map { it.second }.filter { it.isNotBlank() }.distinct() }

    LaunchedEffect(dateText) {
        selectedLessonId = null
        selectedPairNumber = null
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый конспект / замена пары") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Можно создать отдельный конспект или вручную заменить занятие, если расписание изменилось.", fontSize = 13.sp)
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; error = null },
                    label = { Text("Дата (дд.мм.гггг)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (parsedDate != null) {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { pairMenu = true }, Modifier.fillMaxWidth()) {
                            val slotLabel = selectedSlot?.let { (number, times) ->
                                val existing = pairOptions.firstOrNull { it.start == times.first && it.end == times.second }
                                if (existing != null) "$number пара · ${existing.start}–${existing.end} · ${existing.subject}"
                                else "$number пара · ${times.first}–${times.second} · свободное окно"
                            }
                            Text(slotLabel ?: "Какую пару заменить / добавить?", maxLines = 2)
                        }
                        DropdownMenu(expanded = pairMenu, onDismissRequest = { pairMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Не изменять расписание — только создать конспект") },
                                onClick = { selectedLessonId = null; selectedPairNumber = null; pairMenu = false }
                            )
                            pairSlots.forEach { (number, times) ->
                                val existing = pairOptions.firstOrNull { it.start == times.first && it.end == times.second }
                                DropdownMenuItem(
                                    text = {
                                        if (existing != null) {
                                            Text("$number пара · ${times.first}–${times.second} · ${existing.subject} · ${existing.teacher}")
                                        } else {
                                            Text("$number пара · ${times.first}–${times.second} · свободное окно")
                                        }
                                    },
                                    onClick = {
                                        selectedPairNumber = number
                                        selectedLessonId = existing?.id
                                        if (existing != null) {
                                            subject = existing.subject
                                            teacher = existing.teacher
                                        } else {
                                            subject = ""
                                            teacher = ""
                                        }
                                        pairMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it; error = null },
                        label = { Text("Предмет") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { subjectMenu = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                    )
                    DropdownMenu(expanded = subjectMenu, onDismissRequest = { subjectMenu = false }) {
                        subjectOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                subject = option
                                subjectTeacherOptions.firstOrNull { it.first == option && it.second.isNotBlank() }?.let { teacher = it.second }
                                subjectMenu = false
                            })
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("Преподаватель") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { teacherMenu = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                    )
                    DropdownMenu(expanded = teacherMenu, onDismissRequest = { teacherMenu = false }) {
                        teacherOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { teacher = option; teacherMenu = false })
                        }
                    }
                }

                selectedLesson?.let { lesson ->
                    when {
                        repository.isManualLesson(subgroup, lesson.id) -> {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Эта пара добавлена вручную в свободное окно.", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    TextButton(onClick = {
                                        repository.removeManualLesson(subgroup, lesson.id)
                                        selectedLessonId = null
                                        subject = ""
                                        teacher = ""
                                        onChanged()
                                    }) { Text("Удалить ручную пару и вернуть свободное окно") }
                                }
                            }
                        }
                        repository.isLessonOverridden(subgroup, lesson.id) -> {
                            val original = repository.originalLessonFor(subgroup, lesson.id)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Эта пара изменена вручную.", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    original?.let { Text("На сайте: ${it.subject} · ${it.teacher}", fontSize = 12.sp) }
                                    TextButton(onClick = {
                                        val restored = repository.restoreLessonOverride(subgroup, lesson.id)
                                        restored?.let { subject = it.subject; teacher = it.teacher }
                                        onChanged()
                                    }) { Text("Вернуть как на сайте СФУ") }
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    parsedDate == null -> error = "Дата должна быть в формате дд.мм.гггг."
                    subject.isBlank() -> error = "Укажи предмет или выбери его из списка."
                    selectedLesson != null -> {
                        repository.applyLessonOverride(subgroup, selectedLesson.id, subject, teacher)
                        onChanged()
                        onDismiss()
                    }
                    selectedSlot != null -> {
                        val times = selectedSlot.second
                        repository.addManualLesson(subgroup, parsedDate, times.first, times.second, subject, teacher)
                        onChanged()
                        onDismiss()
                    }
                    else -> {
                        val n = repository.createManualNote(subject.trim(), parsedDate)
                        onChanged()
                        onDismiss()
                        onOpenCapture(n.id)
                    }
                }
            }) {
                Text(when {
                    selectedLesson != null -> "Заменить пару"
                    selectedSlot != null -> "Добавить пару"
                    else -> "Создать"
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun LessonActionCard(
    repository: AppRepository,
    lesson: Lesson,
    now: LocalDateTime,
    onRefresh: () -> Unit,
    onOpenCapture: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    showOfficialChanges: Boolean = false
) {
    val note = repository.noteForLesson(lesson.id)
    val storedNoNotes = repository.hasNoNotes(lesson.id)
    var justMarkedNoNotes by remember(lesson.id) { mutableStateOf(false) }
    val noNotes = storedNoNotes || justMarkedNoNotes
    val ended = now.isAfter(LocalDateTime.of(lesson.date, lesson.end))
    val homeworks = repository.homeworkForLesson(lesson.id)
    val noHomework = repository.hasNoHomework(lesson.id)
    val lessonMemo = repository.lessonNote(lesson.id)
    var showHomeworkDialog by remember(lesson.id) { mutableStateOf(false) }
    var showNoteDialog by remember(lesson.id) { mutableStateOf(false) }
    val officialChanged = showOfficialChanges && repository.isOfficiallyChanged(repository.selectedSubgroup, lesson)
    val officialAdded = showOfficialChanges && repository.wasOfficiallyAdded(repository.selectedSubgroup, lesson)
    val officialPublished = showOfficialChanges && repository.wasOfficiallyPublished(repository.selectedSubgroup, lesson)
    val previousOfficial = if (officialChanged) repository.previousOfficialLesson(repository.selectedSubgroup, lesson) else null
    var showPreviousOfficial by remember(lesson.id, officialChanged) { mutableStateOf(false) }
    val officialChangeGreen = Color(0xFF2E7D32)

    Card(
        Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${lesson.start}–${lesson.end}", fontWeight = FontWeight.Bold)
                Text(lesson.kind, fontSize = 12.sp)
            }
            Text(
                lesson.subject,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    officialChanged -> officialChangeGreen
                    officialAdded || officialPublished -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (officialPublished) {
                Text("Опубликовано новое расписание СФУ", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else if (officialAdded) {
                Text("Добавлено СФУ", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (officialChanged) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Изменено СФУ", color = officialChangeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showPreviousOfficial = !showPreviousOfficial }) {
                        Text(if (showPreviousOfficial) "Скрыть" else "Что было раньше", color = officialChangeGreen, fontSize = 11.sp)
                    }
                }
                if (showPreviousOfficial) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = officialChangeGreen.copy(alpha = 0.08f))) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("До изменения на сайте СФУ:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = officialChangeGreen)
                            if (previousOfficial != null) {
                                Text(previousOfficial.subject, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (previousOfficial.kind.isNotBlank()) Text(previousOfficial.kind, fontSize = 11.sp)
                                if (previousOfficial.teacher.isNotBlank()) Text(previousOfficial.teacher, fontSize = 11.sp)
                                if (previousOfficial.room.isNotBlank()) Text(previousOfficial.room, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (lesson.teacher.isNotBlank()) Text(lesson.teacher, fontSize = 13.sp)
            if (lesson.room.isNotBlank()) Text(lesson.room, fontSize = 13.sp)
            if (repository.isLessonOverridden(repository.selectedSubgroup, lesson.id)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Изменено вручную", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { repository.restoreLessonOverride(repository.selectedSubgroup, lesson.id); onRefresh() }) {
                        Text("Вернуть как на сайте", fontSize = 11.sp)
                    }
                }
            }

            when {
                note != null -> {
                    Text("Конспект: ${statusLabel(note.status)} · ${note.imagePaths.size} фото", fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpenCapture(note.id) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Добавить страницы", maxLines = 1, softWrap = false, fontSize = 11.sp)
                        }
                        if (note.draftText.isNotBlank() || (note.status == NoteStatus.FINAL || note.status == NoteStatus.FINAL_SYNCED)) {
                            OutlinedButton(onClick = { onOpenEditor(note.id) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                Text("Открыть", maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }
                        }
                    }
                }
                noNotes -> {
                    Text("Записей по этой паре не было.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            repository.clearNoNotes(lesson.id)
                            justMarkedNoNotes = false
                            val n = repository.createNoteForLesson(lesson)
                            onRefresh()
                            onOpenCapture(n.id)
                        }) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Добавить конспект", maxLines = 1, softWrap = false, fontSize = 11.sp)
                        }
                        TextButton(onClick = { repository.clearNoNotes(lesson.id); justMarkedNoNotes = false; onRefresh() }) { Text("Отменить отметку", maxLines = 1, softWrap = false, fontSize = 11.sp) }
                    }
                }
                ended -> {
                    Text("Требуется действие", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = {
                        val n = repository.createNoteForLesson(lesson)
                        onRefresh()
                        onOpenCapture(n.id)
                    }) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить конспект", maxLines = 1, softWrap = false, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { repository.markNoNotes(lesson.id); justMarkedNoNotes = true }) {
                        Icon(Icons.Default.DoNotDisturbAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Записей не было", maxLines = 1, softWrap = false, fontSize = 11.sp)
                    }
                }
                else -> Text("Действия с конспектом станут доступны после окончания пары", fontSize = 12.sp)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showHomeworkDialog = true }, Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Default.Assignment, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ДЗ", maxLines = 1, softWrap = false, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showNoteDialog = true }, Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Default.StickyNote2, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (lessonMemo.isBlank()) "Заметка" else "Заметка ✓", maxLines = 1, softWrap = false, fontSize = 12.sp)
                }
            }
        }
    }

    if (showHomeworkDialog) HomeworkForLessonDialog(
        repository = repository,
        lesson = lesson,
        onDismiss = { showHomeworkDialog = false },
        onChanged = { onRefresh() }
    )

    if (showNoteDialog) LessonNoteDialog(
        repository = repository,
        lessonId = lesson.id,
        initial = lessonMemo,
        onDismiss = { showNoteDialog = false },
        onSave = {
            repository.setLessonNote(lesson.id, it)
            repository.clearLessonNoteDraft(lesson.id)
            showNoteDialog = false
            onRefresh()
        }
    )
}

@Composable
private fun NativeMultilineEditor(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    minHeight: Int = 140
) {
    fun openIme(edit: EditText) {
        edit.isFocusable = true
        edit.isFocusableInTouchMode = true
        edit.showSoftInputOnFocus = true
        edit.requestFocus()

        // В Compose AlertDialog поле живёт в отдельном окне. showSoftInput(),
        // вызванный до получения window focus, Android/realme может проигнорировать.
        // Поэтому сначала ждём attachment/window focus, затем просим IME через
        // WindowInsetsController именно этого View и дублируем стандартным IMM.
        val requestIme = {
            if (edit.isAttachedToWindow && edit.hasWindowFocus()) {
                ViewCompat.getWindowInsetsController(edit)?.show(WindowInsetsCompat.Type.ime())
                val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        edit.post { requestIme() }
        edit.postDelayed({ requestIme() }, 120)
        edit.postDelayed({ requestIme() }, 350)
    }

    AndroidView(
        factory = { context ->
            object : EditText(context) {
                override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                    super.onWindowFocusChanged(hasWindowFocus)
                    if (hasWindowFocus && hasFocus()) post { openIme(this) }
                }
            }.apply {
                setText(value)
                setHint(hint)
                gravity = Gravity.TOP or Gravity.START
                isSingleLine = false
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 5
                setPadding(24, 20, 24, 20)
                setSelectAllOnFocus(false)
                isFocusable = true
                isFocusableInTouchMode = true
                showSoftInputOnFocus = true
                doAfterTextChanged { editable -> onValueChange(editable?.toString().orEmpty()) }
                setOnClickListener { openIme(this) }
                setOnFocusChangeListener { _, hasFocus -> if (hasFocus) openIme(this) }
            }
        },
        update = { edit ->
            if (edit.text.toString() != value) {
                val pos = edit.selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
                edit.setText(value)
                edit.setSelection(pos.coerceAtMost(edit.text.length))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
    )
}

@Composable
private fun LessonNoteDialog(repository: AppRepository, lessonId: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(lessonId, initial) { mutableStateOf(repository.lessonNoteDraft(lessonId).ifBlank { initial }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка к занятию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Заметка", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                NativeMultilineEditor(
                    value = text,
                    onValueChange = { text = it; repository.setLessonNoteDraft(lessonId, it) },
                    hint = "Например: преподаватель перенёс лабораторную, принести отчёт, уточнить формулу…"
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun HomeworkForLessonDialog(
    repository: AppRepository,
    lesson: Lesson,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val savedDraftText = repository.homeworkDraftText(lesson.id)
    val savedDraftDate = repository.homeworkDraftDueDate(lesson.id)
    var mode by remember(lesson.id) { mutableStateOf(if (savedDraftText.isNotBlank() || savedDraftDate != null) "add" else if (repository.homeworkForLesson(lesson.id).isNotEmpty()) "list" else "choice") }
    var editingHomeworkId by remember(lesson.id) { mutableStateOf<String?>(null) }
    var text by remember(lesson.id) { mutableStateOf(savedDraftText) }
    var dueDate by remember(lesson.id) { mutableStateOf(savedDraftDate) }
    var showCalendar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val existing = repository.homeworkForLesson(lesson.id)
    val noHomework = repository.hasNoHomework(lesson.id)
    val suggestedDates = repository.futureLessonDatesForSubject(lesson)


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Домашнее задание", modifier = Modifier.semantics { contentDescription = "VISUAL_HOMEWORK" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${lesson.subject} · ${lesson.date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))}", fontSize = 13.sp)
                when (mode) {
                    "choice" -> {
                        if (noHomework) Text("Для этой пары отмечено: домашнего задания не было.", fontSize = 13.sp)
                        Button(onClick = {
                            repository.clearNoHomework(lesson.id)
                            mode = "add"
                            onChanged()
                        }, Modifier.fillMaxWidth()) { Text("Добавить ДЗ") }
                        OutlinedButton(onClick = {
                            repository.markNoHomework(lesson.id)
                            onChanged()
                            onDismiss()
                        }, Modifier.fillMaxWidth()) { Text("ДЗ не было") }
                    }
                    "list" -> {
                        existing.forEach { h ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(h.text, fontWeight = FontWeight.SemiBold)
                                    Text("Срок: ${h.dueDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))}", fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = {
                                            repository.setHomeworkCompleted(h.id, !h.completed)
                                            onChanged()
                                        }) { Text(if (h.completed) "Вернуть в работу" else "Выполнено") }
                                        TextButton(onClick = {
                                            editingHomeworkId = h.id
                                            text = h.text
                                            dueDate = h.dueDate
                                            mode = "edit"
                                        }) { Text("Редактировать") }
                                    }
                                    TextButton(
                                        onClick = { repository.deleteHomework(h.id); onChanged() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Удалить домашнее задание") }
                                }
                            }
                        }
                        Button(onClick = { mode = "add" }, Modifier.fillMaxWidth()) { Text("Добавить ещё ДЗ") }
                    }
                    "add", "edit" -> {
                        Text(if (mode == "edit") "Редактирование задания" else "Что нужно сделать", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        NativeMultilineEditor(
                            value = text,
                            onValueChange = { text = it; if (mode == "add") repository.setHomeworkDraft(lesson.id, it, dueDate); error = null },
                            hint = "Запиши домашнее задание…",
                            minHeight = 120
                        )
                        OutlinedButton(onClick = { if (mode == "add") repository.setHomeworkDraft(lesson.id, text, dueDate); showCalendar = true }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Event, null)
                            Spacer(Modifier.width(6.dp))
                            Text(dueDate?.let { "Срок: ${it.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))}" } ?: "Добавить дату")
                        }
                        if (suggestedDates.isNotEmpty()) {
                            Text("В календаре выделены будущие занятия по этому предмету.", fontSize = 12.sp)
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            if (mode == "add" || mode == "edit") Button(onClick = {
                when {
                    text.isBlank() -> error = "Запиши домашнее задание."
                    dueDate == null -> error = "Выбери крайний срок."
                    else -> {
                        if (mode == "edit" && editingHomeworkId != null) {
                            repository.updateHomework(editingHomeworkId!!, text, dueDate!!)
                        } else {
                            repository.addHomework(lesson, text, dueDate!!)
                            repository.clearHomeworkDraft(lesson.id)
                        }
                        onChanged()
                        onDismiss()
                    }
                }
            }) { Text(if (mode == "edit") "Сохранить изменения" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )

    if (showCalendar) MiniCalendarDialog(
        initialMonth = YearMonth.from(dueDate ?: lesson.date.plusDays(1)),
        minDate = lesson.date,
        selectedDate = dueDate,
        highlightedDates = suggestedDates,
        onDismiss = { showCalendar = false },
        onSelect = { dueDate = it; if (mode == "add") repository.setHomeworkDraft(lesson.id, text, it); showCalendar = false }
    )
}

@Composable
private fun MiniCalendarDialog(
    initialMonth: YearMonth,
    minDate: LocalDate,
    selectedDate: LocalDate?,
    highlightedDates: Set<LocalDate>,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    var month by remember { mutableStateOf(initialMonth) }
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - 1).coerceAtLeast(0)
    val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val rows = cells.chunked(7)
    val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Крайний срок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { month = month.minusMonths(1) }, enabled = month > YearMonth.from(minDate)) { Icon(Icons.Default.ChevronLeft, null) }
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        (row + List(7 - row.size) { null }).forEach { date ->
                            Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val enabled = !date.isBefore(minDate)
                                    val highlighted = date in highlightedDates
                                    val selected = date == selectedDate
                                    val container = when {
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        highlighted -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    Surface(
                                        onClick = { if (enabled) onSelect(date) },
                                        enabled = enabled,
                                        shape = MaterialTheme.shapes.small,
                                        color = container,
                                        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(date.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal)
                                            if (highlighted) {
                                                Box(
                                                    Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = 3.dp)
                                                        .size(4.dp)
                                                        .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (highlightedDates.any { YearMonth.from(it) == month }) {
                    Text("Подсвеченные даты — будущие занятия по этому же предмету.", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ArchiveScreen(
    repository: AppRepository,
    refreshKey: Int,
    onBack: () -> Unit,
    onOpenSubject: (String, Int) -> Unit
) {
    refreshKey.hashCode()
    var archiveSubgroup by remember { mutableIntStateOf(repository.selectedSubgroup) }
    val notes = repository.notesForSubgroup(archiveSubgroup)
        .filter { it.imagePaths.isNotEmpty() || it.draftText.isNotBlank() || it.finalText.isNotBlank() }
    val noNoteLessons = repository.noNoteLessonsForSubgroup(archiveSubgroup)
    val subjects = (notes.map { it.subject.trim() } + noNoteLessons.map { it.subject.trim() })
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale("ru")) }
        .sortedBy { it.lowercase(Locale("ru")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои конспекты", maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Конспекты сгруппированы по дисциплинам. Здесь можно открыть любой сохранённый материал.", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (archiveSubgroup == 1) {
                        Button(onClick = {}, Modifier.weight(1f)) { Text("1 подгруппа", maxLines = 1, softWrap = false, fontSize = 12.sp) }
                        OutlinedButton(onClick = { archiveSubgroup = 2 }, Modifier.weight(1f)) { Text("2 подгруппа", maxLines = 1, softWrap = false, fontSize = 12.sp) }
                    } else {
                        OutlinedButton(onClick = { archiveSubgroup = 1 }, Modifier.weight(1f)) { Text("1 подгруппа", maxLines = 1, softWrap = false, fontSize = 12.sp) }
                        Button(onClick = {}, Modifier.weight(1f)) { Text("2 подгруппа", maxLines = 1, softWrap = false, fontSize = 12.sp) }
                    }
                }
            }
            if (subjects.isEmpty()) item { Text("Для ${archiveSubgroup}-й подгруппы архив пока пуст.") }
            items(subjects, key = { it.lowercase(Locale("ru")) }) { subject ->
                val subjectNotes = notes.filter { it.subject.trim().equals(subject, ignoreCase = true) }
                val subjectNoNotes = noNoteLessons.filter { it.subject.trim().equals(subject, ignoreCase = true) }
                val finalCount = subjectNotes.count { it.status == NoteStatus.FINAL || it.status == NoteStatus.FINAL_SYNCED }
                val latestDate = (subjectNotes.map { it.date } + subjectNoNotes.map { it.date }).maxOrNull()
                Card(Modifier.fillMaxWidth().clickable { onOpenSubject(subject, archiveSubgroup) }) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subject, fontWeight = FontWeight.Bold)
                            Text("Конспектов: ${subjectNotes.size} · чистовиков: $finalCount · без записей: ${subjectNoNotes.size}", fontSize = 12.sp)
                            latestDate?.let { Text("Последняя запись: ${it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", fontSize = 12.sp) }
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectArchiveScreen(
    repository: AppRepository,
    subject: String,
    subgroup: Int,
    refreshKey: Int,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit
) {
    refreshKey.hashCode()
    var localRefresh by remember { mutableIntStateOf(0) }
    localRefresh.hashCode()
    val notes = repository.notesForSubgroup(subgroup)
        .filter { it.subject.trim().equals(subject.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<Note> { it.date }.thenByDescending { it.createdAt })
    val noNoteLessons = repository.noNoteLessonsForSubgroup(subgroup)
        .filter { it.subject.trim().equals(subject.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<Lesson> { it.date }.thenByDescending { it.start })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            subject,
                            maxLines = 2,
                            softWrap = true,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            fontSize = 18.sp
                        )
                        Text("${subgroup} подгруппа", fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Конспекты", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (notes.isEmpty()) item { Text("Сохранённых конспектов пока нет.", fontSize = 13.sp) }
            items(notes, key = { "note_${it.id}" }) { note ->
                val lesson = note.lessonId?.let { id -> repository.lessonByIdAnySubgroup(id) }
                Card(Modifier.fillMaxWidth().clickable { onOpenEditor(note.id) }) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(note.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("ru"))), fontWeight = FontWeight.Bold)
                            if (!lesson?.kind.isNullOrBlank()) Text(lesson!!.kind, fontSize = 12.sp)
                            if (!lesson?.teacher.isNullOrBlank()) Text(lesson!!.teacher, fontSize = 12.sp)
                            Text("${note.imagePaths.size} стр. · ${statusLabel(note.status)}", fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text("Записей не было", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Эти отметки не считаются конспектами. Если отметил занятие случайно, здесь можно вернуть его на главный экран.", fontSize = 12.sp)
            }
            if (noNoteLessons.isEmpty()) item { Text("Таких отметок нет.", fontSize = 13.sp) }
            items(noNoteLessons, key = { "no_${it.id}" }) { lesson ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(lesson.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("ru"))), fontWeight = FontWeight.Bold)
                            Text("${lesson.start}–${lesson.end} · ${lesson.kind}", fontSize = 12.sp)
                            if (lesson.teacher.isNotBlank()) Text(lesson.teacher, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            repository.clearNoNotes(lesson.id)
                            localRefresh++
                        }) { Text("Вернуть") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkScreen(
    repository: AppRepository,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState
) {
    refreshKey.hashCode()
    var editingHomework by remember { mutableStateOf<Homework?>(null) }
    val today = LocalDate.now()
    val sorted = repository.homeworks.sortedWith(compareBy<Homework> { it.completed }.thenBy { it.dueDate }.thenBy { it.createdAt })
    Scaffold(
        topBar = { TopAppBar(title = { Text("Домашнее задание", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }) }
    ) { pad ->
        LazyColumn(state = listState, modifier = Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sorted.isEmpty()) item { Text("Домашних заданий пока нет.") }
            items(sorted, key = { it.id }) { h ->
                val days = ChronoUnit.DAYS.between(today, h.dueDate)
                val urgency = when { h.completed -> "Выполнено"; days < 0 -> "Просрочено на ${-days} дн."; days == 0L -> "Срок сегодня"; days == 1L -> "Срок завтра"; else -> "Осталось $days дн." }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(h.subject, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(urgency, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(h.text)
                        Text("Крайний срок: ${h.dueDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")))}", fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { repository.setHomeworkCompleted(h.id, !h.completed); onRefresh() }) { Icon(if (h.completed) Icons.Default.Undo else Icons.Default.CheckCircle, null); Spacer(Modifier.width(5.dp)); Text(if (h.completed) "Вернуть" else "Выполнено") }
                            TextButton(onClick = { editingHomework = h }) { Text("Редактировать") }
                        }
                    }
                }
            }
        }
    }
    editingHomework?.let { homework ->
        HomeworkEditDialog(repository, homework, { editingHomework = null }) { text, dueDate -> repository.updateHomework(homework.id, text, dueDate); editingHomework = null; onRefresh() }
    }
}


@Composable
private fun HomeworkEditDialog(
    repository: AppRepository,
    homework: Homework,
    onDismiss: () -> Unit,
    onSaved: (String, LocalDate) -> Unit
) {
    var text by remember(homework.id) { mutableStateOf(homework.text) }
    var dueDate by remember(homework.id) { mutableStateOf(homework.dueDate) }
    var showCalendar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lesson = repository.lessonByIdAnySubgroup(homework.lessonId)
    val highlighted = lesson?.let { repository.futureLessonDatesForSubject(it) }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать ДЗ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(homework.subject, fontWeight = FontWeight.SemiBold)
                NativeMultilineEditor(
                    value = text,
                    onValueChange = { text = it; error = null },
                    hint = "Что нужно сделать…",
                    minHeight = 120
                )
                OutlinedButton(onClick = { showCalendar = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Event, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Срок: ${dueDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))}")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { Button(onClick = {
            if (text.isBlank()) error = "Текст задания не может быть пустым."
            else onSaved(text, dueDate)
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showCalendar) {
        MiniCalendarDialog(
            initialMonth = YearMonth.from(dueDate),
            minDate = lesson?.date ?: homework.assignedDate,
            selectedDate = dueDate,
            highlightedDates = highlighted,
            onDismiss = { showCalendar = false },
            onSelect = { dueDate = it; showCalendar = false }
        )
    }
}

@Composable
private fun ScheduleScreen(
    repository: AppRepository,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSchedule: () -> Unit,
    onHomework: () -> Unit,
    onNotes: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    listState: LazyListState,
    onOpenCapture: (String) -> Unit,
    onOpenEditor: (String) -> Unit
) {
    refreshKey.hashCode()
    var weekOffset by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monday = currentMonday.plusWeeks(weekOffset.toLong())
    val sunday = monday.plusDays(6)
    val weekKind = SfuScheduleClient().currentWeekKind(monday)
    val lessons = repository.lessons.filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
        .sortedWith(compareBy<Lesson> { it.date }.thenBy { it.start })
    val days = (0L..6L).map { monday.plusDays(it) }
    val purple = MaterialTheme.colorScheme.primary
    val scheduleScope = rememberCoroutineScope()

    fun itemIndexForDate(target: LocalDate): Int {
        var index = 1 // week card is item 0
        for (date in days) {
            if (date == target) return index
            val count = lessons.count { it.date == date }
            index += 1 + if (count == 0) 1 else count
        }
        return 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Расписание", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("${weekLabel(weekKind).replaceFirstChar { it.uppercase() }} неделя", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                selected = BottomTab.SCHEDULE,
                onSchedule = onSchedule,
                onHomework = onHomework,
                onNotes = onNotes,
                onArchive = onArchive,
                onSettings = onSettings
            )
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { if (weekOffset > -1) weekOffset-- }, enabled = weekOffset > -1) {
                                Icon(Icons.Default.ChevronLeft, "Предыдущая неделя")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Неделя ${if (weekKind == WeekKind.ODD) "1" else "2"}", fontWeight = FontWeight.Bold, color = purple)
                                Text("${monday.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))} — ${sunday.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))}", fontSize = 12.sp)
                            }
                            IconButton(onClick = { if (weekOffset < 1) weekOffset++ }, enabled = weekOffset < 1) {
                                Icon(Icons.Default.ChevronRight, "Следующая неделя")
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            days.forEach { date ->
                                val isToday = date == today
                                Surface(
                                    onClick = {
                                        scheduleScope.launch {
                                            listState.animateScrollToItem(itemIndexForDate(date))
                                        }
                                    },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                    color = if (isToday) purple else Color.Transparent,
                                    modifier = Modifier.width(42.dp)
                                ) {
                                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(date.format(DateTimeFormatter.ofPattern("EE", Locale("ru"))).replace(".", ""), fontSize = 10.sp, color = if (isToday) (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White) else purple, fontWeight = FontWeight.SemiBold)
                                        Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = if (isToday) (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White) else purple)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            days.forEach { date ->
                val dayLessons = lessons.filter { it.date == date }
                item {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = purple,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
                if (dayLessons.isEmpty()) {
                    item {
                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("Занятий нет.", fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(dayLessons) { lesson ->
                        LessonActionCard(repository, lesson, LocalDateTime.now(), onRefresh, onOpenCapture, onOpenEditor, showOfficialChanges = true)
                    }
                }
            }
        }
    }
}


@Composable
private fun AddNoteSubjectsScreen(repository: AppRepository, onBack: () -> Unit, onSubject: (String) -> Unit) {
    val subjects = repository.subjectTeacherOptions(repository.selectedSubgroup).map { it.first }.distinct().sortedBy { it.lowercase(Locale("ru")) }
    Scaffold(topBar = { TopAppBar(title = { Text("Добавить конспект", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Выбери дисциплину", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (subjects.isEmpty()) item { Text("Сначала обнови расписание СФУ — список дисциплин пока пуст.") }
            items(subjects.chunked(2)) { rowSubjects ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSubjects.forEach { subject ->
                        SubjectChoiceCard(subject, Modifier.weight(1f)) { onSubject(subject) }
                    }
                    if (rowSubjects.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SubjectChoiceCard(subject: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val low = subject.lowercase(Locale("ru"))
    val icon = when {
        "экол" in low -> Icons.Default.Eco
        "информ" in low || "программ" in low -> Icons.Default.Computer
        "иностран" in low || "англ" in low -> Icons.Default.Language
        "физ" in low -> Icons.Default.FitnessCenter
        "эконом" in low -> Icons.Default.BarChart
        else -> Icons.Default.MenuBook
    }
    val tint = when {
        "экол" in low -> Color(0xFF18B77B)
        "информ" in low -> Color(0xFF247BEA)
        "иностран" in low -> Color(0xFF2487E8)
        "физ" in low -> Color(0xFFFF684F)
        "эконом" in low -> Color(0xFF7546E8)
        else -> Color(0xFF4D83E8)
    }
    Card(modifier = modifier.height(132.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f))) {
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(35.dp)); Spacer(Modifier.height(9.dp)); Text(subject, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun AddNoteDatesScreen(repository: AppRepository, subject: String, onBack: () -> Unit, onCapture: (String) -> Unit) {
    val dates = repository.scheduleForSubgroup(repository.selectedSubgroup).filter { it.subject.trim().equals(subject.trim(), ignoreCase = true) }.sortedByDescending { it.date }
    var selectedLesson by remember { mutableStateOf<Lesson?>(dates.firstOrNull { it.date == LocalDate.now() } ?: dates.firstOrNull()) }
    Scaffold(topBar = { TopAppBar(title = { Text(subject, maxLines = 1, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Выбери день, к которому относится конспект", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (dates.isEmpty()) item { Text("В сохранённом расписании нет дат по этой дисциплине.") }
            items(dates, key = { it.id }) { lesson ->
                val selected = selectedLesson?.id == lesson.id
                Card(Modifier.fillMaxWidth().clickable { selectedLesson = lesson }, colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEDE5FF) else Color.White), border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lesson.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                            Text("${lesson.start}–${lesson.end} · ${lesson.kind}${if (lesson.room.isNotBlank()) " · ${lesson.room}" else ""}", fontSize = 12.sp)
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Button(onClick = { selectedLesson?.let { onCapture(repository.createNoteForLesson(it).id) } }, enabled = selectedLesson != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Продолжить") }
            }
        }
    }
}

@Composable
private fun StatisticsScreen(repository: AppRepository, onRefresh: () -> Unit, onBack: () -> Unit) {
    var details by remember { mutableStateOf<String?>(null) }
    var showBalanceDialog by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var balanceText by remember { mutableStateOf("") }
    var topUpText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика", fontWeight = FontWeight.Bold, color = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4820AA))
            )
        },
        containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.background else Color(0xFFFCFBFF))
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReferenceServiceCard(
                    title = "OpenAI",
                    amount = String.format(Locale.US, "$%.2f", repository.estimatedOpenAiBalanceUsd),
                    logoRes = ru.studynotes.sfu.R.drawable.openai_logo,
                    onDetails = { details = "openai" },
                    modifier = Modifier.weight(1f)
                )
                ReferenceServiceCard(
                    title = "Vercel Hobby",
                    amount = "$0.00",
                    logoRes = ru.studynotes.sfu.R.drawable.vercel_logo,
                    onDetails = { details = "vercel" },
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EEFF)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = Color(0xFF6E42E6),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Следи за своими расходами\nи используй ресурсы эффективно!",
                        color = Color(0xFF5B31D5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }

    when (details) {
        "openai" -> AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("OpenAI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Расчётный остаток", fontWeight = FontWeight.SemiBold)
                    Text(
                        String.format(Locale.US, "$%.8f", repository.estimatedOpenAiBalanceUsd),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("Стартовая точка: ${String.format(Locale.US, "$%.8f", repository.openAiInitialBalanceUsd)}", fontSize = 12.sp, color = Color(0xFF6D6678))
                    Text("Пополнения: +${String.format(Locale.US, "$%.8f", repository.openAiTotalTopUpsUsd)}", fontSize = 12.sp, color = Color(0xFF6D6678))
                    Text("Расходы приложения: -${String.format(Locale.US, "$%.8f", repository.localOpenAiCostUsd)}", fontSize = 12.sp, color = Color(0xFF6D6678))
                    Text(
                        "Расчёт: старт + пополнения − расходы = остаток",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5B31D5)
                    )
                    Text(
                        "Токены: вход ${formatTokenCount(repository.localOpenAiInputTokens)} · cached ${formatTokenCount(repository.localOpenAiCachedInputTokens)} · выход ${formatTokenCount(repository.localOpenAiOutputTokens)}",
                        fontSize = 12.sp,
                        color = Color(0xFF6D6678)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { balanceText = String.format(Locale.US, "%.8f", repository.estimatedOpenAiBalanceUsd); showBalanceDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Изменить баланс", fontSize = 12.sp) }
                        Button(
                            onClick = { topUpText = ""; showTopUpDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("+ Пополнение", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Закрыть") } }
        )
        "vercel" -> AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("Vercel Hobby") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$0.00", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Function Invocations: ${formatTokenCount(repository.hobbyEstimatedInvocations)} / 1 000 000")
                    Text("Active CPU: ≤ ${String.format(Locale.US, "%.4f", repository.hobbyEstimatedCpuHoursUpperBound)} / 4 ч")
                    Text("Provisioned Memory: ≈ ${String.format(Locale.US, "%.4f", repository.hobbyEstimatedProvisionedMemoryGbHours)} / 360 GB·ч")
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Закрыть") } }
        )
    }

    if (showBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showBalanceDialog = false },
            title = { Text("Текущий баланс OpenAI") },
            text = {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.replace(',', '.') },
                    label = { Text("Баланс, $") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    balanceText.toDoubleOrNull()?.let { repository.setCurrentOpenAiBalance(it); onRefresh() }
                    showBalanceDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showBalanceDialog = false }) { Text("Отмена") } }
        )
    }

    if (showTopUpDialog) {
        AlertDialog(
            onDismissRequest = { showTopUpDialog = false },
            title = { Text("Пополнение OpenAI") },
            text = {
                OutlinedTextField(
                    value = topUpText,
                    onValueChange = { topUpText = it.replace(',', '.') },
                    label = { Text("Сумма, $") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    topUpText.toDoubleOrNull()?.let { repository.addOpenAiTopUp(it); onRefresh() }
                    showTopUpDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showTopUpDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ReferenceServiceCard(
    // STATS_CARDS_BALANCED_V0841
    title: String,
    amount: String,
    logoRes: Int,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        title,
                        fontSize = if (title == "Vercel Hobby") 11.sp else 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = Color(0xFF2B2440)
                    )
                    Text(amount, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF171323), maxLines = 1, softWrap = false)
                }
            }
            Text(
                "За текущий\nмесяц",
                modifier = Modifier.padding(start = 54.dp, top = 1.dp),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = Color(0xFF817A91)
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDetails),
                color = Color(0xFFF4EFFF),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Подробнее →",
                    modifier = Modifier.padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    color = Color(0xFF6839E6),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}


@Composable
private fun CaptureScreen(repository: AppRepository, noteId: String, onBack: () -> Unit, onEditor: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialNote = repository.noteById(noteId)
    if (initialNote == null) {
        LaunchedEffect(noteId) { onBack() }
        return
    }
    var note by remember(noteId) { mutableStateOf(initialNote) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedPageIndex by remember(noteId) { mutableIntStateOf((initialNote.imagePaths.size - 1).coerceAtLeast(0)) }
    var zoomStartIndex by remember { mutableStateOf<Int?>(null) }

    zoomStartIndex?.let { start -> ZoomableImageDialog(note.imagePaths, start) { zoomStartIndex = null } }

    fun refreshNote(selectLast: Boolean = false) {
        note = repository.noteById(noteId) ?: return
        if (note.imagePaths.isEmpty()) selectedPageIndex = 0
        else if (selectLast) selectedPageIndex = note.imagePaths.lastIndex
        else selectedPageIndex = selectedPageIndex.coerceIn(0, note.imagePaths.lastIndex)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile
        if (ok && f != null && f.exists()) {
            repository.addImage(noteId, f.absolutePath)
            refreshNote(selectLast = true)
            info = "Страница ${note.imagePaths.size} сохранена в постоянном архиве"
        } else f?.delete()
        pendingFile = null
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var added = 0
        var failed = 0
        uris.forEach { uri ->
            val target = repository.nextImageFile(noteId)
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Не удалось открыть изображение")
                repository.addImage(noteId, target.absolutePath)
            }.isSuccess
            if (ok) added++ else { failed++; target.delete() }
        }
        refreshNote(selectLast = true)
        info = buildString {
            append("Из галереи добавлено страниц: $added")
            if (failed > 0) append(". Не удалось добавить: $failed")
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(note.subject) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(note.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))), fontWeight = FontWeight.SemiBold)
            Text("Оригиналы сохраняются в приватном постоянном архиве приложения.", fontSize = 13.sp)

            Button(onClick = {
                val f = repository.nextImageFile(noteId)
                pendingFile = f
                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                camera.launch(uri)
            }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Сфотографировать страницу")
            }
            OutlinedButton(onClick = { gallery.launch("image/*") }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Выбрать фотографии из галереи")
            }

            Text("Сохранено страниц: ${note.imagePaths.size}", fontWeight = FontWeight.Bold)
            info?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("Ошибка")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }

            if (note.imagePaths.isNotEmpty()) {
                val pagerState = rememberPagerState(initialPage = selectedPageIndex.coerceIn(0, note.imagePaths.lastIndex)) { note.imagePaths.size }
                LaunchedEffect(pagerState.currentPage) { selectedPageIndex = pagerState.currentPage }
                LaunchedEffect(note.imagePaths.size, selectedPageIndex) {
                    if (note.imagePaths.isNotEmpty()) pagerState.scrollToPage(selectedPageIndex.coerceIn(0, note.imagePaths.lastIndex))
                }
                Card(Modifier.fillMaxWidth().height(390.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxSize().padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                                enabled = pagerState.currentPage > 0
                            ) { Icon(Icons.Default.ChevronLeft, "Предыдущая страница") }
                            Text(
                                "Страница ${pagerState.currentPage + 1}/${note.imagePaths.size}",
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(note.imagePaths.lastIndex)) } },
                                enabled = pagerState.currentPage < note.imagePaths.lastIndex
                            ) { Icon(Icons.Default.ChevronRight, "Следующая страница") }
                        }
                        Spacer(Modifier.height(2.dp))
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                            val path = note.imagePaths[page]
                            val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
                            bitmap?.let {
                                Image(
                                    it.asImageBitmap(),
                                    "Страница ${page + 1}",
                                    Modifier.fillMaxSize().clickable { zoomStartIndex = page },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val from = pagerState.currentPage
                                    if (from > 0) {
                                        repository.moveNotePage(noteId, from, from - 1)
                                        refreshNote()
                                        selectedPageIndex = from - 1
                                    }
                                },
                                enabled = pagerState.currentPage > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text("← Переместить", fontSize = 11.sp, maxLines = 1) }
                            OutlinedButton(
                                onClick = {
                                    val from = pagerState.currentPage
                                    if (from < note.imagePaths.lastIndex) {
                                        repository.moveNotePage(noteId, from, from + 1)
                                        refreshNote()
                                        selectedPageIndex = from + 1
                                    }
                                },
                                enabled = pagerState.currentPage < note.imagePaths.lastIndex,
                                modifier = Modifier.weight(1f)
                            ) { Text("Переместить →", fontSize = 11.sp, maxLines = 1) }
                        }
                    }
                }
                Text("Листай фотографии пальцем влево/вправо. Порядок страниц можно менять кнопками; если черновик уже создан, его блоки перестраиваются в том же порядке.", fontSize = 12.sp)
            }

            if (note.draftText.isBlank()) {
                Button(onClick = {
                    if (repository.backendUrl.isBlank()) {
                        info = "Ошибка: сначала укажи адрес backend в настройках."
                        return@Button
                    }
                    busy = true
                    repository.setStatus(noteId, NoteStatus.UPLOADING)
                    refreshNote()
                    info = "Подготовка фотографий…"
                    scope.launch {
                        val current = repository.noteById(noteId)!!
                        val lesson = current.lessonId?.let { id -> repository.lessons.firstOrNull { it.id == id } }
                        val started = System.nanoTime()
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                AiBackendClient(repository.backendUrl).analyze(current, lesson) { stage ->
                                    scope.launch {
                                        info = stage
                                        if (stage.startsWith("OpenAI")) repository.setStatus(noteId, NoteStatus.PROCESSING)
                                    }
                                }
                            }
                        }
                        repository.recordCloudRequest((System.nanoTime() - started) / 1_000_000_000.0)
                        result.onSuccess { draft ->
                            repository.recordOpenAiOperationUsage(draft.operationId, draft.usage.inputTokens, draft.usage.cachedInputTokens, draft.usage.outputTokens, draft.usage.costUsd)
                            repository.updateDraft(noteId, draft.text, emptyList(), draft.pageLinks, draft.sourceAnchors)
                            refreshNote()
                            info = "Черновик готов. Стоимость анализа: ${formatUsd(draft.usage.costUsd)}"
                        }.onFailure { error ->
                            if (error is AiBackendClient.BillableAnalyzeException) {
                                repository.recordOpenAiOperationUsage(
                                    error.operationId,
                                    error.usage.inputTokens,
                                    error.usage.cachedInputTokens,
                                    error.usage.outputTokens,
                                    error.usage.costUsd
                                )
                            }
                            repository.setStatus(noteId, NoteStatus.WAITING_CONNECTION)
                            refreshNote()
                            info = "Ошибка отправки: ${error.message ?: "нет соединения"}. Конспект остался локально — можно повторить отправку без новой фотографии."
                        }
                        busy = false
                    }
                }, enabled = note.imagePaths.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            busy -> "Выполняется…"
                            note.status == NoteStatus.WAITING_CONNECTION -> "Повторить отправку"
                            else -> "Отправить на анализ"
                        },
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Button(onClick = onEditor, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Открыть черновик") }
                OutlinedButton(onClick = {
                    if (repository.backendUrl.isBlank()) {
                        info = "Ошибка: сначала укажи адрес backend в настройках."
                        return@OutlinedButton
                    }
                    busy = true
                    info = "Повторное распознавание сохранённых страниц…"
                    scope.launch {
                        val current = repository.noteById(noteId)!!
                        val lesson = current.lessonId?.let { id -> repository.lessonByIdAnySubgroup(id) }
                        val started = System.nanoTime()
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                AiBackendClient(repository.backendUrl).analyze(current, lesson) { stage -> scope.launch { info = stage } }
                            }
                        }
                        repository.recordCloudRequest((System.nanoTime() - started) / 1_000_000_000.0)
                        result.onSuccess { draft ->
                            repository.recordOpenAiOperationUsage(draft.operationId, draft.usage.inputTokens, draft.usage.cachedInputTokens, draft.usage.outputTokens, draft.usage.costUsd)
                            repository.updateDraft(noteId, draft.text, emptyList(), draft.pageLinks, draft.sourceAnchors)
                            refreshNote()
                            info = "Черновик перераспознан. Стоимость анализа: ${formatUsd(draft.usage.costUsd)}"
                        }.onFailure { error ->
                            if (error is AiBackendClient.BillableAnalyzeException) {
                                repository.recordOpenAiOperationUsage(
                                    error.operationId,
                                    error.usage.inputTokens,
                                    error.usage.cachedInputTokens,
                                    error.usage.outputTokens,
                                    error.usage.costUsd
                                )
                            }
                            info = "Ошибка повторного распознавания: ${error.message}"
                        }
                        busy = false
                    }
                }, enabled = !busy && note.imagePaths.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Перераспознать страницы")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditorScreen(repository: AppRepository, noteId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialNote = repository.noteById(noteId)
    if (initialNote == null) {
        LaunchedEffect(noteId) { onBack() }
        return
    }
    var note by remember(noteId) { mutableStateOf(initialNote) }
    var field by remember(note.id) {
        mutableStateOf(TextFieldValue(repairPhysicalWordBreaks(note.finalText.ifBlank { note.draftText })) )
    }
    var sourceAnchors by remember(note.id) { mutableStateOf(note.sourceAnchors) }
    var selectedImage by remember { mutableStateOf(note.imagePaths.firstOrNull()) }
    var message by remember { mutableStateOf<String?>(null) }
    var syncingFinal by remember { mutableStateOf(false) }
    var zoomStartIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun imageForCursor(cursor: Int): String? =
        sourceAnchors.firstOrNull { cursor >= it.textStart && cursor < it.textEnd }?.imagePath
            ?: note.pageLinks.firstOrNull { cursor >= it.textStart && cursor <= it.textEnd }?.imagePath
            ?: selectedImage

    val isFinal = note.status == NoteStatus.FINAL || note.status == NoteStatus.FINAL_SYNCED
    zoomStartIndex?.let { start -> ZoomableImageDialog(note.imagePaths, start) { zoomStartIndex = null } }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить конспект?") },
            text = { Text("Будут удалены этот конспект и сохранённые в приложении фотографии его страниц. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    repository.deleteNote(noteId)
                    onBack()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFinal) "Чистовик" else "Черновик") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(note.subject, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                    Text(note.date.toString(), fontSize = 12.sp)
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                }
            }

            val cursor = field.selection.start.coerceIn(0, field.text.length)
            val cursorImagePath = imageForCursor(cursor)
            val cursorPageIndex = cursorImagePath?.let { note.imagePaths.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            val editorPagerState = rememberPagerState(initialPage = cursorPageIndex.coerceIn(0, note.imagePaths.lastIndex.coerceAtLeast(0))) { note.imagePaths.size }

            LaunchedEffect(cursorPageIndex, note.imagePaths.size) {
                if (note.imagePaths.isNotEmpty() && cursorPageIndex in note.imagePaths.indices && editorPagerState.currentPage != cursorPageIndex) {
                    editorPagerState.animateScrollToPage(cursorPageIndex)
                }
            }
            LaunchedEffect(editorPagerState.currentPage) {
                selectedImage = note.imagePaths.getOrNull(editorPagerState.currentPage) ?: selectedImage
            }

            if (note.imagePaths.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().height(238.dp)) {
                    Column(Modifier.fillMaxSize().padding(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Оригинал", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { scope.launch { editorPagerState.animateScrollToPage((editorPagerState.currentPage - 1).coerceAtLeast(0)) } },
                                    enabled = editorPagerState.currentPage > 0
                                ) { Icon(Icons.Default.ChevronLeft, "Предыдущая страница") }
                                Text("${editorPagerState.currentPage + 1}/${note.imagePaths.size}", fontSize = 11.sp)
                                IconButton(
                                    onClick = { scope.launch { editorPagerState.animateScrollToPage((editorPagerState.currentPage + 1).coerceAtMost(note.imagePaths.lastIndex)) } },
                                    enabled = editorPagerState.currentPage < note.imagePaths.lastIndex
                                ) { Icon(Icons.Default.ChevronRight, "Следующая страница") }
                            }
                        }
                        HorizontalPager(state = editorPagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                            val pagePath = note.imagePaths[page]
                            val fullBitmap = remember(pagePath) { BitmapFactory.decodeFile(pagePath) }
                            val isCursorPage = pagePath == cursorImagePath
                            val legacyInkBands = remember(pagePath) { fullBitmap?.let { detectHandwritingBands(it) }.orEmpty() }
                            val shownBitmap = remember(pagePath, cursor, sourceAnchors, note.pageLinks, field.text, legacyInkBands, isCursorPage) {
                                if (isCursorPage) fullBitmap?.let { cropSourcePreview(it, note, sourceAnchors, cursor, pagePath, field.text, legacyInkBands) }
                                else fullBitmap
                            }
                            shownBitmap?.let { bmp ->
                                Image(
                                    bmp.asImageBitmap(),
                                    "Страница ${page + 1}",
                                    Modifier.fillMaxSize().clickable { zoomStartIndex = page },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    alignment = Alignment.Center
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Фото закреплено сверху. Листай страницы прямо здесь влево/вправо или стрелками; курсор автоматически возвращает нужную страницу и показывает соответствующий фрагмент.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = field,
                onValueChange = { nv ->
                    val repaired = repairPhysicalWordBreaks(nv.text)
                    val selection = nv.selection.start.coerceAtMost(repaired.length)
                    val fixedValue = if (repaired == nv.text) nv else TextFieldValue(repaired, androidx.compose.ui.text.TextRange(selection))
                    val newAnchors = adjustSourceAnchors(field.text, fixedValue.text, sourceAnchors)
                    field = fixedValue
                    sourceAnchors = newAnchors
                    selectedImage = imageForCursor(fixedValue.selection.start)
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                visualTransformation = VisualTransformation.None,
                label = { Text("Текст конспекта") }
            )
            message?.let { Text(it, fontSize = 11.sp, maxLines = 2) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    repository.saveEditedDraft(noteId, field.text, emptyList(), sourceAnchors)
                    note = repository.noteById(noteId)!!
                    message = "Черновик сохранён"
                }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Сохранить") }
                Button(onClick = {
                    repository.makeFinal(noteId, field.text)
                    note = repository.noteById(noteId)!!
                    if (repository.backendUrl.isBlank()) {
                        message = "Чистовик сохранён локально. Backend не настроен."
                        return@Button
                    }
                    syncingFinal = true
                    message = "Сохранение чистовика в архиве предмета..."
                    scope.launch {
                        val current = repository.noteById(noteId)!!
                        val lesson = current.lessonId?.let { id -> repository.lessons.firstOrNull { it.id == id } }
                        val started = System.nanoTime()
                        val result = runCatching { withContext(Dispatchers.IO) { AiBackendClient(repository.backendUrl).finalize(current, lesson, field.text) } }
                        repository.recordCloudRequest((System.nanoTime() - started) / 1_000_000_000.0)
                        result.onSuccess { finalResult ->
                            repository.recordOpenAiUsage(finalResult.usage.inputTokens, finalResult.usage.cachedInputTokens, finalResult.usage.outputTokens, finalResult.usage.costUsd)
                            repository.setStatus(noteId, NoteStatus.FINAL_SYNCED)
                            note = repository.noteById(noteId)!!
                            message = "Чистовик сохранён в архиве предмета. Стоимость синхронизации: ${formatUsd(finalResult.usage.costUsd)}"
                        }.onFailure {
                            message = "Чистовик сохранён локально, но не отправлен: ${it.message}"
                        }
                        syncingFinal = false
                    }
                }, enabled = !syncingFinal, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (syncingFinal) "Сохранение..." else "Чистовик") }
            }
            if (isFinal) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val export = buildSubjectExport(repository, note.subject)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Конспекты: ${note.subject}", export))
                        message = "Все чистовики предмета скопированы."
                    }, Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Копировать", fontSize = 11.sp) }
                    OutlinedButton(onClick = {
                        val export = buildSubjectExport(repository, note.subject)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Конспекты — ${note.subject}")
                            putExtra(Intent.EXTRA_TEXT, export)
                        }
                        context.startActivity(Intent.createChooser(send, "Передать конспекты"))
                    }, Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Поделиться", fontSize = 11.sp) }
                }
            }
        }
    }
}

private fun repairPhysicalWordBreaks(text: String): String {
    // 1) Обычный перенос слова через физическую строку: "исследу-\nемого" -> "исследуемого".
    // Нормативные дефисы внутри одной строки (кол-во, физ.-хим., кислотно-основной) не трогаем.
    var out = text.replace(Regex("(?<=\\p{L})[-‐‑]\\h*\\R\\h*(?=\\p{Ll})"), "")

    // 2) Некоторые OCR-ответы теряют сам перевод строки и оставляют артефакт вроде
    // "использ-зована". Удаляем дефис только в очень безопасном случае, когда на стыке
    // продублирована одна и та же строчная буква. Это исправляет OCR-артефакт, не превращая
    // реальные слова типа "кислотно-основной" в одно слово.
    out = out.replace(Regex("(\\p{Ll}+)([\\p{Ll}])[-‐‑]\\2(\\p{Ll}+)") ) { m ->
        m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
    }
    return out
}

private fun cropSourcePreview(
    bitmap: android.graphics.Bitmap,
    note: Note,
    anchors: List<SourceAnchor>,
    cursor: Int,
    imagePath: String,
    currentText: String,
    legacyInkBands: List<IntRange>
): android.graphics.Bitmap {
    val h = bitmap.height
    val w = bitmap.width
    if (h <= 1 || w <= 1) return bitmap

    // Новые OCR-конспекты имеют реальные координаты строки от backend.
    // Берём самый узкий подходящий anchor: так границы соседних строк не смогут
    // перетянуть курсор на предыдущий большой диапазон.
    val anchor = anchors
        .filter { it.imagePath == imagePath && cursor >= it.textStart && cursor < it.textEnd }
        .minByOrNull { (it.textEnd - it.textStart).coerceAtLeast(1) }

    val rawTop: Int
    val rawBottom: Int
    if (anchor != null) {
        // Для новых OCR-конспектов доверяем координатам блока, которые вернул backend.
        // Это устойчивее, чем повторно угадывать номер строки по локальному анализу чернил:
        // один логический фрагмент может занимать несколько физических строк на листе.
        if (anchor.yBottom > anchor.yTop) {
            rawTop = (anchor.yTop.coerceIn(0, 1000) / 1000f * h).toInt()
            rawBottom = (anchor.yBottom.coerceIn(0, 1000) / 1000f * h).toInt()
        } else {
            rawTop = 0
            rawBottom = h
        }
    } else {
        val link = note.pageLinks.firstOrNull {
            it.imagePath == imagePath && cursor >= it.textStart && cursor <= it.textEnd
        } ?: return bitmap

        val pageStart = link.textStart.coerceIn(0, currentText.length)
        val pageEndExclusive = (link.textEnd + 1).coerceIn(pageStart, currentText.length)
        val pageText = currentText.substring(pageStart, pageEndExclusive)
        val localCursor = (cursor - pageStart).coerceIn(0, pageText.length)

        // Находим именно логическую строку, в которой находится курсор.
        // Пустые строки не считаем строками рукописи, но сохраняем их позицию.
        val lineRanges = mutableListOf<IntRange>()
        var lineStart = 0
        for (i in 0..pageText.length) {
            if (i == pageText.length || pageText[i] == '\n') {
                val lineEnd = i
                if (pageText.substring(lineStart, lineEnd).isNotBlank()) {
                    lineRanges += lineStart until lineEnd.coerceAtLeast(lineStart + 1)
                }
                lineStart = i + 1
            }
        }
        val currentLineIndex = lineRanges.indexOfFirst { localCursor >= it.first && localCursor <= it.last + 1 }
            .let { if (it >= 0) it else lineRanges.indexOfLast { r -> r.first <= localCursor }.coerceAtLeast(0) }
        val lineCount = lineRanges.size.coerceAtLeast(1)

        val center = if (legacyInkBands.isNotEmpty()) {
            // Сопоставляем строку печатного текста с реально найденной строкой чернил.
            val targetBandIndex = if (lineCount <= 1) 0 else {
                ((currentLineIndex.toFloat() / (lineCount - 1).toFloat()) * (legacyInkBands.size - 1))
                    .toInt().coerceIn(0, legacyInkBands.lastIndex)
            }
            val band = legacyInkBands[targetBandIndex]
            (band.first + band.last) / 2
        } else {
            // Запасной вариант для очень бледных/чёрно-белых фотографий.
            // Используем номер строки, а не количество символов: переносы и длинные
            // предложения больше не сдвигают фрагмент к неправильному месту.
            val usableTop = (h * 0.07f).toInt()
            val usableBottom = (h * 0.96f).toInt()
            val ratio = if (lineCount <= 1) 0.5f else currentLineIndex.toFloat() / (lineCount - 1).toFloat()
            (usableTop + ratio * (usableBottom - usableTop)).toInt()
        }.coerceIn(0, h - 1)

        val approxHeight = (h * 0.11f).toInt().coerceAtLeast(100).coerceAtMost(h)
        rawTop = (center - approxHeight / 2).coerceAtLeast(0)
        rawBottom = (rawTop + approxHeight).coerceAtMost(h)
    }

    // Показываем заметный контекст вокруг логического фрагмента: так рукопись легче
    // сверять даже когда предложение занимает несколько физических строк.
    val padding = (h * 0.035f).toInt().coerceAtLeast(18)
    var top = (rawTop - padding).coerceIn(0, h - 1)
    var bottom = (rawBottom + padding).coerceIn(top + 1, h)
    val minHeight = (h * 0.18f).toInt().coerceAtLeast(140).coerceAtMost(h)
    if (bottom - top < minHeight) {
        val center = (top + bottom) / 2
        top = (center - minHeight / 2).coerceAtLeast(0)
        bottom = (top + minHeight).coerceAtMost(h)
        top = (bottom - minHeight).coerceAtLeast(0)
    }
    return android.graphics.Bitmap.createBitmap(bitmap, 0, top, w, (bottom - top).coerceAtLeast(1))
}

/**
 * Приблизительно находит горизонтальные полосы рукописного текста.
 * Используется только для старых конспектов, у которых backend ещё не сохранял
 * SourceAnchor. Сетка тетради почти серая и светлая, поэтому считаем в основном
 * тёмные цветные (синие/фиолетовые) пиксели и объединяем соседние строки.
 */
private fun detectHandwritingBands(bitmap: android.graphics.Bitmap): List<IntRange> {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 20 || h < 20) return emptyList()

    val stepX = (w / 420).coerceAtLeast(2)
    val stepY = (h / 700).coerceAtLeast(2)
    val x0 = (w * 0.04f).toInt()
    val x1 = (w * 0.96f).toInt().coerceAtMost(w - 1)
    val rowYs = mutableListOf<Int>()
    val rowScores = mutableListOf<Int>()

    var y = (h * 0.03f).toInt()
    val yEnd = (h * 0.98f).toInt()
    while (y <= yEnd) {
        var ink = 0
        var samples = 0
        var x = x0
        while (x <= x1) {
            val c = bitmap.getPixel(x, y)
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val lum = (r * 30 + g * 59 + b * 11) / 100
            val colorfulDark = lum < 188 && (maxC - minC) >= 18
            val veryDark = lum < 118
            if (colorfulDark || veryDark) ink++
            samples++
            x += stepX
        }
        rowYs += y
        rowScores += if (samples > 0) ink * 1000 / samples else 0
        y += stepY
    }
    if (rowScores.isEmpty()) return emptyList()

    // Небольшое вертикальное сглаживание помогает не дробить одну рукописную строку.
    val smooth = IntArray(rowScores.size)
    for (i in rowScores.indices) {
        var sum = 0
        var n = 0
        for (j in (i - 2).coerceAtLeast(0)..(i + 2).coerceAtMost(rowScores.lastIndex)) {
            sum += rowScores[j]
            n++
        }
        smooth[i] = if (n > 0) sum / n else 0
    }
    val positive = smooth.filter { it > 0 }.sorted()
    if (positive.isEmpty()) return emptyList()
    val median = positive[positive.size / 2]
    val threshold = maxOf(7, median + 5)

    val raw = mutableListOf<IntRange>()
    var startIndex = -1
    for (i in smooth.indices) {
        val active = smooth[i] >= threshold
        if (active && startIndex < 0) startIndex = i
        val last = i == smooth.lastIndex
        if (startIndex >= 0 && (!active || last)) {
            val endIndex = if (active && last) i else i - 1
            val top = rowYs[startIndex]
            val bottom = (rowYs[endIndex] + stepY).coerceAtMost(h - 1)
            if (bottom > top) raw += top..bottom
            startIndex = -1
        }
    }
    if (raw.isEmpty()) return emptyList()

    // Склеиваем части одной строки, но сохраняем реальные промежутки между строками.
    val merged = mutableListOf<IntRange>()
    val mergeGap = (h * 0.006f).toInt().coerceAtLeast(stepY * 2)
    for (band in raw) {
        val last = merged.lastOrNull()
        if (last != null && band.first - last.last <= mergeGap) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, band.last)
        } else {
            merged += band
        }
    }
    return merged.filter { (it.last - it.first) >= stepY }
}

// OWNER_SECRET_V0829
// OWNER_VALIDATION_V0831
private val OWNER_KEY_SHA256_BYTES = byteArrayOf(
    0xdd.toByte(), 0x91.toByte(), 0x7a.toByte(), 0xe3.toByte(),
    0x47.toByte(), 0x52.toByte(), 0x1e.toByte(), 0x90.toByte(),
    0x59.toByte(), 0xc1.toByte(), 0x26.toByte(), 0x8a.toByte(),
    0x3b.toByte(), 0xe9.toByte(), 0x78.toByte(), 0x59.toByte(),
    0x1a.toByte(), 0xa5.toByte(), 0x97.toByte(), 0xd7.toByte(),
    0x3b.toByte(), 0x99.toByte(), 0x7f.toByte(), 0x58.toByte(),
    0x48.toByte(), 0x1d.toByte(), 0xb6.toByte(), 0xdb.toByte(),
    0xaf.toByte(), 0xc9.toByte(), 0x0d.toByte(), 0x8d.toByte()
)
private fun validOwnerKey(value: String): Boolean {
    val actual = MessageDigest.getInstance("SHA-256").digest(value.trim().toByteArray(Charsets.UTF_8))
    return MessageDigest.isEqual(actual, OWNER_KEY_SHA256_BYTES)
}

@Composable
private fun SettingsScreen(repository: AppRepository, onRefresh: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var subgroupSyncing by remember { mutableStateOf(false) }
    var subgroupMessage by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateFound by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateProgress by remember { mutableIntStateOf(-1) }
    var ownerTapCount by remember { mutableIntStateOf(0) }
    val ownerPersistContext = androidx.compose.ui.platform.LocalContext.current
    val ownerPrefs = remember(ownerPersistContext) { ownerPersistContext.getSharedPreferences("studynotes_owner", android.content.Context.MODE_PRIVATE) }
    var ownerTestMode by remember { mutableStateOf(ownerPrefs.getBoolean("unlocked", false)) } // OWNER_CONTROLS_PERSISTENT_V0838
    var showOwnerKeyDialog by remember { mutableStateOf(false) }
    var ownerKeyInput by remember { mutableStateOf("") }
    var ownerKeyError by remember { mutableStateOf<String?>(null) }

    fun refreshCurrentSchedule() {
        if (subgroupSyncing) return
        val subgroup = repository.selectedSubgroup
        subgroupSyncing = true
        subgroupMessage = "Загружаю расписание ${subgroup}-й подгруппы…"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { SfuScheduleClient().fetch(context, repository.selectedGroupName) }
            }
            result.onSuccess {
                repository.replaceSchedule(it.lessons, it.currentWeek, subgroup)
                LessonNotificationWorker.schedule(context, it.lessons)
                subgroupMessage = "Расписание ${subgroup}-й подгруппы обновлено."
            }.onFailure {
                subgroupMessage = "Не удалось обновить расписание: ${it.message ?: "ошибка сети"}. Сохранённая версия оставлена без изменений."
            }
            subgroupSyncing = false
            onRefresh()
        }
    }

    fun switchSubgroup(subgroup: Int) {
        if (subgroupSyncing || subgroup == repository.selectedSubgroup) return
        repository.setSelectedSubgroup(subgroup)
        subgroupSyncing = true
        subgroupMessage = "Загружаю расписание ${subgroup}-й подгруппы…"
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { SfuScheduleClient().fetch(context, repository.selectedGroupName) } }
            result.onSuccess {
                repository.replaceSchedule(it.lessons, it.currentWeek, subgroup)
                LessonNotificationWorker.schedule(context, it.lessons)
                subgroupMessage = "Выбрана ${subgroup}-я подгруппа. Расписание обновлено."
            }.onFailure {
                subgroupMessage = "Подгруппа переключена. Не удалось обновить расписание — используется сохранённая версия."
            }
            subgroupSyncing = false
            onRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White))) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            val themeSettingsContextV0849 = androidx.compose.ui.platform.LocalContext.current
                            Column(modifier=Modifier.fillMaxWidth().padding(bottom=12.dp)) { // FULL_REFERENCE_V0856
                             Text("Внешний вид",fontWeight=FontWeight.Bold,fontSize=18.sp,color=MaterialTheme.colorScheme.onSurface)
                             Text("Тема приложения",modifier=Modifier.padding(top=10.dp,bottom=7.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                             Row(modifier=Modifier.fillMaxWidth().height(74.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
                              listOf("☀\nСветлая","☾\nТёмная","▣\nСистемная").forEachIndexed { i,label ->
                               val active=(i==0&&!appDarkThemeStateV0849.value)||(i==1&&appDarkThemeStateV0849.value)
                               Box(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(if(active) Color(0xFF7540F8) else Color.Transparent).clickable { if(i<2){val mode=i==1;appDarkThemeStateV0849.value=mode;themeSettingsContextV0849.getSharedPreferences("studynotes_theme",android.content.Context.MODE_PRIVATE).edit().putBoolean("dark",mode).apply()} },contentAlignment=Alignment.Center){Text(label,textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=12.sp,color=if(active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)}
                              }
                             }
                            }
Text("Смена подгруппы", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Text(repository.selectedGroupName, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (repository.selectedSubgroup == 1) {
                                Button(onClick = {}, Modifier.weight(1f)) { Text("1 подгруппа") }
                                OutlinedButton(onClick = { switchSubgroup(2) }, Modifier.weight(1f), enabled = !subgroupSyncing) { Text("2 подгруппа") }
                            } else {
                                OutlinedButton(onClick = { switchSubgroup(1) }, Modifier.weight(1f), enabled = !subgroupSyncing) { Text("1 подгруппа") }
                                Button(onClick = {}, Modifier.weight(1f)) { Text("2 подгруппа") }
                            }
                        }
                                                Text("Расписание", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { refreshCurrentSchedule() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !subgroupSyncing
                        ) {
                            Text(if (subgroupSyncing) "Обновление…" else "Обновить расписание")
                        }
subgroupMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            // SETTINGS_RECOVERY_CARDS_V0826


            // UPDATE_ARCHITECTURE_V0829
            item {
                Card(colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White))) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Обновления", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Text(
                            "Текущая версия: ${ru.studynotes.sfu.BuildConfig.VERSION_NAME}",
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                ownerTapCount++
                                if (ownerTapCount >= 7) {
                                    ownerTapCount = 0
                                    ownerKeyInput = ""
                                    ownerKeyError = null
                                    showOwnerKeyDialog = true
                                }
                            }
                        )

                        if (ownerTestMode) {
                            Text("Режим владельца", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (repository.updateChannel == "stable") {
                                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Stable") }
                                    OutlinedButton(
                                        onClick = { repository.setUpdateChannel("test"); updateStatus = "Выбран Test."; onRefresh() },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Test") }
                                } else {
                                    OutlinedButton(
                                        onClick = { repository.setUpdateChannel("stable"); updateStatus = "Выбран Stable."; onRefresh() },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Stable") }
                                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Test") }
                                }
                            }
                        } else {
                            Text("Канал: ${if (repository.updateChannel == "test") "Test" else "Stable"}", fontSize = 12.sp, color = Color(0xFF6E667B))
                        }

                        Button(
                            onClick = {
                                updateChecking = true
                                updateStatus = null
                                updateFound = null
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) { AppUpdateManager.check(repository.updateChannel) }
                                    }
                                    updateFound = result.getOrNull()
                                    updateStatus = when {
                                        result.isFailure -> "Не удалось проверить обновления: ${result.exceptionOrNull()?.message}"
                                        updateFound == null -> "Установлена актуальная версия."
                                        else -> "Доступна версия ${updateFound!!.versionName}."
                                    }
                                    updateChecking = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !updateChecking
                        ) { Text(if (updateChecking && updateFound == null) "Проверка…" else "Проверить обновления") }

                        updateFound?.let { update ->
                            Button(
                                onClick = {
                                    if (!AppUpdateManager.canInstallPackages(context)) {
                                        updateStatus = "Разреши установку приложений для StudyNotesSFU и вернись сюда."
                                        AppUpdateManager.openInstallPermission(context)
                                    } else {
                                        updateChecking = true
                                        updateProgress = 0
                                        scope.launch {
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    AppUpdateManager.downloadAndStartInstall(context, update) { progress ->
                                                        scope.launch { updateProgress = progress }
                                                    }
                                                }
                                            }
                                            updateStatus = result.exceptionOrNull()?.let { "Ошибка обновления: ${it.message}" }
                                            updateChecking = false
                                            if (result.isFailure) updateProgress = -1
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !updateChecking
                            ) {
                                if (updateChecking && updateProgress >= 0) {
                                    CircularProgressIndicator(
                                        progress = updateProgress.coerceIn(0, 100) / 100f,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("$updateProgress% · осталось ${100 - updateProgress.coerceIn(0, 100)}%")
                                } else {
                                    Text("Обновить до ${update.versionName}")
                                }
                            }
                        }
                        updateStatus?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = (if (appDarkThemeStateV0849.value) MaterialTheme.colorScheme.surface else Color.White))) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SettingsEthernet, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Подключение", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Text("Backend: ${repository.backendUrl}", fontSize = 12.sp, color = Color(0xFF6E667B))

                        val backendDeviceKeyId = remember {
                            ru.studynotes.sfu.security.DeviceSigningKey.keyId()
                        }
                        var backendKeyCopyMessage by remember { mutableStateOf<String?>(null) }

                        Text(
                            "ID устройства: $backendDeviceKeyId",
                            fontSize = 12.sp,
                            color = Color(0xFF6E667B)
                        )

                        OutlinedButton(
                            onClick = {
                                val publicKey =
                                    ru.studynotes.sfu.security.DeviceSigningKey.publicKeyBase64()

                                val clipboard = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager

                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "StudyNotesSFU public key",
                                        publicKey
                                    )
                                )

                                backendKeyCopyMessage = "Публичный ключ скопирован."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скопировать публичный ключ")
                        }

                        backendKeyCopyMessage?.let {
                            Text(
                                it,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                testing = true
                                message = null
                                scope.launch {
                                    val started = System.nanoTime()
                                    val result = runCatching { withContext(Dispatchers.IO) { AiBackendClient(repository.backendUrl).health() } }
                                    repository.recordCloudRequest((System.nanoTime() - started) / 1_000_000_000.0)
                                    message = result.fold({ "Соединение работает." }, { "Не удалось подключиться: ${it.message}" })
                                    testing = false
                                    onRefresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !testing
                        ) { Text(if (testing) "Проверка…" else "Проверить подключение") }
                        message?.let {
                            Text(it, fontSize = 12.sp, color = if (it.startsWith("Соединение")) Color(0xFF168B63) else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // OWNER_KEY_DIALOG_V0829
    if (showOwnerKeyDialog) {
        AlertDialog(
            onDismissRequest = { showOwnerKeyDialog = false },
            title = { Text("Режим владельца") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введи секретный ключ владельца.")
                    OutlinedTextField(
                        value = ownerKeyInput,
                        onValueChange = { ownerKeyInput = it; ownerKeyError = null },
                        label = { Text("Ключ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ownerKeyError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (validOwnerKey(ownerKeyInput)) {
                        ownerTestMode = true
                        ownerPrefs.edit().putBoolean("unlocked", true).apply()
                        repository.setUpdateChannel("test")
                        updateStatus = "Режим владельца активирован. Выбран Test."
                        ownerKeyInput = ""
                        ownerKeyError = null
                        showOwnerKeyDialog = false
                        onRefresh()
                    } else {
                        ownerKeyError = "Неверный ключ."
                    }
                }) { Text("Активировать") }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerKeyDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ZoomableImageDialog(imagePaths: List<String>, initialIndex: Int, onDismiss: () -> Unit) {
    if (imagePaths.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, imagePaths.lastIndex)) { imagePaths.size }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                            enabled = pagerState.currentPage > 0
                        ) { Icon(Icons.Default.ChevronLeft, "Предыдущая страница") }
                        Text("Страница ${pagerState.currentPage + 1}/${imagePaths.size}", fontWeight = FontWeight.SemiBold)
                        IconButton(
                            onClick = { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(imagePaths.lastIndex)) } },
                            enabled = pagerState.currentPage < imagePaths.lastIndex
                        ) { Icon(Icons.Default.ChevronRight, "Следующая страница") }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                    val path = imagePaths[page]
                    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
                    var scale by remember(path) { mutableFloatStateOf(1f) }
                    var offsetX by remember(path) { mutableFloatStateOf(0f) }
                    var offsetY by remember(path) { mutableFloatStateOf(0f) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        bitmap?.let {
                            Image(
                                it.asImageBitmap(),
                                "Страница ${page + 1}",
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (scale > 1.01f) Modifier.pointerInput(path, scale) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                scale = (scale * zoom).coerceIn(1f, 6f)
                                                offsetX += pan.x
                                                offsetY += pan.y
                                                if (scale <= 1.01f) { offsetX = 0f; offsetY = 0f }
                                            }
                                        } else Modifier
                                    )
                                    .graphicsLayer {
                                        scaleX = scale; scaleY = scale
                                        translationX = offsetX; translationY = offsetY
                                    },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(onClick = {
                                scale = (scale - 0.5f).coerceAtLeast(1f)
                                if (scale <= 1.01f) { offsetX = 0f; offsetY = 0f }
                            }) { Icon(Icons.Default.Remove, "Уменьшить") }
                            FilledTonalIconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(6f) }) { Icon(Icons.Default.Add, "Увеличить") }
                        }
                    }
                }
                Text("Листай страницы влево/вправо. Для увеличения используй +/−; при увеличении фото можно перемещать.", Modifier.padding(10.dp), fontSize = 11.sp)
            }
        }
    }
}

private fun buildSubjectExport(repository: AppRepository, subject: String): String {
    val notes = repository.notes
        .filter { it.subject.equals(subject, ignoreCase = true) && it.finalText.isNotBlank() }
        .sortedBy { it.date }
    return buildString {
        appendLine("ПРЕДМЕТ: $subject")
        appendLine("Источник: подтверждённые чистовики приложения StudyNotesSFU")
        appendLine()
        if (notes.isEmpty()) {
            appendLine("Подтверждённых чистовиков пока нет.")
        } else {
            notes.forEachIndexed { index, n ->
                appendLine("=== КОНСПЕКТ ${index + 1} — ${n.date} ===")
                appendLine(n.finalText.trim())
                appendLine()
            }
        }
        appendLine("---")
        appendLine("При разборе опирайся прежде всего на эти конспекты. Если заметишь возможную ошибку или пробел, отдели это от того, что прямо записано в источнике.")
    }
}

private fun formatUsd(value: Double): String = String.format(Locale.US, "$%.4f", value)
private fun formatUsd8(value: Double): String = String.format(Locale.US, "$%.8f", value)
private fun formatTokenCount(value: Long): String = String.format(Locale.US, "%,d", value).replace(',', ' ')

private fun weekLabel(w: WeekKind) = when (w) {
    WeekKind.ODD -> "нечётная"
    WeekKind.EVEN -> "чётная"
    WeekKind.UNKNOWN -> "не определена"
}

private fun statusLabel(s: NoteStatus) = when (s) {
    NoteStatus.LOCAL -> "сохранён локально"
    NoteStatus.WAITING_CONNECTION -> "ожидает соединения"
    NoteStatus.UPLOADING -> "отправляется"
    NoteStatus.PROCESSING -> "обрабатывается"
    NoteStatus.DRAFT_READY -> "черновик готов"
    NoteStatus.FINAL -> "чистовик (локально)"
    NoteStatus.FINAL_SYNCED -> "чистовик синхронизирован"
}

// DARK_SURFACE_CALENDAR_V0851: surfaces=14, calendarBorders=1

@Composable
private fun ReferencePairDataV0856(title: String, time: String, room: String, group: String, format: String, teacher: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF071522)).padding(20.dp)) {
        Text("‹   Пара", color=Color(0xFFF7F8FC), fontSize=20.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(20.dp)); Text(title,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text("лекция",Modifier.padding(vertical=10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp)).background(Color(0xFFB69CFF)).padding(horizontal=10.dp,vertical=5.dp),color=Color(0xFF5125C8))
        listOf("◷  $time","●  $room","♟  Поток: $group","▣  Формат: $format","♟  Преподаватель\\n    $teacher").forEach { Text(it,Modifier.padding(vertical=8.dp),color=Color(0xFFF7F8FC),fontSize=16.sp) }
        Card(Modifier.fillMaxWidth().padding(top=14.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0D2031))) { Column(Modifier.padding(14.dp)){ Text("Заметки",color=Color.White,fontWeight=FontWeight.Bold); Text("Добавить заметку...",Modifier.fillMaxWidth().padding(vertical=18.dp),color=Color(0xFF9AAAC3)); Button({},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7540F8))){Text("Сохранить")} } }
    }
}

@Composable
private fun ReferenceStatisticsV0856() {
    Column(Modifier.fillMaxWidth().background(Color(0xFF071522)).padding(18.dp)) {
        Text("‹   Статистика",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical=14.dp)){ listOf("Неделя","Месяц","Семестр").forEachIndexed{i,s-> Box(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(if(i==0)Color(0xFF7540F8) else Color(0xFF12283D)).padding(10.dp),contentAlignment=Alignment.Center){Text(s,color=Color.White,fontSize=12.sp)} } }
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF0D2031))){Column(Modifier.fillMaxWidth().padding(12.dp)){Text("Посещаемость",color=Color.White,fontWeight=FontWeight.Bold);Text("85%",Modifier.padding(vertical=22.dp),color=Color.White,fontSize=34.sp,fontWeight=FontWeight.Bold);Text("● Посещено   34     ● Пропущено   6     ● Всего   40",color=Color(0xFF9AAAC3),fontSize=12.sp)}}
        Spacer(Modifier.height(12.dp));Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF0D2031))){Column(Modifier.fillMaxWidth().padding(12.dp)){Text("Распределение по предметам",color=Color.White,fontWeight=FontWeight.Bold);listOf("Аналитическая химия" to .9f,"Программирование" to .8f,"Экология" to .75f,"Высшая математика" to .6f,"Физика" to .5f).forEach{(n,v)->Text(n,Modifier.padding(top=10.dp),color=Color.White,fontSize=12.sp);LinearProgressIndicator(progress=v,modifier=Modifier.fillMaxWidth(),color=Color(0xFF7540F8),trackColor=Color(0xFF12283D))}}}
    }
}
// FULL_REFERENCE_SCREEN_HELPERS_V0856

// REFERENCE_UI_V2_V0857 — single authoritative visual specification
private val RefCanvasV2 = Color(0xFF071522)
private val RefCardV2 = Color(0xFF0D2031)
private val RefRaisedV2 = Color(0xFF12283D)
private val RefPurpleV2 = Color(0xFF7540F8)
private val RefTextV2 = Color(0xFFF7F8FC)
private val RefMutedV2 = Color(0xFF9AAAC3)
private val RefRadiusV2 = 10.dp
private val RefGapV2 = 12.dp

@Composable
private fun RefSectionV2(title:String, content:@Composable ColumnScope.()->Unit){
 Card(modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(RefRadiusV2),colors=CardDefaults.cardColors(containerColor=RefCardV2)){Column(Modifier.fillMaxWidth().padding(14.dp)){Text(title,color=RefTextV2,fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(10.dp));content()}}
}

@Composable
private fun RefStatRowV2(name:String,value:Float,label:String){
 Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name,color=RefTextV2,fontSize=12.sp);Text(label,color=RefTextV2,fontSize=12.sp)};LinearProgressIndicator(progress=value,modifier=Modifier.fillMaxWidth().height(7.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),color=RefPurpleV2,trackColor=RefRaisedV2)}
}

