package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.LanguageUnit
import com.example.data.QuizScore
import com.example.data.SpeakingPracticeLog
import com.example.ui.LanguageLearningViewModel
import com.example.ui.QuizQuestion
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: LanguageLearningViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                MainAppScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppScreen(viewModel: LanguageLearningViewModel = viewModel()) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val wordsList by viewModel.allUnits.collectAsState()
    
    // UI Local tab selection: "lessons", "vocabulary", "quiz", "dictionary"
    var activeTab by remember { mutableStateOf("lessons") }
    
    // State to hold currently active term for custom speech recognition flow
    var activeSpeakingPracticeUnit by remember { mutableStateOf<LanguageUnit?>(null) }

    // Screen width detection for responsive layouts (600dp boundary)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTodayDateAndResetIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val setupCompleted by viewModel.setupCompleted.collectAsState()

        if (!setupCompleted) {
            LanguageSetupScreen(viewModel = viewModel)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isTablet) {
                    // Tablet layout: Side Navigation Rail + Detail Splittings
                    Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Language App Mascot",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        
                        NavigationRailItem(
                            selected = activeTab == "lessons",
                            onClick = { activeTab = "lessons" },
                            icon = { Icon(Icons.Default.School, "Daily Lessons") },
                            label = { Text("Lessons") }
                        )
                        NavigationRailItem(
                            selected = activeTab == "vocabulary",
                            onClick = { activeTab = "vocabulary" },
                            icon = { Icon(Icons.Default.Style, "Flashcards") },
                            label = { Text("Flashcards") }
                        )
                        NavigationRailItem(
                            selected = activeTab == "quiz",
                            onClick = { activeTab = "quiz" },
                            icon = { Icon(Icons.Default.Quiz, "Quiz Arena") },
                            label = { Text("Quiz") }
                        )
                        NavigationRailItem(
                            selected = activeTab == "dictionary",
                            onClick = { activeTab = "dictionary" },
                            icon = { Icon(Icons.Default.MenuBook, "Dictionary") },
                            label = { Text("Dictionary") }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    VerticalDivider()

                    // Main tablet page content container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TabletPageContent(
                            activeTab = activeTab,
                            viewModel = viewModel,
                            selectedLanguage = selectedLanguage,
                            selectedCategory = selectedCategory,
                            wordsList = wordsList,
                            onTabChange = { activeTab = it },
                            onPracticeSpeaking = { activeSpeakingPracticeUnit = it }
                        )
                    }
                }
            } else {
                // Mobile layout: Standard bottom bar navigation with safe status insets
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationBarItem(
                                selected = activeTab == "lessons",
                                onClick = { activeTab = "lessons" },
                                icon = { Icon(Icons.Default.School, "Daily Lessons") },
                                label = { Text("Lessons") },
                                modifier = Modifier.testTag("nav_lessons")
                            )
                            NavigationBarItem(
                                selected = activeTab == "vocabulary",
                                onClick = { activeTab = "vocabulary" },
                                icon = { Icon(Icons.Default.Style, "Flashcards") },
                                label = { Text("Cards") },
                                modifier = Modifier.testTag("nav_cards")
                            )
                            NavigationBarItem(
                                selected = activeTab == "quiz",
                                onClick = { activeTab = "quiz" },
                                icon = { Icon(Icons.Default.Quiz, "Quiz Arena") },
                                label = { Text("Quiz") },
                                modifier = Modifier.testTag("nav_quiz")
                            )
                            NavigationBarItem(
                                selected = activeTab == "dictionary",
                                onClick = { activeTab = "dictionary" },
                                icon = { Icon(Icons.Default.MenuBook, "Words") },
                                label = { Text("Words") },
                                modifier = Modifier.testTag("nav_words")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MobilePageContent(
                            activeTab = activeTab,
                            viewModel = viewModel,
                            selectedLanguage = selectedLanguage,
                            selectedCategory = selectedCategory,
                            wordsList = wordsList,
                            onTabChange = { activeTab = it },
                            onPracticeSpeaking = { activeSpeakingPracticeUnit = it }
                        )
                    }
                }
            }

            // Practice Speaking Pronunciation Modal Sheet overlay
            if (activeSpeakingPracticeUnit != null) {
                PracticeSpeakingDialog(
                    unit = activeSpeakingPracticeUnit!!,
                    viewModel = viewModel,
                    onDismiss = { activeSpeakingPracticeUnit = null }
                )
            }
        }
    }
}
}

@Composable
fun TabletPageContent(
    activeTab: String,
    viewModel: LanguageLearningViewModel,
    selectedLanguage: String,
    selectedCategory: String,
    wordsList: List<LanguageUnit>,
    onTabChange: (String) -> Unit,
    onPracticeSpeaking: (LanguageUnit) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            MobilePageContent(
                activeTab = activeTab,
                viewModel = viewModel,
                selectedLanguage = selectedLanguage,
                selectedCategory = selectedCategory,
                wordsList = wordsList,
                onTabChange = onTabChange,
                onPracticeSpeaking = onPracticeSpeaking
            )
        }
    }
}

@Composable
fun MobilePageContent(
    activeTab: String,
    viewModel: LanguageLearningViewModel,
    selectedLanguage: String,
    selectedCategory: String,
    wordsList: List<LanguageUnit>,
    onTabChange: (String) -> Unit,
    onPracticeSpeaking: (LanguageUnit) -> Unit
) {
    val quizActive by viewModel.quizActive.collectAsState()

    if (activeTab == "quiz" && quizActive) {
        QuizScreen(viewModel = viewModel)
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Branding & Theme Toggle Header
            val themeModeState by viewModel.themeMode.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🦉",
                        fontSize = 28.sp,
                        modifier = Modifier.testTag("app_mascot_owl")
                    )
                    Column {
                        Text(
                            text = "Mr. Owl",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val langFlag = when (selectedLanguage.lowercase()) {
                            "spanish" -> "🇪🇸"
                            "french" -> "🇫🇷"
                            "japanese" -> "🇯🇵"
                            "german" -> "🇩🇪"
                            "italian" -> "🇮🇹"
                            "portuguese" -> "🇵🇹"
                            "chinese" -> "🇨🇳"
                            "korean" -> "🇰🇷"
                            "russian" -> "🇷🇺"
                            "arabic" -> "🇸🇦"
                            "hindi" -> "🇮🇳"
                            "swedish" -> "🇸🇪"
                            else -> "🌐"
                        }
                        Text(
                            text = "Practicing: $selectedLanguage $langFlag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val newMode = if (themeModeState == "dark") "light" else "dark"
                        viewModel.updateThemeMode(newMode)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .testTag("theme_quick_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (themeModeState == "dark") Icons.Default.WbSunny else Icons.Default.NightsStay,
                        contentDescription = "Switch Theme Mode",
                        tint = if (themeModeState == "dark") Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }


            Crossfade(
                targetState = activeTab,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "TabSwitcher"
            ) { tab ->
                when (tab) {
                    "lessons" -> LessonsTabScreen(viewModel = viewModel, wordsList = wordsList, onTabChange = onTabChange, onPracticeSpeaking = onPracticeSpeaking)
                    "vocabulary" -> FlashcardsTabScreen(viewModel = viewModel, wordsList = wordsList, onPracticeSpeaking = onPracticeSpeaking)
                    "quiz" -> QuizScoreboardTabScreen(viewModel = viewModel)
                    "dictionary" -> DictionaryTabScreen(viewModel = viewModel, wordsList = wordsList, onPracticeSpeaking = onPracticeSpeaking)
                }
            }
        }
    }
}

@Composable
fun LanguageHeaderBanner(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I WANT TO LEARN:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            // Flag buttons selector row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                val languages = listOf(
                    Triple("Spanish", "🇪🇸", "Spain"),
                    Triple("French", "🇫🇷", "French"),
                    Triple("Japanese", "🇯🇵", "Japan"),
                    Triple("German", "🇩🇪", "Germany"),
                    Triple("Italian", "🇮🇹", "Italy"),
                    Triple("Portuguese", "🇵🇹", "Portugal"),
                    Triple("Chinese", "🇨🇳", "China"),
                    Triple("Korean", "🇰🇷", "Korea"),
                    Triple("Russian", "🇷🇺", "Russia"),
                    Triple("Arabic", "🇸🇦", "Saudi Arabia"),
                    Triple("Hindi", "🇮🇳", "India"),
                    Triple("Swedish", "🇸🇪", "Sweden")
                )

                items(languages) { (lang, flag, label) ->
                    val isSelected = selectedLanguage.equals(lang, ignoreCase = true)
                    
                    Button(
                        onClick = { onLanguageChange(lang) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .testTag("lang_${lang.lowercase()}")
                            .height(52.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = flag, fontSize = 22.sp)
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PronunciationStreakProgressCard(
    viewModel: LanguageLearningViewModel,
    userStreak: Int,
    userPoints: Int
) {
    val speakingLogs by viewModel.speakingLogs.collectAsState(initial = emptyList())
    val quizScores by viewModel.quizScores.collectAsState(initial = emptyList())
    val activeDates by viewModel.activeDates.collectAsState(initial = emptySet())
    val practicedWordIdsToday by viewModel.practicedWordIdsToday.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var showInspectorDialog by remember { mutableStateOf(false) }

    // Calculate total speaking practices, average accuracy, and total speaking XP
    val totalPractices = speakingLogs.size
    val averageAccuracy = if (speakingLogs.isEmpty()) 0 else {
        speakingLogs.map { it.accuracy }.average().toInt()
    }
    val speakingPoints = speakingLogs.sumOf { it.pointsEarned }

    // Generate 7 days of the current week (Sunday to Saturday) in real-time
    val calendar = java.util.Calendar.getInstance()
    val todayDateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
    
    // Set calendar to Sunday of the current week
    calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val dayLabelFormat = java.text.SimpleDateFormat("E", java.util.Locale.US) // "Sun", "Mon", etc.

    val weekDays = (0..6).map { i ->
        val dayCal = java.util.Calendar.getInstance()
        dayCal.time = calendar.time
        dayCal.add(java.util.Calendar.DATE, i)
        val dateStr = dateFormat.format(dayCal.time)
        val label = dayLabelFormat.format(dayCal.time).take(1) // "S", "M", "T"
        
        // A day is completed if there's a speaking log, a quiz score, any recorded active practice date, or active words practiced today
        val hasPractice = activeDates.contains(dateStr) || 
                          (dateStr == todayDateString && practicedWordIdsToday.isNotEmpty()) ||
                          speakingLogs.any { dateFormat.format(java.util.Date(it.timestamp)) == dateStr } ||
                          quizScores.any { dateFormat.format(java.util.Date(it.timestamp)) == dateStr }
        val isToday = dateStr == todayDateString
        
        Triple(label, hasPractice, isToday)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("pronunciation_streak_progress_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥",
                        fontSize = 24.sp,
                        modifier = Modifier.animateContentSize()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRONUNCIATION PROGRESS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.1.sp
                    )
                }
                
                // Badge for quick level
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Level ${1 + (speakingPoints / 50)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats grid layout (Duolingo-inspired cards inside)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Streak Card (Left)
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), // soft warm orange background
                    border = BorderStroke(2.dp, Color(0xFFFFB74D)), // bright orange border
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔥", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (userStreak > 0) "$userStreak DAY STREAK" else "0 DAYS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFE65100),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Speaking XP Card (Right)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), // soft green
                    border = BorderStroke(2.dp, Color(0xFF81C784)), // green border
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🗣️", fontSize = 26.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$speakingPoints SPEAK XP",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2E7D32),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Extra metrics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Avg Accuracy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$averageAccuracy%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Exercises Completed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$totalPractices",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7-day Weekly Track Visualizer
            Text(
                text = "WEEKLY PRACTICE TRACK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEach { (label, completed, isToday) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val circleBg = when {
                            completed -> Color(0xFF4CAF50) // completed green
                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                        val borderStroke = when {
                            completed -> null
                            isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(circleBg)
                                .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(17.dp)) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Interactive expand button for logs history
            if (speakingLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide Practice History" else "View Practice History Logs (${speakingLogs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speakingLogs.take(5).forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.original,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${log.language} • ${log.translation}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (log.accuracy >= 75) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${log.accuracy}% Match",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (log.accuracy >= 75) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+${log.pointsEarned} XP",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- STREAK DATE SYSTEM DIAGNOSTICS & MILESTONES ---
            var showDiagnostics by remember { mutableStateOf(false) }
            val lastActiveDateStr by viewModel.lastActiveDate.collectAsState()

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDiagnostics = !showDiagnostics }
                    .testTag("streak_diagnostics_toggle")
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showDiagnostics) "Hide Streak Calculations" else "Show Live Streak Calculations 🔍",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = showDiagnostics) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("streak_diagnostics_panel")
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val calYesterday = java.util.Calendar.getInstance()
                    calYesterday.add(java.util.Calendar.DATE, -1)
                    val yesterdayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calYesterday.time)

                    // Diagnostic Details Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
                            .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "STREAK SYSTEM PARAMETERS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.1.sp
                            )
                            
                            // 1. Current Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🗓️ Current System Date (Today)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = todayDateString,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 2. Yesterday Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "↩️ System Date (Yesterday)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = yesterdayStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 3. Last Active Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "✨ Last Recorded Practice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (lastActiveDateStr.isBlank()) "None recorded yet" else lastActiveDateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 4. Analysis and live logic calculation description
                            val (analysisText, analysisColor) = when {
                                lastActiveDateStr == todayDateString -> {
                                    Pair(
                                        "Streak is fully updated and locked for today! You have secured your $userStreak-day streak. Great job! 🥳",
                                        Color(0xFF2E7D32)
                                    )
                                }
                                lastActiveDateStr == yesterdayStr -> {
                                    Pair(
                                        "Yesterday was active! Your streak is safely waiting. Your next practice will instantly increment it to ${userStreak + 1} days! 🚀",
                                        Color(0xFFE65100)
                                    )
                                }
                                lastActiveDateStr.isBlank() -> {
                                    Pair(
                                        "Welcome! This is your initial session. Practice now to establish your dynamic streak calculation starting at ${userStreak} days! 🌱",
                                        MaterialTheme.colorScheme.primary
                                    )
                                }
                                else -> {
                                    Pair(
                                        "Streak is currently inactive (last activity on $lastActiveDateStr). Your next practice session will initiate a fresh 1-day streak! ⚡",
                                        Color(0xFFC62828)
                                    )
                                }
                            }

                            Text(
                                text = "Live Calculation Analysis:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = analysisText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = analysisColor,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Milestone Celebrations List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "STREAK MILESTONES & ACHIEVEMENTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )

                        val milestones = listOf(
                            Triple("3-Day Starter", 3, "🌱"),
                            Triple("7-Day Learner", 7, "🔥"),
                            Triple("14-Day Champion", 14, "👑"),
                            Triple("30-Day Master", 30, "✨")
                        )

                        milestones.forEach { (title, days, emoji) ->
                            val progress = if (userStreak >= days) 1f else userStreak.toFloat() / days.toFloat()
                            val isReached = userStreak >= days

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isReached) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    }
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isReached) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = emoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isReached) "COMPLETED" else "$userStreak / $days days",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (isReached) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showInspectorDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_storage_inspector_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Database Inspector",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Database & Storage Inspector ⚙️",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    if (showInspectorDialog) {
        DatabaseStorageInspectorDialog(
            viewModel = viewModel,
            onDismiss = { showInspectorDialog = false }
        )
    }
}

@Composable
fun DatabaseStorageInspectorDialog(
    viewModel: LanguageLearningViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // States from ViewModel
    val userNameState by viewModel.userName.collectAsState()
    val userPointsState by viewModel.userPoints.collectAsState()
    val userStreakState by viewModel.userStreak.collectAsState()
    val lastActiveDateState by viewModel.lastActiveDate.collectAsState()
    val speechAttemptsState by viewModel.speechAttempts.collectAsState()
    val setupCompletedState by viewModel.setupCompleted.collectAsState()
    val activeDatesState by viewModel.activeDates.collectAsState()
    val dailyGoalTargetState by viewModel.dailyGoalTarget.collectAsState()
    
    // DB state collections
    val quizScoresState by viewModel.quizScores.collectAsState(initial = emptyList())
    val speakingLogsState by viewModel.speakingLogs.collectAsState(initial = emptyList())
    val allUnitsState by viewModel.allUnits.collectAsState()
    
    var selectedTab by remember { mutableStateOf("prefs") } // "prefs", "db", "danger"
    
    // Temporary edit states
    var tempName by remember { mutableStateOf(userNameState) }
    var tempPointsStr by remember { mutableStateOf(userPointsState.toString()) }
    var tempStreakStr by remember { mutableStateOf(userStreakState.toString()) }
    var tempAttemptsStr by remember { mutableStateOf(speechAttemptsState.toString()) }
    var tempDateStr by remember { mutableStateOf(lastActiveDateState) }

    // Sync temp states if they change in viewModel
    LaunchedEffect(userNameState, userPointsState, userStreakState, lastActiveDateState, speechAttemptsState) {
        tempName = userNameState
        tempPointsStr = userPointsState.toString()
        tempStreakStr = userStreakState.toString()
        tempAttemptsStr = speechAttemptsState.toString()
        tempDateStr = lastActiveDateState
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.90f)
            .testTag("storage_inspector_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Storage & DB Inspector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Offline SQLite & SharedPreferences Explorer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_inspector_btn")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tab Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "prefs" to "📝 SharedPreferences",
                        "db" to "🗄️ Room SQLite Tables",
                        "danger" to "⚠️ Danger Zone"
                    ).forEach { (tabId, label) ->
                        val isSelected = selectedTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .clickable { selectedTab = tabId }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        "prefs" -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Text(
                                        text = "LIVE KEY-VALUE STORAGE (SharedPreferences)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }

                                // 1. User Name Input
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("key: user_name (String)", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = tempName,
                                                    onValueChange = { tempName = it },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Button(
                                                    onClick = { viewModel.debugUpdateUserName(tempName) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(48.dp)
                                                ) {
                                                    Text("Save", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Points (XP)
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("key: points (Int)", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    IconButton(onClick = {
                                                        val p = (userPointsState - 50).coerceAtLeast(0)
                                                        viewModel.debugUpdatePoints(p)
                                                    }) {
                                                        Text("-50", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                    Text(
                                                        text = "$userPointsState XP",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    IconButton(onClick = {
                                                        val p = userPointsState + 50
                                                        viewModel.debugUpdatePoints(p)
                                                    }) {
                                                        Text("+50", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedTextField(
                                                        value = tempPointsStr,
                                                        onValueChange = { tempPointsStr = it },
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.width(80.dp).height(48.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    Button(
                                                        onClick = {
                                                            tempPointsStr.toIntOrNull()?.let { viewModel.debugUpdatePoints(it) }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.height(48.dp)
                                                    ) {
                                                        Text("Set", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Streak Count (Days)
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("key: user_streak (Int)", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    IconButton(onClick = {
                                                        val s = (userStreakState - 1).coerceAtLeast(0)
                                                        viewModel.debugUpdateStreak(s)
                                                    }) {
                                                        Text("-1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                    Text(
                                                        text = "$userStreakState Days",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    IconButton(onClick = {
                                                        val s = userStreakState + 1
                                                        viewModel.debugUpdateStreak(s)
                                                    }) {
                                                        Text("+1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedTextField(
                                                        value = tempStreakStr,
                                                        onValueChange = { tempStreakStr = it },
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.width(80.dp).height(48.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    Button(
                                                        onClick = {
                                                            tempStreakStr.toIntOrNull()?.let { viewModel.debugUpdateStreak(it) }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.height(48.dp)
                                                    ) {
                                                        Text("Set", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Last Active Date
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("key: last_active_date (String)", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = tempDateStr,
                                                    onValueChange = { tempDateStr = it },
                                                    singleLine = true,
                                                    placeholder = { Text("yyyy-MM-dd") },
                                                    textStyle = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Button(
                                                    onClick = { viewModel.debugUpdateLastActiveDate(tempDateStr) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(48.dp)
                                                ) {
                                                    Text("Set", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }

                                            // Date Presets Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Calendar.getInstance().time)
                                                val calY = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -1) }
                                                val yesterdayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calY.time)
                                                val calB = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -2) }
                                                val twoDaysAgoStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calB.time)

                                                listOf(
                                                    "Today" to todayStr,
                                                    "Yesterday" to yesterdayStr,
                                                    "2 Days Ago" to twoDaysAgoStr
                                                ).forEach { (label, dateVal) ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                                            .clickable {
                                                                tempDateStr = dateVal
                                                                viewModel.debugUpdateLastActiveDate(dateVal)
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 5. Setup Onboarding Toggle
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("key: setup_completed (Boolean)", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                                Text(
                                                    text = "Current State: ${if (setupCompletedState) "COMPLETED ✅" else "ONBOARDING STEP 🛑"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.debugResetSetupCompleted() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Rerun Setup", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                // 6. Completed Activity Dates List (Grid checklist!)
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("key: completed_activity_dates (Set<String>)", style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                                            Text(
                                                text = "Select active days to mock calendar records:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Render last 7 days checklist
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                for (i in 0..6) {
                                                    val checkCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -i) }
                                                    val checkDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(checkCal.time)
                                                    val isChecked = activeDatesState.contains(checkDateStr)
                                                    val dayLabel = when (i) {
                                                        0 -> "Today"
                                                        1 -> "Yesterday"
                                                        else -> java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(checkCal.time)
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { viewModel.debugToggleActivityDate(checkDateStr) }
                                                            .background(
                                                                if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                                else Color.Transparent
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = if (isChecked) "✅ " else "⬜ ",
                                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                            Text("$dayLabel ($checkDateStr)", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        Text(
                                                            text = if (isChecked) "ACTIVE" else "INACTIVE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "db" -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 1. Summary of Tables
                                item {
                                    Text(
                                        text = "SQLITE ROOM DATABASE STATUS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Database File: language_learning_db", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("📊 language_units (Vocabulary)", style = MaterialTheme.typography.bodySmall)
                                                Text("${allUnitsState.size} entries (${allUnitsState.count { it.isLearned }} learned)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("🏆 quiz_scores (Quiz Results)", style = MaterialTheme.typography.bodySmall)
                                                Text("${quizScoresState.size} records", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("🎙️ speaking_practice_logs (Speech Logs)", style = MaterialTheme.typography.bodySmall)
                                                Text("${speakingLogsState.size} entries", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // 2. table: language_units Actions
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Table: language_units (Vocabulary State)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.debugMarkAllLearned() },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Mark All Learned", style = MaterialTheme.typography.labelSmall)
                                            }
                                            
                                            OutlinedButton(
                                                onClick = { viewModel.debugResetVocabProgress() },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Reset Vocab Progress", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }

                                // 3. Table: quiz_scores Rows
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Table: quiz_scores (${quizScoresState.size} rows)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            TextButton(onClick = { viewModel.debugClearQuizHistory() }) {
                                                Text("Truncate Table 🗑️", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        if (quizScoresState.isEmpty()) {
                                            Text("No records found in quiz_scores.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 200.dp)
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                                    items(quizScoresState.take(30)) { score ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("id: ${score.id} | ${score.language} (${score.category})", style = MaterialTheme.typography.bodySmall)
                                                            Text("Score: ${score.score}/${score.totalQuestions}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Table: speaking_practice_logs Rows
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Table: speaking_practice_logs (${speakingLogsState.size} rows)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            TextButton(onClick = { viewModel.debugClearSpeakingLogs() }) {
                                                Text("Truncate Table 🗑️", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        if (speakingLogsState.isEmpty()) {
                                            Text("No records found in speaking_practice_logs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 200.dp)
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                                    items(speakingLogsState.take(30)) { log ->
                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text("id: ${log.id} | Word: \"${log.original}\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                                Text("${log.accuracy}% match", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = if (log.accuracy >= 75) Color(0xFF2E7D32) else Color(0xFFC62828))
                                                            }
                                                            Text("Lang: ${log.language} | points: +${log.pointsEarned} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "danger" -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(72.dp)
                                )
                                Text(
                                    text = "DANGER ZONE",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Resetting the storage will instantly delete all SharedPreferences, truncate all Room quiz results and custom logs, reset vocabulary learned statuses, and return the application back to onboarding step 1. This action is completely irreversible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        viewModel.debugResetAllStorage()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("debug_factory_reset_btn")
                                ) {
                                    Text("🚨 Factory Reset & Clear All Database Storage", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun LessonsTabScreen(
    viewModel: LanguageLearningViewModel,
    wordsList: List<LanguageUnit>,
    onTabChange: (String) -> Unit,
    onPracticeSpeaking: (LanguageUnit) -> Unit
) {
    var customTopicText by remember { mutableStateOf("") }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    val geminiLoading by viewModel.geminiLoading.collectAsState()
    val geminiError by viewModel.geminiError.collectAsState()
    
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val totalCount = wordsList.size
    val learnedCount = wordsList.count { it.isLearned }
    val favoriteCount = wordsList.count { it.isFavorite }
    
    val userPoints by viewModel.userPoints.collectAsState()
    val userStreak by viewModel.userStreak.collectAsState()
    val dailyGoalTarget by viewModel.dailyGoalTarget.collectAsState()

    if (showEditGoalDialog) {
        AlertDialog(
            onDismissRequest = { showEditGoalDialog = false },
            title = {
                Text(
                    text = "Set Daily Practice Goal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Set a target number of words to practice each day to maintain your consistency. Practicing words via flashcards, speaking exercises, or marking them learned will count toward your progress!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "CHOOSE TARGET WORDS:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.1.sp
                    )
                    
                    val goalOptions = listOf(3, 5, 10, 15, 20, 30)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        goalOptions.forEach { option ->
                            val isSelected = dailyGoalTarget == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.setDailyGoalTarget(option) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$option",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "CUSTOM TARGET: $dailyGoalTarget words",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.1.sp
                    )
                    
                    Slider(
                        value = dailyGoalTarget.toFloat(),
                        onValueChange = { viewModel.setDailyGoalTarget(it.toInt().coerceIn(1, 50)) },
                        valueRange = 1f..50f,
                        steps = 49,
                        modifier = Modifier.testTag("daily_goal_slider")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showEditGoalDialog = false },
                    modifier = Modifier.testTag("confirm_daily_goal")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Flying Welcome Mascot (Mr. Owl Welcome Banner)
        item {
            val userNameState by viewModel.userName.collectAsState()
            val selectedLanguage by viewModel.selectedLanguage.collectAsState()
            val dailyGoalTarget by viewModel.dailyGoalTarget.collectAsState()
            
            var showWelcomeFlyer by remember { mutableStateOf(true) }
            var startFlying by remember { mutableStateOf(false) }
            
            // Continuous bobbing & flapping animation state
            val infiniteTransition = rememberInfiniteTransition(label = "owl_flight")
            val bobbingOffset by infiniteTransition.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "owl_bob"
            )
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = -4f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "owl_tilt"
            )
            val scaleFactor by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "owl_scale"
            )
            
            // Sparkle state when waved back
            var wavedCount by remember { mutableStateOf(0) }
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(200)
                startFlying = true
            }
            
            AnimatedVisibility(
                visible = startFlying && showWelcomeFlyer,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(1000)),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(800))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("mr_owl_welcome_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Animated Flying/Floating Owl
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .graphicsLayer {
                                        translationY = bobbingOffset
                                        rotationZ = rotationAngle
                                        scaleX = scaleFactor
                                        scaleY = scaleFactor
                                    }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (wavedCount % 2 == 0) "🦉" else "👋🦉", 
                                        fontSize = 38.sp
                                    )
                                }
                            }
                            
                            // Welcoming Speech Bubble
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Mr. Owl Flies In!",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                    Text(text = "✨🚀", fontSize = 12.sp)
                                }
                                
                                Text(
                                    text = "Welcome, ${if (userNameState.isBlank()) "Scholar" else userNameState}! 🦉 I just flew in to cheer you on as you practice $selectedLanguage today! Let's hit our goal of $dailyGoalTarget words! 🎓🌟",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        
                        // Interactive action row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showWelcomeFlyer = false
                                },
                                modifier = Modifier.testTag("owl_fly_away_btn")
                            ) {
                                Text(
                                    text = "Fly Away! 🍃", 
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Button(
                                onClick = {
                                    wavedCount++
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("owl_wave_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Wave back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (wavedCount == 0) "Wave Back 👋" else "Waved $wavedCount Times! ❤️",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bento Personalized Profile Header (Row layout inspired by header)
        item {
            val userNameState by viewModel.userName.collectAsState()
            val nativeLanguageState by viewModel.nativeLanguage.collectAsState()
            var isEditingName by remember { mutableStateOf(false) }
            var tempName by remember { mutableStateOf(userNameState) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Profile row with customizable name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar decoration
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (userNameState.isNotEmpty()) userNameState.take(1).uppercase() else "🎓",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Column {
                                if (isEditingName) {
                                    OutlinedTextField(
                                        value = tempName,
                                        onValueChange = { tempName = it },
                                        singleLine = true,
                                        modifier = Modifier
                                            .width(180.dp)
                                            .testTag("edit_username_input"),
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        trailingIcon = {
                                            IconButton(
                                                modifier = Modifier.testTag("save_username_button"),
                                                onClick = {
                                                    if (tempName.isNotBlank()) {
                                                        viewModel.updateProfile(tempName, nativeLanguageState)
                                                    }
                                                    isEditingName = false
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Save Name",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = userNameState,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        IconButton(
                                            modifier = Modifier.size(24.dp).testTag("edit_username_btn"),
                                            onClick = {
                                                tempName = userNameState
                                                isEditingName = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Name",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Active Native: $nativeLanguageState",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        
                        // Streak & Points Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Flame
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF2B8B5))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = Color(0xFF601410),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$userStreak",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF601410)
                                )
                            }
                            
                            // Points
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏆", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$userPoints XP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { viewModel.resetSetup() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_setup_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reconfigure Target & Native Languages",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Pronunciation Progress & Daily Learning Streak Track Card
        item {
            PronunciationStreakProgressCard(
                viewModel = viewModel,
                userStreak = userStreak,
                userPoints = userPoints
            )
        }

        // 1. Bento Large Hero Card: Interactive Daily Learning Goal Tracking
        item {
            val dailyGoalTargetState by viewModel.dailyGoalTarget.collectAsState()
            val practicedWordIdsTodayState by viewModel.practicedWordIdsToday.collectAsState()
            val practicedTodayCount = practicedWordIdsTodayState.size
            val percentage = if (dailyGoalTargetState > 0) (practicedTodayCount * 100) / dailyGoalTargetState else 0
            val fillRatio = if (dailyGoalTargetState > 0) practicedTodayCount.toFloat() / dailyGoalTargetState.toFloat() else 0f

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daily_goal_hero_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), // Theme-adaptive Primary Container
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Small overlay badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "DAILY GOAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Edit button
                            TextButton(
                                onClick = { showEditGoalDialog = true },
                                modifier = Modifier.testTag("edit_daily_goal_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Goal",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Adjust Target",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = if (percentage >= 100) "🎉 Daily Goal Achieved!" else "Keep up the consistency!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer // Theme-adaptive Text Color
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Practice vocabulary words to hit your daily target. Today you have practiced $practicedTodayCount of $dailyGoalTargetState words.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Progress bar block inside Hero card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Progress: ${percentage.coerceAtMost(100)}% ($practicedTodayCount / $dailyGoalTargetState words)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // Translucent track with filled primary indicator
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color.White.copy(alpha = 0.5f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fillRatio.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                            
                            Button(
                                onClick = { onTabChange("vocabulary") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Practice",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val dailyFocusWords by viewModel.dailyFocusWords.collectAsState()
                        if (dailyFocusWords.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "🎯 TODAY'S FOCUS WORDS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.1.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fresh unlearned words selected for today's goal. Rotates automatically tomorrow!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val context = androidx.compose.ui.platform.LocalContext.current
                            dailyFocusWords.forEach { unit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.06f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = unit.original,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = unit.translation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.speakPronunciation(unit.original) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Listen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        val isCompletedToday = practicedWordIdsTodayState.contains(unit.id)
                                        IconButton(
                                            onClick = {
                                                if (!isCompletedToday) {
                                                    viewModel.trackWordPractice(unit.id)
                                                    viewModel.setUnitLearnedStatus(unit.id, true)
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCompletedToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = if (isCompletedToday) "Completed" else "Mark Done",
                                                tint = if (isCompletedToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        // Unlocked Milestones Bento Card
        item {
            val unlockedBadges by viewModel.unlockedBadges.collectAsState()
            val allBadges = com.example.ui.ALL_BADGES
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎖️ Unlocked Milestones (${unlockedBadges.size}/${allBadges.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allBadges) { badge ->
                            val isUnlocked = unlockedBadges.contains(badge.id)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.width(135.dp),
                                border = BorderStroke(1.dp, if (isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = badge.icon,
                                        fontSize = 32.sp,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = if (isUnlocked) 1.0f else 0.35f
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = badge.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = badge.criteriaDescription,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center,
                                        minLines = 2,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Bento Grid Section (Smaller tiles in 2-row / 2-col arrangement)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tile A (Vocab Tall Box)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable { onTabChange("dictionary") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), // Adapt to sky blue container
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "Vocab",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Vocab",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "$totalCount items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Tile B (Flashcards High Contrast Dark Box)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable { onTabChange("vocabulary") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), // Duolingo green accent
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = "Flashcards",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Flashcards",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "Quick review",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tile C (Grammar / Starred)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable {
                                viewModel.updateSelectedCategory("Phrases")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), // Gold/Orange container
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Starred phrases",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Favorite",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "$favoriteCount starred",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Tile D (Weekly Quiz Medium Purple Card)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable { onTabChange("quiz") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), // Sky blue
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = "Quiz",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Weekly Quiz",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF21005D)
                                )
                                Text(
                                    text = "Test your skills",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // AI Dynamic Language Generator Option (Gemini Direct REST Integration)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI tool",
                            tint = Color(0xFF9333EA)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bento AI Generator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter any topic or scenario (e.g. \"In a coffee shop\", \"Asking directions in Berlin\") to generate 5 tailored lessons with native pronunciations instantly using Gemini!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = customTopicText,
                        onValueChange = { customTopicText = it },
                        placeholder = { Text("Topic: e.g. Airport, Restaurant, Shopping...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_topic_input"),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (geminiLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gemini is translating & generating...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = {
                                viewModel.generateAIUnits(customTopicText)
                                customTopicText = ""
                            },
                            enabled = customTopicText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_generate_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Vocabulary list")
                        }
                    }

                    if (geminiError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Alert",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = geminiError ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.clearGeminiError() },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "close",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lesson Categories quick links
        item {
            Text(
                text = "Course Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            val categories = listOf(
                Triple("All", "📚", "Everything"),
                Triple("Vocabulary", "🍎", "Nouns & Verbs"),
                Triple("Phrases", "💬", "Daily speech"),
                Triple("Grammar", "📝", "Structures"),
                Triple("Custom", "🪄", "AI Generated")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (cat, emoji, subtitle) ->
                    val isSelected = viewModel.selectedCategory.collectAsState().value == cat
                    Card(
                        modifier = Modifier
                            .width(130.dp)
                            .clickable { viewModel.updateSelectedCategory(cat) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Active filtered lists preview banner
        item {
            Text(
                text = "${viewModel.selectedCategory.collectAsState().value} Preview (${wordsList.size} items)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (wordsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Category is empty",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Type in custom topic above and let Gemini fill in the terms!",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(wordsList.take(6)) { unit ->
                VocabularyRow(unit = unit, viewModel = viewModel, onPracticeSpeaking = onPracticeSpeaking)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PracticeSpeakingDialog(
    unit: LanguageUnit,
    viewModel: LanguageLearningViewModel,
    onDismiss: () -> Unit
) {
    val isListening by viewModel.isListening.collectAsState()
    val speechRecognitionAvailable by viewModel.speechRecognitionAvailable.collectAsState()
    val speechMatchResult by viewModel.speechMatchResult.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(unit) {
        viewModel.selectUnitForSpeaking(unit)
    }
    
    // Setup Android RECORD_AUDIO runtime permissions launcher
    val launchPermission = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening(unit)
        } else {
            android.widget.Toast.makeText(context, "Microphone access is required to analyze speaking pronunciation.", android.widget.Toast.LENGTH_SHORT).show()
            viewModel.startListening(unit)
        }
    }

    // Auto-mark as learned when successfully pronunciation matched
    LaunchedEffect(speechMatchResult) {
        val result = speechMatchResult
        if (result != null && result.accuracy >= 75) {
            viewModel.setUnitLearnedStatus(unit.id, true)
        }
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.stopListening()
            onDismiss()
        },
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Practice Pronunciation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pronounce this ${unit.language} term clearly:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Big Term Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = unit.original,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "[${unit.ipa}]",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = unit.translation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.speakPronunciation(unit.original) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, "Listen")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hear Correct Sound", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(18.dp))
                
                // Listening pulsing visualizer
                if (isListening) {
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by transition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    
                    Box(
                        modifier = Modifier.size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = 1.0f - (pulseScale - 1.0f) * 2.5f
                                }
                                .clip(RoundedCornerShape(45.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        )
                        FilledIconButton(
                            onClick = { viewModel.stopListening() },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Listening...", modifier = Modifier.size(32.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Listening to your voice...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // Tap to listen trigger (always visible after speech is processed so they can retry)
                    FilledIconButton(
                        onClick = {
                            launchPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.size(72.dp).testTag("practice_mic_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Tap to speak", modifier = Modifier.size(36.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (speechMatchResult != null) "Tap microphone to practice again" else "Tap microphone to practice",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Result Feedback Module (Green if correct (accuracy >= 75%), Red if wrong (accuracy < 75%))
                speechMatchResult?.let { result ->
                    val isCorrect = result.accuracy >= 75
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE) // soft green / soft red
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)) // deep green / deep red border
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = if (isCorrect) "Correct" else "Wrong",
                                    tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCorrect) "Correct Pronunciation! 🌟" else "Pronunciation Needs Work! ❌",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCorrect) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Accuracy Score: ${result.accuracy}%",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCorrect) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = result.feedback,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = Color.DarkGray
                            )
                            
                            if (result.textSpoken.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Heard Spoken: \"${result.textSpoken}\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            if (result.pointsAwarded > 0 && isCorrect) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "+${result.pointsAwarded} XP Earned!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }
                
                // Emulator / Mic Helper manual rating choices
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Emulator Helper",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Emulator Self-Assessment Fallback",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cloud emulators can sometimes restrict live microphone routing. Repeat the terms out loud to practice, then choose your rating directly below to earn XP & update your streak!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.setManualSpeechResult(95, "Pronounced terms fluently out loud", unit.original) 
                                    viewModel.setUnitLearnedStatus(unit.id, true)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE8F5E9),
                                    contentColor = Color(0xFF2E7D32)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🌟 Perfect", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text("95% • +15 XP", fontSize = 8.sp)
                                }
                            }
                            
                            Button(
                                onClick = { 
                                    viewModel.setManualSpeechResult(78, "Spoken with minor accent details", unit.original)
                                    viewModel.setUnitLearnedStatus(unit.id, true)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFF8E1),
                                    contentColor = Color(0xFFF57F17)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("👍 Good", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text("78% • +10 XP", fontSize = 8.sp)
                                }
                            }
                            
                            Button(
                                onClick = { 
                                    viewModel.setManualSpeechResult(40, "Needs more practice and sound correction", unit.original) 
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFEBEE),
                                    contentColor = Color(0xFFC62828)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🗣️ Struggled", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text("40% • 0 XP", fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onDismiss() }
                ) {
                    Text("Done")
                }
            }
        }
    )
}

@Composable
fun VocabularyRow(
    unit: LanguageUnit,
    viewModel: LanguageLearningViewModel,
    onPracticeSpeaking: (LanguageUnit) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                viewModel.speakPronunciation(unit.original)
                viewModel.trackWordPractice(unit)
            },
        colors = CardDefaults.cardColors(
            containerColor = if (unit.isLearned) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    viewModel.speakPronunciation(unit.original)
                    viewModel.trackWordPractice(unit)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Speak",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = unit.original,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (unit.isLearned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${unit.ipa}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = unit.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick actions
            IconButton(
                onClick = { onPracticeSpeaking(unit) },
                modifier = Modifier.testTag("vocabulary_row_mic_${unit.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Practice Speaking",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = { viewModel.toggleFavorite(unit) }) {
                Icon(
                    imageVector = if (unit.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Star",
                    tint = if (unit.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { viewModel.toggleLearned(unit) }) {
                Icon(
                    imageVector = if (unit.isLearned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Check learned",
                    tint = if (unit.isLearned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FlashcardsTabScreen(
    viewModel: LanguageLearningViewModel,
    wordsList: List<LanguageUnit>,
    onPracticeSpeaking: (LanguageUnit) -> Unit
) {
    if (wordsList.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No flashcards available here", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Try switching categories or generating custom words using Gemini in the Lessons screen!", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var currentIndex by remember { mutableStateOf(0) }
    // Ensure index doesn't go outer bounds when unit list switches
    if (currentIndex >= wordsList.size) {
        currentIndex = 0
    }

    val currentUnit = wordsList.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Flashcard ${currentIndex + 1} of ${wordsList.size}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (currentUnit != null) {
            var isFlipped by remember { mutableStateOf(false) }
            
            // Re-trigger face side when changing cards
            LaunchedEffect(currentIndex) {
                isFlipped = false
                viewModel.trackWordPractice(currentUnit)
            }

            // Beautiful Card-Flipping rotation animation setup
            val rotationAngle by animateFloatAsState(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "CardFlipAnimation"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .graphicsLayer {
                        rotationY = rotationAngle
                        cameraDistance = 12f * density
                    }
                    .clickable { 
                        isFlipped = !isFlipped 
                        viewModel.trackWordPractice(currentUnit)
                    }
                    .testTag("interactive_flashcard"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFlipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                // If it's flipped more than halfway, invert content scale to avoid mirrored labels!
                if (rotationAngle > 90f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .graphicsLayer { rotationY = 180f },
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Card back: translations and sentence usages
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "BACK (English Definition)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentUnit.translation,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "[${currentUnit.ipa}]",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Context Example Sentence
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Example:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentUnit.exampleOriginal,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUnit.exampleTranslation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Quick action tools inside flipped back card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledIconButton(
                                onClick = { viewModel.speakPronunciation(currentUnit.original) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.VolumeUp, "pronounce")
                            }

                            FilledIconButton(
                                onClick = { onPracticeSpeaking(currentUnit) },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.testTag("flashcard_practice_speaking")
                            ) {
                                Icon(Icons.Default.Mic, "Practice Speaking")
                            }
                        }
                    }
                } else {
                    // Card front: clean displaying of original target vocabulary word
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "FRONT (Target Language)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row {
                                Icon(
                                    imageVector = if (currentUnit.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (currentUnit.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (currentUnit.isLearned) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentUnit.original,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Category: ${currentUnit.category}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "👉 TAP CARD TO REVEAL TRANSLATION 👈",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interlocking card controller buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    if (currentIndex > 0) currentIndex-- else currentIndex = wordsList.size - 1
                },
                modifier = Modifier.width(100.dp).testTag("prev_card_button")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "previous")
            }

            if (currentUnit != null) {
                // Quickly flag / review bookmark stats directly from controller Row
                OutlinedIconButton(
                    onClick = { viewModel.toggleFavorite(currentUnit) }
                ) {
                    Icon(
                        imageVector = if (currentUnit.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (currentUnit.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedIconButton(
                    onClick = { viewModel.toggleLearned(currentUnit) }
                ) {
                    Icon(
                        imageVector = if (currentUnit.isLearned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Learned marker",
                        tint = if (currentUnit.isLearned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    if (currentIndex < wordsList.size - 1) currentIndex++ else currentIndex = 0
                },
                modifier = Modifier.width(100.dp).testTag("next_card_button")
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "next")
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun QuizScoreboardTabScreen(
    viewModel: LanguageLearningViewModel
) {
    val scoresHistory by viewModel.quizScores.collectAsState()
    val wordsList by viewModel.allUnits.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Quiz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aistudio Language Arena",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Note that matching quizzes require at least 4 items in the list
                Text(
                    text = "Put your active translations to the test! Quizzes generate matchings dynamically from your learned vocabulary.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.startQuiz() },
                    modifier = Modifier.fillMaxWidth().testTag("start_quiz_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Enter Quiz Arena (5 Questions)")
                }

                if (wordsList.size < 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Need at least 4 terms in your current tab pool! We will auto-inject fallback starter terms on start.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Quiz History Score log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (scoresHistory.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No scores logged yet",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Complete your first match quiz challenge to see stats!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scoresHistory) { log ->
                    QuizHistoryRow(log = log)
                }
            }
        }
    }
}

@Composable
fun QuizHistoryRow(log: QuizScore) {
    val dateStr = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    val isPerfect = log.score == log.totalQuestions

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isPerfect) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPerfect) "👑" else "✏️",
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.language} matching test",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${log.score}/${log.totalQuestions}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPerfect) Color(0xFFD97706) else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isPerfect) "Perfect!" else "Keep practicing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuizScreen(
    viewModel: LanguageLearningViewModel
) {
    val questions by viewModel.quizQuestions.collectAsState()
    val index by viewModel.currentQuestionIndex.collectAsState()
    val quizScore by viewModel.quizScore.collectAsState()
    val answeredIndex by viewModel.answeredIndex.collectAsState()
    val quizCompleted by viewModel.quizCompleted.collectAsState()

    val currentQuestion = questions.getOrNull(index)

    Scaffold(
        topBar = {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quiz Arena",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    IconButton(
                        onClick = { viewModel.exitQuiz() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Exit Quiz",
                            tint = Color.Red
                        )
                    }
                }
                
                // Active Quiz Progress indicator
                val progress = if (questions.isNotEmpty()) (index + 1).toFloat() / questions.size else 0f
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) { innerPadding ->
        if (quizCompleted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🎉 MATCH FINISHED! 🎉",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val isPerfect = quizScore == questions.size
                Card(
                    modifier = Modifier.size(160.dp),
                    shape = RoundedCornerShape(80.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPerfect) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isPerfect) "🏆" else "🌟",
                            fontSize = 44.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$quizScore / ${questions.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isPerfect) "Sensational! A complete 5/5 perfect matching course translation!" else "Fantastic effort! Keep checking individual pronunciations to improve accuracy.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = { viewModel.exitQuiz() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exit_quiz_finished_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Return to scoreboard")
                }
            }
        } else if (currentQuestion != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "QUESTION ${index + 1} OF ${questions.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentQuestion.question,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "SELECT THE MATCH:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // 4 Multiple Choice option list
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        currentQuestion.options.forEachIndexed { optIdx, option ->
                            val isSelected = answeredIndex == optIdx
                            val isCorrectAnswer = optIdx == currentQuestion.correctOptionIndex
                            val hasBeenAnswered = answeredIndex != null
                            
                            val btnBg = if (hasBeenAnswered) {
                                if (isCorrectAnswer) Color(0xFFD1FAE5) // Clean Green highlight for correct answer
                                else if (isSelected) Color(0xFFFEE2E2) // Red highlight for chosen incorrect answer
                                else MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surface
                            }

                            val borderClr = if (hasBeenAnswered) {
                                if (isCorrectAnswer) Color(0xFF10B981)
                                else if (isSelected) Color(0xFFEF4444)
                                else MaterialTheme.colorScheme.outlineVariant
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }

                            val btnTextClr = if (hasBeenAnswered) {
                                if (isCorrectAnswer) Color(0xFF0F5132)
                                else if (isSelected) Color(0xFF842029)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.answerQuestion(optIdx) }
                                    .testTag("quiz_option_$optIdx"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = btnBg),
                                border = BorderStroke(1.5.dp, borderClr)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${('A' + optIdx)}.  ",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = btnTextClr
                                    )
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = btnTextClr,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    if (hasBeenAnswered) {
                                        if (isCorrectAnswer) {
                                            Icon(Icons.Default.Check, contentDescription = "Correct", tint = Color(0xFF10B981))
                                        } else if (isSelected) {
                                            Icon(Icons.Default.Close, contentDescription = "Incorrect", tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom validation result label & continue controls
                if (answeredIndex != null) {
                    val wasCorrect = answeredIndex == currentQuestion.correctOptionIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (wasCorrect) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (wasCorrect) "✨ AMAZING - CORRECT! ✨" else "⚠️ NOT QUITE CORRECT",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = if (wasCorrect) Color(0xFF0F5132) else Color(0xFF842029)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Word: ${currentQuestion.originalWord} [${currentQuestion.ipa}] = \"${currentQuestion.translationWord}\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (wasCorrect) Color(0xFF0F5132) else Color(0xFF842029),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { viewModel.nextQuestion() },
                            modifier = Modifier.fillMaxWidth().testTag("next_question_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (index == questions.size - 1) "View Final results" else "Continue to next Question",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Match the correct translation to proceed!",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DictionaryTabScreen(
    viewModel: LanguageLearningViewModel,
    wordsList: List<LanguageUnit>,
    onPracticeSpeaking: (LanguageUnit) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyStarred by remember { mutableStateOf(false) }

    val filteredList = remember(wordsList, searchQuery, showOnlyStarred) {
        wordsList.filter { unit ->
            val matchesSearch = unit.original.contains(searchQuery, ignoreCase = true) ||
                    unit.translation.contains(searchQuery, ignoreCase = true)
            val matchesStarred = if (showOnlyStarred) unit.isFavorite else true
            matchesSearch && matchesStarred
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search words or translations...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showOnlyStarred,
                    onCheckedChange = { showOnlyStarred = it },
                    modifier = Modifier.testTag("starred_filter_checkbox")
                )
                Text(
                    "Show only starred items",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "${filteredList.size} displayed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (filteredList.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = "Not found",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No terms found matching filters", fontWeight = FontWeight.Bold)
                Text("Try adjusting terms or custom adding topics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { unit ->
                    VocabularyRow(unit = unit, viewModel = viewModel, onPracticeSpeaking = onPracticeSpeaking)
                }
            }
        }
    }
}

@Composable
fun LanguageSetupScreen(viewModel: LanguageLearningViewModel) {
    val nativeLanguageState by viewModel.nativeLanguage.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val userNameState by viewModel.userName.collectAsState()
    val themeModeState by viewModel.themeMode.collectAsState()

    var currentStep by remember { mutableStateOf(1) }
    var inputName by remember { mutableStateOf(userNameState) }
    var chosenNative by remember { mutableStateOf(nativeLanguageState) }
    var chosenTarget by remember { mutableStateOf(selectedLanguage) }
    var chosenReason by remember { mutableStateOf("Brain Exercise") }
    var chosenGoalWords by remember { mutableStateOf(5) } // Default 5 words

    val nativeList = listOf(
        Pair("English", "🇺🇸"),
        Pair("Telugu", "🇮🇳"),
        Pair("Hindi", "🇮🇳"),
        Pair("Malayalam", "🇮🇳"),
        Pair("Tamil", "🇮🇳"),
        Pair("Kannada", "🇮🇳"),
        Pair("Spanish", "🇪🇸"),
        Pair("French", "🇫🇷"),
        Pair("German", "🇩🇪"),
        Pair("Japanese", "🇯🇵")
    )

    val targetList = listOf(
        Triple("Spanish", "🇪🇸", "Spain"),
        Triple("French", "🇫🇷", "France"),
        Triple("Japanese", "🇯🇵", "Japan"),
        Triple("German", "🇩🇪", "Germany"),
        Triple("Italian", "🇮🇹", "Italy"),
        Triple("Portuguese", "🇵🇹", "Portugal"),
        Triple("Chinese", "🇨🇳", "China"),
        Triple("Korean", "🇰🇷", "Korea"),
        Triple("Russian", "🇷🇺", "Russia"),
        Triple("Arabic", "🇸🇦", "Saudi Arabia"),
        Triple("Hindi", "🇮🇳", "India"),
        Triple("Swedish", "🇸🇪", "Sweden")
    )

    val motivationList = listOf(
        Triple("Travel & Culture", "✈️", "Explore new places and traditions"),
        Triple("Career Growth", "💼", "Boost professional & work prospects"),
        Triple("Brain Exercise", "🧠", "Strengthen memory & cognitive agility"),
        Triple("School Success", "🎓", "Excel in classrooms & language tests"),
        Triple("Family & Friends", "🤝", "Connect with native-speaking loved ones"),
        Triple("Hobby & Interest", "🌟", "Fun personal pursuit and pass time")
    )

    val dailyGoalList = listOf(
        Triple("Casual", 1, "1 word / day"),
        Triple("Regular", 3, "3 words / day"),
        Triple("Serious", 5, "5 words / day"),
        Triple("Insane", 10, "10 words / day")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Step Indicator & Progress Bar (Duolingo style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentStep > 1) currentStep-- },
                    enabled = currentStep > 1,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (currentStep > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }

                LinearProgressIndicator(
                    progress = { currentStep / 5f },
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "$currentStep/5",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val newMode = if (themeModeState == "dark") "light" else "dark"
                        viewModel.updateThemeMode(newMode)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (themeModeState == "dark") Icons.Default.WbSunny else Icons.Default.NightsStay,
                        contentDescription = "Switch Theme Mode",
                        tint = if (themeModeState == "dark") Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Mascot Speech Bubble Block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🦉", fontSize = 38.sp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = when (currentStep) {
                            1 -> "Hi there! I'm Mr. Owl, your personal learning owl. Let's customize your experience. What is your name?"
                            2 -> "Nice to meet you, ${if (inputName.isBlank()) "Scholar" else inputName}! Which language do you speak natively?"
                            3 -> "Fascinating choice! Now, which language are you excited to learn and master?"
                            4 -> "That's wonderful! What is your main motivation for learning this language?"
                            5 -> "Consistency is key! Set your daily learning target words to complete the setup:"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step Content Pages
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                label = "setup_step_transition"
            ) { step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        1 -> {
                            // Step 1: Name Input
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "ENTER YOUR NAME:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.1.sp
                                    )
                                    OutlinedTextField(
                                        value = inputName,
                                        onValueChange = { inputName = it },
                                        placeholder = { Text("e.g. Alex") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("setup_username_input"),
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                        2 -> {
                            // Step 2: Native Language Selection
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "YOUR NATIVE TONGUE:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.1.sp
                                    )
                                    
                                    nativeList.chunked(2).forEach { chunk ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            chunk.forEach { (native, flag) ->
                                                val isSelected = chosenNative.equals(native, ignoreCase = true)
                                                Card(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(54.dp)
                                                        .testTag("setup_native_${native.lowercase()}")
                                                        .clickable { chosenNative = native },
                                                    shape = RoundedCornerShape(14.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    ),
                                                    border = BorderStroke(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 14.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Text(text = flag, fontSize = 22.sp)
                                                        Text(
                                                            text = native,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = "Selected",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Step 3: Target Language Selection
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "LANGUAGE TO LEARN:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.1.sp
                                    )

                                    targetList.chunked(2).forEach { chunk ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            chunk.forEach { (lang, flag, label) ->
                                                val isSelected = chosenTarget.equals(lang, ignoreCase = true)
                                                Card(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(54.dp)
                                                        .testTag("setup_target_${lang.lowercase()}")
                                                        .clickable { chosenTarget = lang },
                                                    shape = RoundedCornerShape(14.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    ),
                                                    border = BorderStroke(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 14.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Text(text = flag, fontSize = 22.sp)
                                                        Text(
                                                            text = lang,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = "Selected",
                                                                tint = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        4 -> {
                            // Step 4: Learning Goal / Motivation
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "YOUR LEARNING GOAL:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.1.sp
                                    )

                                    motivationList.forEach { (reason, emoji, desc) ->
                                        val isSelected = chosenReason == reason
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { chosenReason = reason },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Text(text = emoji, fontSize = 24.sp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = reason,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = desc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        5 -> {
                            // Step 5: Daily Goal Targets
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "DAILY STUDY COMMITMENT:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.1.sp
                                    )

                                    dailyGoalList.forEach { (level, words, label) ->
                                        val isSelected = chosenGoalWords == words
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { chosenGoalWords = words },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(
                                                        text = level,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Onboarding Bottom Primary Action Button
            Button(
                onClick = {
                    if (currentStep < 5) {
                        currentStep++
                    } else {
                        val finalName = if (inputName.isBlank()) "Scholar" else inputName.trim()
                        viewModel.completeSetup(
                            name = finalName,
                            nativeLang = chosenNative,
                            targetLang = chosenTarget,
                            reason = chosenReason,
                            level = "Beginner", // default level
                            dailyGoal = chosenGoalWords
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("setup_next_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (currentStep < 5) "Continue" else "Finish Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (currentStep < 5) Icons.Default.ArrowForward else Icons.Default.Done,
                        contentDescription = "Continue",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}
