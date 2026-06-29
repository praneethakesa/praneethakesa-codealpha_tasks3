package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class LanguageBadge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val criteriaDescription: String
)

data class LeaderboardUser(
    val name: String,
    val country: String,
    val points: Int,
    val avatarEmoji: String,
    val isUser: Boolean = false
)

data class SpeechMatchResult(
    val textSpoken: String,
    val accuracy: Int,
    val feedback: String,
    val pointsAwarded: Int
)

val ALL_BADGES = listOf(
    LanguageBadge(
        id = "first_steps",
        name = "First Steps",
        description = "Embark on lessons and mark terms learned to begin your journey.",
        icon = "🌱",
        criteriaDescription = "Earn your first points"
    ),
    LanguageBadge(
        id = "speaking_sensation",
        name = "Speaking Sensation",
        description = "Practiced voice pronunciation 3 times with the speech feedback engine.",
        icon = "🗣️",
        criteriaDescription = "Practice speaking 3 times"
    ),
    LanguageBadge(
        id = "word_collector",
        name = "Word Collector",
        description = "Completed lessons and officially learned 5 words or phrases.",
        icon = "📚",
        criteriaDescription = "Learn 5 words"
    ),
    LanguageBadge(
        id = "vocab_virtuoso",
        name = "Vocabulary Virtuoso",
        description = "Earned vocabulary virtuoso status by mastering 10 language units.",
        icon = "🎓",
        criteriaDescription = "Learn 10 words"
    ),
    LanguageBadge(
        id = "polyglot_pioneer",
        name = "Polyglot Pioneer",
        description = "Learned or starred terms across at least 2 separate language pools.",
        icon = "🌍",
        criteriaDescription = "Practice 2 languages"
    ),
    LanguageBadge(
        id = "quiz_conqueror",
        name = "Quiz Conqueror",
        description = "Achieved flawless 5/5 perfect test results in the Quiz Arena.",
        icon = "👑",
        criteriaDescription = "Perfect score in Quiz"
    ),
    LanguageBadge(
        id = "daily_champion",
        name = "Daily Champion",
        description = "Hit your target number of daily practiced words to complete your consistency check.",
        icon = "🎯",
        criteriaDescription = "Achieve Daily Goal"
    ),
    LanguageBadge(
        id = "centurion_scholar",
        name = "Centurion Scholar",
        description = "Earned over 500 total points in global language learning studies.",
        icon = "⚔️",
        criteriaDescription = "Reach 500 XP points"
    )
)

class LanguageLearningViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "LanguageVM"
    private val repository: LanguageRepository

    // Global SharedPreferences for persistent gamification stats
    private val sharedPrefs = application.getSharedPreferences("aistudio_language_gamification", Context.MODE_PRIVATE)

    // User profile state variables
    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "Scholar") ?: "Scholar")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _nativeLanguage = MutableStateFlow(sharedPrefs.getString("native_language", "English") ?: "English")
    val nativeLanguage: StateFlow<String> = _nativeLanguage.asStateFlow()

    private val _setupCompleted = MutableStateFlow(sharedPrefs.getBoolean("setup_completed", false))
    val setupCompleted: StateFlow<Boolean> = _setupCompleted.asStateFlow()

    // Theme Mode Selection State: "system", "light", "dark"
    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    private val _learningReason = MutableStateFlow(sharedPrefs.getString("learning_reason", "Brain Exercise") ?: "Brain Exercise")
    val learningReason: StateFlow<String> = _learningReason.asStateFlow()

    private val _experienceLevel = MutableStateFlow(sharedPrefs.getString("experience_level", "Beginner") ?: "Beginner")
    val experienceLevel: StateFlow<String> = _experienceLevel.asStateFlow()

    fun updateProfile(name: String, nativeLang: String) {
        _userName.value = name
        _nativeLanguage.value = nativeLang
        sharedPrefs.edit()
            .putString("user_name", name)
            .putString("native_language", nativeLang)
            .apply()
    }

    fun completeSetup(
        name: String,
        nativeLang: String,
        targetLang: String,
        reason: String,
        level: String,
        dailyGoal: Int
    ) {
        _userName.value = name
        _nativeLanguage.value = nativeLang
        _selectedLanguage.value = targetLang
        _learningReason.value = reason
        _experienceLevel.value = level
        _dailyGoalTarget.value = dailyGoal
        _setupCompleted.value = true
        sharedPrefs.edit()
            .putString("user_name", name)
            .putString("native_language", nativeLang)
            .putString("selected_language", targetLang)
            .putString("learning_reason", reason)
            .putString("experience_level", level)
            .putInt("daily_goal_target", dailyGoal)
            .putBoolean("setup_completed", true)
            .apply()
        updateTTSLanguage(targetLang)
    }

    fun resetSetup() {
        _setupCompleted.value = false
        sharedPrefs.edit().putBoolean("setup_completed", false).apply()
    }

    // Gamification properties
    private val _userPoints = MutableStateFlow(sharedPrefs.getInt("points", 120)) // start at 120 to feel welcoming
    val userPoints: StateFlow<Int> = _userPoints.asStateFlow()

    private val _userStreak = MutableStateFlow(sharedPrefs.getInt("user_streak", 12)) // Default welcoming base of 12 days to transition gracefully
    val userStreak: StateFlow<Int> = _userStreak.asStateFlow()

    private val _lastActiveDate = MutableStateFlow(sharedPrefs.getString("last_active_date", "") ?: "")
    val lastActiveDate: StateFlow<String> = _lastActiveDate.asStateFlow()

    private val _speechAttempts = MutableStateFlow(sharedPrefs.getInt("speech_attempts", 0))
    val speechAttempts: StateFlow<Int> = _speechAttempts.asStateFlow()

    private val _unlockedBadges = MutableStateFlow<Set<String>>(sharedPrefs.getStringSet("unlocked_badges", emptySet()) ?: emptySet())
    val unlockedBadges: StateFlow<Set<String>> = _unlockedBadges.asStateFlow()

    // Global leaderboard sorted descending
    val leaderboardUsers: StateFlow<List<LeaderboardUser>> = _userPoints.map { points ->
        listOf(
            LeaderboardUser("Hana Sato", "Japan", 810, "🌸"),
            LeaderboardUser("Mateo Silva", "Spain", 620, "🐂"),
            LeaderboardUser("Camille Roux", "France", 450, "🥐"),
            LeaderboardUser("Sebastian K.", "Germany", 310, "🥨"),
            LeaderboardUser("You (${_userName.value})", "Global", points, "🎓", isUser = true),
            LeaderboardUser("Chloe Dupont", "France", 185, "🥞"),
            LeaderboardUser("Diego Gomez", "Spain", 90, "🇪🇸")
        ).sortedByDescending { it.points }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Speech properties
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _speechRecognitionAvailable = MutableStateFlow(true)
    val speechRecognitionAvailable: StateFlow<Boolean> = _speechRecognitionAvailable.asStateFlow()

    private val _speechMatchResult = MutableStateFlow<SpeechMatchResult?>(null)
    val speechMatchResult: StateFlow<SpeechMatchResult?> = _speechMatchResult.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var currentPracticedUnit: LanguageUnit? = null

    // Global selected language state
    private val _selectedLanguage = MutableStateFlow(sharedPrefs.getString("selected_language", "Spanish") ?: "Spanish")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Global selected category filter: "All", "Vocabulary", "Phrases", "Grammar", "Custom"
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Database flow containing all units
    val allUnits: StateFlow<List<LanguageUnit>>

    // Custom loading indicators
    private val _geminiLoading = MutableStateFlow(false)
    val geminiLoading: StateFlow<Boolean> = _geminiLoading.asStateFlow()

    private val _geminiError = MutableStateFlow<String?>(null)
    val geminiError: StateFlow<String?> = _geminiError.asStateFlow()

    // Quiz State Management
    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _quizActive = MutableStateFlow(false)
    val quizActive: StateFlow<Boolean> = _quizActive.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    private val _answeredIndex = MutableStateFlow<Int?>(null) // selected index by user
    val answeredIndex: StateFlow<Int?> = _answeredIndex.asStateFlow()

    private val _quizCompleted = MutableStateFlow(false)
    val quizCompleted: StateFlow<Boolean> = _quizCompleted.asStateFlow()

    // Quiz high scores historical flow
    val quizScores: StateFlow<List<QuizScore>>

    // Speaking practice completed exercises flow
    val speakingLogs: StateFlow<List<SpeakingPracticeLog>>

    // Daily Learning Goal Tracking
    private val _dailyGoalTarget = MutableStateFlow(sharedPrefs.getInt("daily_goal_target", 5))
    val dailyGoalTarget: StateFlow<Int> = _dailyGoalTarget.asStateFlow()

    private val _practicedWordIdsToday = MutableStateFlow<Set<Int>>(emptySet())
    val practicedWordIdsToday: StateFlow<Set<Int>> = _practicedWordIdsToday.asStateFlow()

    // Active practicing/activity dates tracking
    private val _activeDates = MutableStateFlow<Set<String>>(sharedPrefs.getStringSet("completed_activity_dates", emptySet()) ?: emptySet())
    val activeDates: StateFlow<Set<String>> = _activeDates.asStateFlow()

    // Today's dynamic focus words/recommended new words that automatically rotate daily
    val dailyFocusWords: StateFlow<List<LanguageUnit>>

    // Audio text to speech engine
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        val db = AppDatabase.getDatabase(application)
        repository = LanguageRepository(db)

        // Observe historical high scores
        quizScores = repository.getAllScores()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Observe completed speaking logs
        speakingLogs = repository.getAllSpeakingLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Pre-populate historical active dates to align with the default welcoming 12-day streak if not initialized
        val savedActiveDates = sharedPrefs.getStringSet("completed_activity_dates", null)
        if (savedActiveDates == null || savedActiveDates.isEmpty()) {
            val initialDates = mutableSetOf<String>()
            val cal = java.util.Calendar.getInstance()
            // Go back 1 to 12 days to simulate a solid historical active record
            for (i in 1..12) {
                val backCal = java.util.Calendar.getInstance()
                backCal.add(java.util.Calendar.DATE, -i)
                initialDates.add(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(backCal.time))
            }
            sharedPrefs.edit().putStringSet("completed_activity_dates", initialDates).apply()
            _activeDates.value = initialDates
            
            // Also set last active date as yesterday to connect the streak cleanly on next action!
            if (sharedPrefs.getString("last_active_date", "").isNullOrBlank()) {
                val yesterdayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time.apply { cal.add(java.util.Calendar.DATE, -1) })
                _lastActiveDate.value = yesterdayStr
                sharedPrefs.edit().putString("last_active_date", yesterdayStr).apply()
            }
        }

        // Initialize today's practiced words from SharedPreferences
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Calendar.getInstance().time)
        val savedDate = sharedPrefs.getString("daily_goal_date", "") ?: ""
        if (savedDate == todayStr) {
            val savedIds = sharedPrefs.getStringSet("daily_goal_practiced_ids", emptySet()) ?: emptySet()
            _practicedWordIdsToday.value = savedIds.mapNotNull { it.toIntOrNull() }.toSet()
            // If they already have practice recorded from today on startup, ensure we reflect that in the active tracking and streak!
            if (savedIds.isNotEmpty()) {
                recordActivity()
            }
        } else {
            // New day! Let's reset the practiced IDs for today
            sharedPrefs.edit()
                .putString("daily_goal_date", todayStr)
                .putStringSet("daily_goal_practiced_ids", emptySet())
                .apply()
            _practicedWordIdsToday.value = emptySet()
        }

        // Reactively filter vocabulary list by selected language and category and translate transparently in-place
        allUnits = combine(
            repository.getAllUnits(),
            _selectedLanguage,
            _selectedCategory,
            _nativeLanguage
        ) { units, language, category, nativeLang ->
            units.filter { unit ->
                val languageMatches = unit.language.equals(language, ignoreCase = true)
                val categoryMatches = if (category == "All") {
                    true
                } else if (category == "Custom") {
                    // Include any category that is NOT Vocabulary, Phrases, or Grammar
                    unit.category != "Vocabulary" && unit.category != "Phrases" && unit.category != "Grammar"
                } else {
                    unit.category.equals(category, ignoreCase = true)
                }
                languageMatches && categoryMatches
            }.map { unit ->
                unit.copy(
                    translation = getNativeTranslation(unit.translation, nativeLang),
                    exampleTranslation = getNativeTranslation(unit.exampleTranslation, nativeLang)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initialize Today's Dynamic Focus Words based on stable daily hashing seed
        dailyFocusWords = combine(
            allUnits,
            _practicedWordIdsToday
        ) { units, practicedIds ->
            // Prioritize vocabulary terms that have not been learned yet
            val unlearned = units.filter { !it.isLearned }
            val sourceList = if (unlearned.isNotEmpty()) unlearned else units
            
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val seed = todayStr.hashCode().toLong()
            val random = java.util.Random(seed)
            
            if (sourceList.isNotEmpty()) {
                sourceList.shuffled(random).take(3)
            } else {
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Collect DB status to automatically evaluate milestones
        viewModelScope.launch {
            repository.getAllUnits().collect { units ->
                val learnedCount = units.count { it.isLearned }
                val languagesUsed = units.filter { it.isLearned || it.isFavorite }.map { it.language.lowercase() }.distinct().size
                evaluateMilestones(learnedCount, languagesUsed)
            }
        }

        // Primary initialization of Text-To-Speech
        initTTS(application)
        
        // Setup initial daily learning streak
        checkAndResetStreakOnStartup()

        // Setup periodic real-time checks to automatically transition dates live
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000) // Check every 10 seconds
                refreshTodayDateAndResetIfNeeded()
            }
        }
    }

    private fun getCurrentDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DATE, -1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    fun refreshTodayDateAndResetIfNeeded() {
        val todayStr = getCurrentDateString()
        val savedDate = sharedPrefs.getString("daily_goal_date", "") ?: ""
        if (savedDate != todayStr) {
            // Midnight rollover detected! Dynamically clear practiced set
            sharedPrefs.edit()
                .putString("daily_goal_date", todayStr)
                .putStringSet("daily_goal_practiced_ids", emptySet())
                .apply()
            _practicedWordIdsToday.value = emptySet()
            
            // Recheck streak status
            checkAndResetStreakOnStartup()
        }
    }

    private fun checkAndResetStreakOnStartup() {
        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()
        val lastActive = _lastActiveDate.value

        Log.d("StreakDiagnostic", "checkAndResetStreakOnStartup - Today: $today, Yesterday: $yesterday, Last Active: $lastActive, Current Streak: ${_userStreak.value}")
        if (lastActive.isNotBlank() && lastActive != today && lastActive != yesterday) {
            Log.d("StreakDiagnostic", "checkAndResetStreakOnStartup - Streak broken! Last Active ($lastActive) is before Yesterday ($yesterday). Resetting streak from ${_userStreak.value} to 0.")
            _userStreak.value = 0
            sharedPrefs.edit().putInt("user_streak", 0).apply()
        } else {
            Log.d("StreakDiagnostic", "checkAndResetStreakOnStartup - Streak is preserved. Last Active: $lastActive.")
        }
    }

    fun recordActivity() {
        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()
        val lastActive = _lastActiveDate.value

        Log.d("StreakDiagnostic", "recordActivity started - Today: $today, Yesterday: $yesterday, Last Active: $lastActive, Current Streak: ${_userStreak.value}")

        // Record today as an active learning date
        val currentDates = _activeDates.value.toMutableSet()
        if (currentDates.add(today)) {
            _activeDates.value = currentDates
            sharedPrefs.edit().putStringSet("completed_activity_dates", currentDates).apply()
            Log.d("StreakDiagnostic", "recordActivity - Added today ($today) to completed activity dates. Total active dates: ${currentDates.size}")
        }

        if (lastActive == today) {
            Log.d("StreakDiagnostic", "recordActivity - Already recorded today. Streak remains at ${_userStreak.value} days.")
            return
        }

        val newStreak = if (lastActive == yesterday) {
            val s = _userStreak.value + 1
            Log.d("StreakDiagnostic", "recordActivity - Last active was yesterday. Incrementing streak: ${_userStreak.value} -> $s")
            s
        } else if (lastActive.isBlank()) {
            val s = _userStreak.value + 1
            Log.d("StreakDiagnostic", "recordActivity - First practice or empty last active date. Incrementing/preserving welcoming streak: ${_userStreak.value} -> $s")
            s
        } else {
            Log.d("StreakDiagnostic", "recordActivity - Streak broken (last active: $lastActive). Resetting streak to 1.")
            1
        }

        _userStreak.value = newStreak
        _lastActiveDate.value = today
        sharedPrefs.edit()
            .putInt("user_streak", newStreak)
            .putString("last_active_date", today)
            .apply()

        Log.d("StreakDiagnostic", "recordActivity finished - New Streak: $newStreak, Last Active Date updated to: $today")
    }

    private fun initTTS(context: Context) {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    updateTTSLanguage(_selectedLanguage.value)
                } else {
                    Log.e(TAG, "Initialization of TTS failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS setup error", e)
        }
    }

    fun updateSelectedLanguage(language: String) {
        _selectedLanguage.value = language
        updateTTSLanguage(language)
        // Reset category to All
        _selectedCategory.value = "All"
        sharedPrefs.edit().putString("selected_language", language).apply()
    }

    fun updateSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    private fun updateTTSLanguage(language: String) {
        if (!ttsReady || tts == null) return
        val locale = when (language.lowercase(Locale.ROOT)) {
            "spanish" -> Locale("es", "ES")
            "french" -> Locale("fr", "FR")
            "japanese" -> Locale.JAPAN
            "german" -> Locale.GERMAN
            "italian" -> Locale.ITALIAN
            "portuguese" -> Locale("pt", "PT")
            "chinese" -> Locale.CHINA
            "korean" -> Locale.KOREA
            "russian" -> Locale("ru", "RU")
            "arabic" -> Locale("ar", "SA")
            "hindi" -> Locale("hi", "IN")
            "swedish" -> Locale("sv", "SE")
            else -> Locale.ENGLISH
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "Language $language is not supported or missing data")
        }
    }

    /**
     * Pronounce translation using Android Text To Speech API
     */
    fun speakPronunciation(text: String) {
        val context = getApplication<Application>()
        if (tts == null) {
            ttsReady = false
            initTTS(context)
        }

        val speechText = text.substringBefore(" (") // strip kanji labels or brackets

        if (ttsReady && tts != null) {
            // Force-refresh selected language before speaking to guarantee correct pronunciation locale is active
            updateTTSLanguage(_selectedLanguage.value)

            val result = tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "LanguageSpeechID")
            if (result == TextToSpeech.ERROR) {
                Toast.makeText(context, "Speech play error. Refreshing audio module...", Toast.LENGTH_SHORT).show()
                ttsReady = false
                initTTS(context)
            }
        } else {
            Toast.makeText(context, "Audio speech module is waking up. Try again in a second!", Toast.LENGTH_SHORT).show()
            initTTS(context)
        }
    }

    /**
     * Delete language learned unit
     */
    fun deleteVocabulary(id: Int) {
        viewModelScope.launch {
            repository.deleteUnit(id)
        }
    }

    /**
     * Toggle item's bookmark state
     */
    fun toggleFavorite(unit: LanguageUnit) {
        viewModelScope.launch {
            repository.updateFavoriteStatus(unit.id, !unit.isFavorite)
        }
    }

    /**
     * Toggle item's learned flag
     */
    fun toggleLearned(unit: LanguageUnit) {
        viewModelScope.launch {
            val nextState = !unit.isLearned
            repository.updateLearnedStatus(unit.id, nextState)
            if (nextState) {
                addPoints(10)
                trackWordPractice(unit.id)
            }
        }
    }

    /**
     * Set a unit's learned status directly
     */
    fun setUnitLearnedStatus(id: Int, isLearned: Boolean) {
        viewModelScope.launch {
            repository.updateLearnedStatus(id, isLearned)
            if (isLearned) {
                trackWordPractice(id)
            }
        }
    }

    /**
     * Manage and track custom Daily Learning Goals
     */
    fun setDailyGoalTarget(target: Int) {
        _dailyGoalTarget.value = target
        sharedPrefs.edit().putInt("daily_goal_target", target).apply()
    }

    fun trackWordPractice(unitId: Int) {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Calendar.getInstance().time)
        val savedDate = sharedPrefs.getString("daily_goal_date", "") ?: ""
        
        val currentSet = if (savedDate == todayStr) {
            _practicedWordIdsToday.value.toMutableSet()
        } else {
            mutableSetOf()
        }
        
        // Always record activity for today on ANY practice tap, ensuring the streak checkmark gets checked immediately!
        recordActivity()

        if (currentSet.add(unitId)) {
            _practicedWordIdsToday.value = currentSet
            sharedPrefs.edit()
                .putString("daily_goal_date", todayStr)
                .putStringSet("daily_goal_practiced_ids", currentSet.map { it.toString() }.toSet())
                .apply()

            // Check Daily Goal Achievement!
            val target = _dailyGoalTarget.value
            if (currentSet.size >= target) {
                val rewardedDate = sharedPrefs.getString("daily_goal_rewarded_date", "") ?: ""
                if (rewardedDate != todayStr) {
                    sharedPrefs.edit().putString("daily_goal_rewarded_date", todayStr).apply()
                    // Award a premium +50 points bonus!
                    addPoints(50)
                }
            }
        }
    }

    fun trackWordPractice(unit: LanguageUnit) {
        trackWordPractice(unit.id)
    }

    /**
     * Generate custom words using Gemini API dynamic teaches
     */
    fun generateAIUnits(topicTopic: String) {
        if (topicTopic.isBlank()) return
        val language = _selectedLanguage.value
        _geminiLoading.value = true
        _geminiError.value = null

        viewModelScope.launch {
            try {
                if (GeminiClient.isKeyConfigured()) {
                    // Key is present, call actual Gemini endpoint!
                    val results = GeminiClient.generateCustomItems(language, topicTopic, _nativeLanguage.value)
                    val dbUnits = results.map { item ->
                        LanguageUnit(
                            language = language,
                            category = topicTopic.trim(), // Assign custom topic name
                            original = item.original,
                            ipa = item.ipa,
                            translation = item.translation,
                            exampleOriginal = item.exampleOriginal,
                            exampleTranslation = item.exampleTranslation
                        )
                    }
                    repository.insertAll(dbUnits)
                    _selectedCategory.value = "Custom"
                } else {
                    // API key is missing. Provide top-tier offline fallback items to show a polished simulation!
                    val fallbackItems = getSimulatedLessons(language, topicTopic)
                    repository.insertAll(fallbackItems)
                    _selectedCategory.value = "Custom"
                    _geminiError.value = "Using offline fallback! (Configure GEMINI_API_KEY in Secrets screen to generate live content)"
                }
            } catch (e: Exception) {
                _geminiError.value = "Failed to load: ${e.localizedMessage}. Using offline backup instead!"
                // Insert fallback items on exception so the system feels incredibly responsive and operational
                val fallbackItems = getSimulatedLessons(language, topicTopic)
                repository.insertAll(fallbackItems)
                _selectedCategory.value = "Custom"
            } finally {
                _geminiLoading.value = false
            }
        }
    }

    fun clearGeminiError() {
        _geminiError.value = null
    }

    // === QUIZ ENGINE IMPLEMENTATION ===

    /**
     * Establish and host interactive customized quizzes
     */
    fun startQuiz() {
        val pool = allUnits.value
        if (pool.size < 4) {
            // Cannot build valid 4-option matching quiz. Provide emergency fallback prepopulations on the fly!
            viewModelScope.launch {
                val fallbacks = getPrepopulatedWords().filter { it.language.equals(_selectedLanguage.value, ignoreCase = true) }
                repository.insertAll(fallbacks)
                // Resume quiz generation next loop
            }
            return
        }

        val questionsList = mutableListOf<QuizQuestion>()
        val shuffledPool = pool.shuffled()
        val totalQuestions = minOf(5, shuffledPool.size)

        for (i in 0 until totalQuestions) {
            val correctUnit = shuffledPool[i]
            
            // Collect distractors
            val otherUnits = pool.filter { it.id != correctUnit.id }.shuffled()
            val distractors = otherUnits.take(3)
            
            val options = (distractors + correctUnit).shuffled()
            val correctIndex = options.indexOf(correctUnit)

            // Dynamic question style: (0: Translate Spanish to English, 1: Translate English to Spanish)
            val style = if (Math.random() > 0.5) QuestionStyle.FOREIGN_TO_NATIVE else QuestionStyle.NATIVE_TO_FOREIGN

            val questionText = if (style == QuestionStyle.FOREIGN_TO_NATIVE) {
                "What is the English translation for \"${correctUnit.original}\"?"
            } else {
                "Select the correct \"${correctUnit.language}\" translation for \"${correctUnit.translation}\"."
            }

            val questionOptions = options.map { option ->
                if (style == QuestionStyle.FOREIGN_TO_NATIVE) option.translation else option.original
            }

            questionsList.add(
                QuizQuestion(
                    question = questionText,
                    originalWord = correctUnit.original,
                    translationWord = correctUnit.translation,
                    ipa = correctUnit.ipa,
                    options = questionOptions,
                    correctOptionIndex = correctIndex
                )
            )
        }

        _quizQuestions.value = questionsList
        _currentQuestionIndex.value = 0
        _quizScore.value = 0
        _answeredIndex.value = null
        _quizCompleted.value = false
        _quizActive.value = true
    }

    fun answerQuestion(index: Int) {
        if (_answeredIndex.value != null) return // already answered
        _answeredIndex.value = index
        
        val question = _quizQuestions.value.getOrNull(_currentQuestionIndex.value) ?: return
        if (index == question.correctOptionIndex) {
            _quizScore.value += 1
            addPoints(20) // Reward points for correct answers!
            // Also trigger speech audio pronunciation on success
            speakPronunciation(question.originalWord)
        }
    }

    fun nextQuestion() {
        _answeredIndex.value = null
        val nextIdx = _currentQuestionIndex.value + 1
        if (nextIdx < _quizQuestions.value.size) {
            _currentQuestionIndex.value = nextIdx
        } else {
            // Quiz completed! Save user progress to historical Room Database scores!
            _quizCompleted.value = true
            addPoints(100) // Reward huge completion points bonus!
            
            val scoreVal = _quizScore.value
            val totalQuestionsVal = _quizQuestions.value.size
            if (scoreVal == totalQuestionsVal && totalQuestionsVal > 0) {
                // Perfect Quiz Score milestone unlock!
                val currentBadges = _unlockedBadges.value.toMutableSet()
                if (!currentBadges.contains("quiz_conqueror")) {
                    currentBadges.add("quiz_conqueror")
                    _unlockedBadges.value = currentBadges
                    sharedPrefs.edit().putStringSet("unlocked_badges", currentBadges).apply()
                }
            }
            
            viewModelScope.launch {
                val scoreEntity = QuizScore(
                    language = _selectedLanguage.value,
                    category = _selectedCategory.value,
                    score = scoreVal,
                    totalQuestions = totalQuestionsVal
                )
                repository.insertScore(scoreEntity)
            }
        }
    }

    fun exitQuiz() {
        _quizActive.value = false
        _quizCompleted.value = false
        _answeredIndex.value = null
    }

    // === GAMIFICATION SYSTEM ACTIONS ===

    fun addPoints(amount: Int) {
        val newPoints = _userPoints.value + amount
        _userPoints.value = newPoints
        sharedPrefs.edit().putInt("points", newPoints).apply()
        recordActivity() // Automatically keep streak updated on points gain!
        // Force evaluation check
        viewModelScope.launch {
            val list = repository.languageUnitDao.getAllUnits().firstOrNull() ?: emptyList()
            val learnedCount = list.count { it.isLearned }
            val languagesUsed = list.filter { it.isLearned || it.isFavorite }.map { it.language.lowercase() }.distinct().size
            evaluateMilestones(learnedCount, languagesUsed)
        }
    }

    private fun evaluateMilestones(learnedCount: Int, languagesCount: Int) {
        val currentBadges = _unlockedBadges.value.toMutableSet()
        
        // Milestone conditions
        if (_userPoints.value > 0 && !currentBadges.contains("first_steps")) {
            currentBadges.add("first_steps")
        }
        if (learnedCount >= 5 && !currentBadges.contains("word_collector")) {
            currentBadges.add("word_collector")
        }
        if (learnedCount >= 10 && !currentBadges.contains("vocab_virtuoso")) {
            currentBadges.add("vocab_virtuoso")
        }
        if (languagesCount >= 2 && !currentBadges.contains("polyglot_pioneer")) {
            currentBadges.add("polyglot_pioneer")
        }
        if (_userPoints.value >= 500 && !currentBadges.contains("centurion_scholar")) {
            currentBadges.add("centurion_scholar")
        }
        
        // Daily Champion Badge check
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Calendar.getInstance().time)
        val rewardedDate = sharedPrefs.getString("daily_goal_rewarded_date", "") ?: ""
        if (rewardedDate == todayStr && !currentBadges.contains("daily_champion")) {
            currentBadges.add("daily_champion")
        }
        
        if (currentBadges.size != _unlockedBadges.value.size) {
            _unlockedBadges.value = currentBadges
            sharedPrefs.edit().putStringSet("unlocked_badges", currentBadges).apply()
        }
    }

    // === SPEECH RECOGNITION COMPARATIVE SYSTEM ===

    fun selectUnitForSpeaking(unit: LanguageUnit) {
        currentPracticedUnit = unit
        _speechMatchResult.value = null
    }

    fun startListening(unit: LanguageUnit) {
        currentPracticedUnit = unit
        startListening(unit.original)
    }

    fun startListening(correctWord: String) {
        _isListening.value = true
        _speechMatchResult.value = null
        
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _speechRecognitionAvailable.value = false
            triggerSimulatedSpeech(correctWord, "random")
            return
        }
        
        _speechRecognitionAvailable.value = true
        try {
            if (recognizer != null) {
                recognizer?.destroy()
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        val errorText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched. Try pronouncing it slowly and clearly."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out. We did not hear anything. Please try again!"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Tap the button and try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission is required for microphone input."
                            11 -> "This language model is not fully supported for offline voice typing on your device's current engine. Feel free to use the self-assessment helper below!"
                            else -> "Could not process speaking audio (error code $error). Try speaking closer to the microphone."
                        }
                        _speechMatchResult.value = SpeechMatchResult(
                            textSpoken = "",
                            accuracy = 0,
                            feedback = errorText,
                            pointsAwarded = 0
                        )
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            // Find the match with the highest similarity score
                            var bestMatch = matches[0]
                            var highestScore = -1
                            for (match in matches) {
                                val cleanSpoken = match.lowercase(Locale.ROOT).trim().replace("[^\\p{L}\\p{N}\\s]".toRegex(), "").replace("\\s+".toRegex(), " ")
                                val cleanCorrect = correctWord.substringBefore(" (").lowercase(Locale.ROOT).trim().replace("[^\\p{L}\\p{N}\\s]".toRegex(), "").replace("\\s+".toRegex(), " ")
                                val score = calculateSimilarity(cleanSpoken, cleanCorrect)
                                if (score > highestScore) {
                                    highestScore = score
                                    bestMatch = match
                                }
                            }
                            processSpeechMatch(bestMatch, correctWord)
                        } else {
                            _speechMatchResult.value = SpeechMatchResult(
                                textSpoken = "",
                                accuracy = 0,
                                feedback = "No clear words were detected. Please pronounce clearly after the audio prompt.",
                                pointsAwarded = 0
                            )
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val word = matches?.getOrNull(0) ?: ""
                        if (word.isNotBlank()) {
                            _speechMatchResult.value = SpeechMatchResult(
                                textSpoken = word,
                                accuracy = 0,
                                feedback = "Hearing your voice: \"$word\"... Speak clearly!",
                                pointsAwarded = 0
                            )
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langCode = when (_selectedLanguage.value.lowercase(Locale.ROOT)) {
                    "spanish" -> "es-ES"
                    "french" -> "fr-FR"
                    "japanese" -> "ja-JP"
                    "german" -> "de-DE"
                    "italian" -> "it-IT"
                    "portuguese" -> "pt-PT"
                    "chinese" -> "zh-CN"
                    "korean" -> "ko-KR"
                    "russian" -> "ru-RU"
                    "arabic" -> "ar-SA"
                    "hindi" -> "hi-IN"
                    "swedish" -> "sv-SE"
                    else -> "en-US"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                
                // Web Speech API continuous & interimResults configuration mapping:
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Enable interim/partial results (interimResults = true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L) // Wait at least 2s for continuous entry
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L) // Allow reasonable user pauses without early cutoff
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L) // Wait for natural speech pauses
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _speechMatchResult.value = SpeechMatchResult(
                textSpoken = "",
                accuracy = 0,
                feedback = "Microphone error: ${e.localizedMessage}. Please try again later.",
                pointsAwarded = 0
            )
        }
    }

    fun stopListening() {
        _isListening.value = false
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {}
    }

    fun setManualSpeechResult(accuracy: Int, spokenText: String, correctText: String) {
        val feedback = when {
            accuracy >= 90 -> "Incredible self-grading! Native-like fluency! 🌟"
            accuracy >= 75 -> "Excellent! Very close, minor accent details. 👍"
            accuracy >= 50 -> "Good effort. Try focusing on individual sounds. 🗣️"
            else -> "Keep practicing! Tap the native speaker sound guide to listen."
        }
        val pts = when {
            accuracy >= 90 -> 15
            accuracy >= 75 -> 10
            accuracy >= 50 -> 5
            else -> 0
        }
        _speechMatchResult.value = SpeechMatchResult(
            textSpoken = spokenText,
            accuracy = accuracy,
            feedback = feedback,
            pointsAwarded = pts
        )
        if (pts > 0) {
            addPoints(pts)
        } else {
            recordActivity()
        }

        // Log speaking practice
        val unit = currentPracticedUnit
        if (unit != null) {
            trackWordPractice(unit.id)
            viewModelScope.launch {
                val log = SpeakingPracticeLog(
                    unitId = unit.id,
                    original = unit.original,
                    language = unit.language,
                    translation = unit.translation,
                    accuracy = accuracy,
                    pointsEarned = pts
                )
                repository.insertSpeakingLog(log)
            }
        }

        val nextAttempts = _speechAttempts.value + 1
        _speechAttempts.value = nextAttempts
        sharedPrefs.edit().putInt("speech_attempts", nextAttempts).apply()
    }

    fun triggerSimulatedSpeech(correctWord: String, type: String = "random") {
        viewModelScope.launch {
            _isListening.value = true
            kotlinx.coroutines.delay(1000) // Simulates speech recognition processing
            _isListening.value = false
            
            val cleanCorrect = correctWord.substringBefore(" (").trim()
            val (spokenWord, simulatedScore) = when (type) {
                "fluent" -> Pair(cleanCorrect, 98)
                "accent" -> Pair(cleanCorrect.take(maxOf(1, cleanCorrect.length - 2)) + " accented", 78)
                "poor" -> Pair("Mumbled " + cleanCorrect.take(2), 35)
                "silent" -> Pair("", 0)
                else -> {
                    // Random mix behavior
                    val opts = listOf(
                        Pair(cleanCorrect, 100),
                        Pair(cleanCorrect + " (with pause)", 88),
                        Pair(cleanCorrect.take(maxOf(1, cleanCorrect.length - 2)) + "..", 65),
                        Pair("Wrong speech", 18)
                    )
                    opts.random()
                }
            }
            
            if (spokenWord.isBlank()) {
                _speechMatchResult.value = SpeechMatchResult(
                    textSpoken = "",
                    accuracy = 0,
                    feedback = "Silence detected in simulation. Try speaking clearly!",
                    pointsAwarded = 0
                )
            } else {
                processSpeechMatch(spokenWord, correctWord)
            }
        }
    }

    fun processSpeechMatch(spokenWord: String, correctWord: String) {
        val cleanSpoken = spokenWord.lowercase(Locale.ROOT).trim().replace("[^\\p{L}\\p{N}\\s]".toRegex(), "").replace("\\s+".toRegex(), " ")
        val cleanCorrect = correctWord.substringBefore(" (").lowercase(Locale.ROOT).trim().replace("[^\\p{L}\\p{N}\\s]".toRegex(), "").replace("\\s+".toRegex(), " ")
        
        val score = calculateSimilarity(cleanSpoken, cleanCorrect)
        
        val feedback = when {
            score >= 90 -> "Incredible! PERFECT pronunciation! 🌟"
            score >= 75 -> "Excellent! Very close, just minor accent details. 👍"
            score >= 50 -> "Good effort! Try to focus on phonetic elements. 🗣️"
            else -> "Keep practicing! Tap the Speaker to listen to native voice and try again."
        }
        
        val pts = when {
            score >= 90 -> 15
            score >= 75 -> 10
            score >= 50 -> 5
            else -> 0
        }
        
        _speechMatchResult.value = SpeechMatchResult(
            textSpoken = spokenWord,
            accuracy = score,
            feedback = feedback,
            pointsAwarded = pts
        )
        
        if (pts > 0) {
            addPoints(pts)
        } else {
            recordActivity()
        }

        // Log speaking practice
        val unit = currentPracticedUnit
        if (unit != null) {
            trackWordPractice(unit.id)
            viewModelScope.launch {
                val log = SpeakingPracticeLog(
                    unitId = unit.id,
                    original = unit.original,
                    language = unit.language,
                    translation = unit.translation,
                    accuracy = score,
                    pointsEarned = pts
                )
                repository.insertSpeakingLog(log)
            }
        }
        
        // Handle speaker attempt milestone
        val nextAttempts = _speechAttempts.value + 1
        _speechAttempts.value = nextAttempts
        sharedPrefs.edit().putInt("speech_attempts", nextAttempts).apply()
        
        if (nextAttempts >= 3) {
            val currentBadges = _unlockedBadges.value.toMutableSet()
            if (!currentBadges.contains("speaking_sensation")) {
                currentBadges.add("speaking_sensation")
                _unlockedBadges.value = currentBadges
                sharedPrefs.edit().putStringSet("unlocked_badges", currentBadges).apply()
            }
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Int {
        if (s1.isEmpty() || s2.isEmpty()) return 0
        if (s1 == s2) return 100
        
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        val ratio = 1.0 - (distance.toDouble() / maxLen.toDouble())
        return (ratio * 100).toInt()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, minOf(costDelete, costReplace))
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    override fun onCleared() {
        super.onCleared()
        try {
            recognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up TTS", e)
        }
    }

    /**
     * Beautiful Simulated offline categories when key is not registered
     */
    private fun getSimulatedLessons(lang: String, topic: String): List<LanguageUnit> {
        val topicLower = topic.lowercase(Locale.ROOT)
        return if (topicLower.contains("airport") || topicLower.contains("travel") || topicLower.contains("flight")) {
            listOf(
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "El pasaporte"
                    "French" -> "Le passeport"
                    "Japanese" -> "パスポート (Pasupooto)"
                    "German" -> "Der Reisepass"
                    "Italian" -> "Il passaporto"
                    "Portuguese" -> "O passaporte"
                    "Chinese" -> "护照 (Hùzhào)"
                    "Korean" -> "여권 (Yeogwon)"
                    "Russian" -> "Паспорт (Pasport)"
                    "Arabic" -> "جواز السفر (Jawaz as-safar)"
                    "Hindi" -> "पासपोर्ट (Passport)"
                    "Swedish" -> "Passet"
                    else -> "Passport"
                }, ipa = "pahs-ah-pohr-teh", translation = "The passport", exampleOriginal = "Passport control.", exampleTranslation = "Passport control."),
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "El vuelo"
                    "French" -> "Le vol"
                    "Japanese" -> "フライト (Furaito)"
                    "German" -> "Der Flug"
                    "Italian" -> "Il volo"
                    "Portuguese" -> "O voo"
                    "Chinese" -> "航班 (Hángbān)"
                    "Korean" -> "비행 (Bihaeng)"
                    "Russian" -> "Рейс (Reys)"
                    "Arabic" -> "الرحلة (Ar-rihlah)"
                    "Hindi" -> "उड़ान (Udaan)"
                    "Swedish" -> "Flyget"
                    else -> "Flight"
                }, ipa = "bweh-loh", translation = "The flight", exampleOriginal = "A good flight.", exampleTranslation = "A good flight."),
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "La maleta"
                    "French" -> "La valise"
                    "Japanese" -> "スーツケース (Suutsukeesu)"
                    "German" -> "Der Koffer"
                    "Italian" -> "La valigia"
                    "Portuguese" -> "A mala"
                    "Chinese" -> "行李箱 (Xínglǐxiāng)"
                    "Korean" -> "가방 (Gabang)"
                    "Russian" -> "Чемодан (Chemodan)"
                    "Arabic" -> "الحقيبة (Al-haqibah)"
                    "Hindi" -> "सामान (Saamaan)"
                    "Swedish" -> "Resväskan"
                    else -> "Luggage"
                }, ipa = "mah-leh-tah", translation = "The suitcase", exampleOriginal = "My suitcase.", exampleTranslation = "My suitcase.")
            )
        } else if (topicLower.contains("eat") || topicLower.contains("food") || topicLower.contains("restaurant") || topicLower.contains("cook")) {
            listOf(
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "La cuenta, por favor"
                    "French" -> "L'addition, s'il vous plaît"
                    "Japanese" -> "お会計お願いします (Okaikei onegai shimasu)"
                    "German" -> "Die Rechnung, bitte"
                    "Italian" -> "Il conto, per favore"
                    "Portuguese" -> "A conta, por favor"
                    "Chinese" -> "买单 (Mǎidān)"
                    "Korean" -> "계산서 주세요 (Gyesanseo juseyo)"
                    "Russian" -> "Счет, пожалуйста (Schet, pozhaluysta)"
                    "Arabic" -> "الحساب من فضلك (Al-hisab min fadlak)"
                    "Hindi" -> "कृपया बिल लाइए (Kripya bill laiye)"
                    "Swedish" -> "Notan, tack"
                    else -> "The bill, please"
                }, ipa = "koohn-tah", translation = "The bill, please", exampleOriginal = "Waiter, the bill.", exampleTranslation = "Waiter, the bill."),
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "Delicioso"
                    "French" -> "Délicieux"
                    "Japanese" -> "美味しい (Oishii)"
                    "German" -> "Köstlich"
                    "Italian" -> "Delizioso"
                    "Portuguese" -> "Delicioso"
                    "Chinese" -> "美味 (Měiwèi)"
                    "Korean" -> "맛있어요 (Masisseoyo)"
                    "Russian" -> "Вкусно (Vkusno)"
                    "Arabic" -> "لذيذ (Ladhidh)"
                    "Hindi" -> "स्वादिष्ट (Svaadist)"
                    "Swedish" -> "Mumsigt"
                    else -> "Delicious"
                }, ipa = "deh-lee-syoh-soh", translation = "Delicious", exampleOriginal = "Very delicious.", exampleTranslation = "Very delicious.")
            )
        } else {
            listOf(
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "Aprender"
                    "French" -> "Apprendre"
                    "Japanese" -> "学ぶ (Manabu)"
                    "German" -> "Lernen"
                    "Italian" -> "Imparare"
                    "Portuguese" -> "Aprender"
                    "Chinese" -> "学习 (Xuéxí)"
                    "Korean" -> "배우다 (Baeuda)"
                    "Russian" -> "Учиться (Uchitsya)"
                    "Arabic" -> "تعلم (Ta'allum)"
                    "Hindi" -> "सीखना (Seekhna)"
                    "Swedish" -> "Lära"
                    else -> "Learn"
                }, ipa = "ah-prehn-dehr", translation = "To learn", exampleOriginal = "I love to learn.", exampleTranslation = "I love to learn."),
                LanguageUnit(language = lang, category = topic, original = when (lang) {
                    "Spanish" -> "Éxito"
                    "French" -> "Le succès"
                    "Japanese" -> "成功 (Seikou)"
                    "German" -> "Der Erfolg"
                    "Italian" -> "Successo"
                    "Portuguese" -> "Sucesso"
                    "Chinese" -> "成功 (Chénggōng)"
                    "Korean" -> "성공 (Seonggong)"
                    "Russian" -> "Успех (Uspekh)"
                    "Arabic" -> "نجاح (Najah)"
                    "Hindi" -> "सफलता (Safalta)"
                    "Swedish" -> "Framgång"
                    else -> "Success"
                }, ipa = "ehk-see-toh", translation = "Success", exampleOriginal = "Much success.", exampleTranslation = "Much success.")
            )
        }
    }

    // === DEVELOPER & STORAGE INSPECTOR ACTIONS ===

    fun debugUpdatePoints(points: Int) {
        _userPoints.value = points.coerceAtLeast(0)
        sharedPrefs.edit().putInt("points", _userPoints.value).apply()
    }

    fun debugUpdateStreak(streak: Int) {
        _userStreak.value = streak.coerceAtLeast(0)
        sharedPrefs.edit().putInt("user_streak", _userStreak.value).apply()
    }

    fun debugUpdateLastActiveDate(dateStr: String) {
        _lastActiveDate.value = dateStr
        sharedPrefs.edit().putString("last_active_date", dateStr).apply()
    }

    fun debugUpdateSpeechAttempts(attempts: Int) {
        _speechAttempts.value = attempts.coerceAtLeast(0)
        sharedPrefs.edit().putInt("speech_attempts", _speechAttempts.value).apply()
    }

    fun debugUpdateUserName(name: String) {
        _userName.value = name
        sharedPrefs.edit().putString("user_name", name).apply()
    }

    fun debugResetSetupCompleted() {
        _setupCompleted.value = false
        sharedPrefs.edit().putBoolean("setup_completed", false).apply()
    }

    fun debugToggleActivityDate(date: String) {
        val currentDates = _activeDates.value.toMutableSet()
        if (currentDates.contains(date)) {
            currentDates.remove(date)
        } else {
            currentDates.add(date)
        }
        _activeDates.value = currentDates
        sharedPrefs.edit().putStringSet("completed_activity_dates", currentDates).apply()
    }

    fun debugClearQuizHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.quizScoreDao.clearAllScores()
        }
    }

    fun debugClearSpeakingLogs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.speakingPracticeDao.clearAllLogs()
        }
    }

    fun debugMarkAllLearned() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val allVocab = repository.languageUnitDao.getAllUnits().firstOrNull() ?: emptyList()
            val resetVocab = allVocab.map { it.copy(isLearned = true) }
            repository.languageUnitDao.insertAll(resetVocab)
        }
    }

    fun debugResetVocabProgress() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val allVocab = repository.languageUnitDao.getAllUnits().firstOrNull() ?: emptyList()
            val resetVocab = allVocab.map { it.copy(isLearned = false) }
            repository.languageUnitDao.insertAll(resetVocab)
        }
    }

    fun debugResetAllStorage() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Clear SharedPreferences
            sharedPrefs.edit().clear().apply()
            
            // Re-populate default values in state flows
            _userPoints.value = 120
            _userStreak.value = 12
            _userName.value = "Scholar"
            _nativeLanguage.value = "English"
            _selectedLanguage.value = "Spanish"
            _themeMode.value = "system"
            _setupCompleted.value = false
            _lastActiveDate.value = ""
            _speechAttempts.value = 0
            _unlockedBadges.value = emptySet()
            _activeDates.value = emptySet()
            _dailyGoalTarget.value = 5
            
            // Delete and rebuild DB or truncate tables
            repository.quizScoreDao.clearAllScores()
            repository.speakingPracticeDao.clearAllLogs()
            
            // Reset learned/favorite status on all vocabulary words
            val allVocab = repository.languageUnitDao.getAllUnits().firstOrNull() ?: emptyList()
            val resetVocab = allVocab.map { it.copy(isLearned = false, isFavorite = false) }
            repository.languageUnitDao.insertAll(resetVocab)
            
            // Pre-populate historical active dates to align with the default welcoming 12-day streak
            val initialDates = mutableSetOf<String>()
            val cal = java.util.Calendar.getInstance()
            for (i in 1..12) {
                val backCal = java.util.Calendar.getInstance()
                backCal.add(java.util.Calendar.DATE, -i)
                initialDates.add(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(backCal.time))
            }
            sharedPrefs.edit().putStringSet("completed_activity_dates", initialDates).apply()
            _activeDates.value = initialDates
            
            val yesterdayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time.apply { cal.add(java.util.Calendar.DATE, -1) })
            _lastActiveDate.value = yesterdayStr
            sharedPrefs.edit().putString("last_active_date", yesterdayStr).apply()
        }
    }
}

enum class QuestionStyle {
    FOREIGN_TO_NATIVE,
    NATIVE_TO_FOREIGN
}

data class QuizQuestion(
    val question: String,
    val originalWord: String,
    val translationWord: String,
    val ipa: String,
    val options: List<String>,
    val correctOptionIndex: Int
)

fun getNativeTranslation(originalTranslation: String, nativeLang: String): String {
    if (nativeLang.equals("English", ignoreCase = true)) return originalTranslation
    val map = mapOf(
        "Apple" to mapOf("Telugu" to "యాపిల్ పండు (Apple)", "Hindi" to "सेब (Seb)", "Malayalam" to "ആപ്പിൾ (Apple)", "Tamil" to "ஆப்பிள் (Apple)", "Kannada" to "ಸೇಬು (Sebu)", "Spanish" to "Manzana", "French" to "Pomme", "German" to "Apfel", "Japanese" to "林檎 (Ringo)"),
        "I like eating a red apple." to mapOf("Telugu" to "నాకు ఎర్రటి యాపిల్ పండు తినడం ఇష్టం.", "Hindi" to "मुझे लाल सेब खाना पसंद है।", "Malayalam" to "എനിക്ക് చുവന്ന ఆപ്പിൾ കഴിക്കാൻ ഇഷ്ടമാണ്.", "Tamil" to "எனக்கு சிவப்பு ఆப்பிள் சாப்பிட பிடிக்கும்.", "Kannada" to "ನನಗೆ ಕೆಂಪು ಸೇಬು ತಿನ್ನಲು ಇಷ್ಟ.", "Spanish" to "Me gusta comer una manzana roja.", "French" to "J'aime manger une pomme rouge.", "German" to "Ich esse gerne einen roten Apfel."),
        "Thank you" to mapOf("Telugu" to "ధన్యవాదాలు (Dhanyavadalu)", "Hindi" to "धन्यवाद (Dhanyavaad)", "Malayalam" to "നന്ദി (Nandi)", "Tamil" to "நன்றி (Nandri)", "Kannada" to "ಧನ್ಯವಾದಗಳು (Dhanyavadagalu)", "Spanish" to "Gracias", "French" to "Merci", "German" to "Danke", "Japanese" to "ありがとう (Arigatou)"),
        "Thank you very much for your help." to mapOf("Telugu" to "మీ సహాయానికి చాలా ధన్యవాదాలు.", "Hindi" to "आपकी मदद के लिए बहुत-बहुत धन्यवाद।", "Malayalam" to "നിങ്ങളുടെ സഹായത്തിന് വളരെ നന്ദി.", "Tamil" to "உங்கள் உதவிக்கு மிக்க நன்றி.", "Kannada" to "ನಿಮ್ಮ సహాయಕ್ಕೆ ತುಂಬಾ ಧನ್ಯವಾದಗಳು.", "Spanish" to "Muchas gracias por tu ayuda.", "French" to "Merci beaucoup pour votre aide.", "German" to "Vielen Dank für Ihre Hilfe."),
        "Dog" to mapOf("Telugu" to "కుక్క (Kukka)", "Hindi" to "कुत्ता (Kutta)", "Malayalam" to "നായ (Naya)", "Tamil" to "நாய் (Naai)", "Kannada" to "ನಾಯಿ (Naayi)", "Spanish" to "Perro", "French" to "Chien", "German" to "Hund", "Japanese" to "犬 (Inu)"),
        "The dog runs in the park." to mapOf("Telugu" to "కుక్క పార్కులో పరుగెత్తుతోంది.", "Hindi" to "कुत्ता पार्क में दौड़ता है।", "Malayalam" to "നായ പാർക്കിൽ ഓടുന്നു.", "Tamil" to "நாய் பூங்காவில் ஓடுகிறது.", "Kannada" to "ನಾಯಿ ಪಾರ್ಕಿನಲ್ಲಿ ಓಡುತ್ತದೆ.", "Spanish" to "El perro corre en el parque.", "French" to "Le chien court dans le parc.", "German" to "Der Hund läuft im Park."),
        "Where is the bathroom?" to mapOf("Telugu" to "టాయిలెట్/బాత్‌రూమ్ ఎక్కడ ఉంది?", "Hindi" to "शौचालय कहाँ है?", "Malayalam" to "ബാത്ത്റൂം എവിടെയാണ്?", "Tamil" to "கழிப்பறை எங்கே இருக்கிறது?", "Kannada" to "ಬಾತ್ ರೂಮ್ ಎಲ್ಲಿದೆ?", "Spanish" to "¿Dónde está el baño?", "French" to "Où sont les toilettes ?", "German" to "Wo ist die Toilette?", "Japanese" to "お手洗いはどこですか (Otearai wa doko desu ka)"),
        "Excuse me sir, where is the bathroom?" to mapOf("Telugu" to "క్షమించండి సార్, టాయిలెట్ ఎక్కడ ఉంది?", "Hindi" to "क्षमा करें महोदय, शौचालय कहाँ है?", "Malayalam" to "ക്ഷമിക്കണം സാർ, ബാത്ത്റൂം എവിടെയാണ്?", "Tamil" to "மன்னிக்கவும் ஐயா, கழிப்பறை எங்கே இருக்கிறது?", "Kannada" to "ಕ್ಷಮಿಸಿ ಸರ್, ಬಾತ್ ರೂಮ್ ಎಲ್ಲಿದೆ?", "Spanish" to "Disculpe señor, ¿Dónde está el baño?", "French" to "Excusez-moi monsieur, où sont les toilettes ?", "German" to "Entschuldigung, mein Herr, wo ist die Toilette?"),
        "Nice to meet you" to mapOf("Telugu" to "మిమ్మల్ని కలవడం సంతోషంగా ఉంది", "Hindi" to "आपसे मिलकर अच्छा लगा", "Malayalam" to "കണ്ടുമുട്ടിയതിൽ സന്തോഷം", "Tamil" to "உங்களை சந்தித்ததில் மகிழ்ச்சி", "Kannada" to "మిమ్మల్ని భేటీ చేయడం సంతోషం", "Spanish" to "Mucho gusto", "French" to "Enchanté", "German" to "Freut mich, Sie kennenzulernen", "Japanese" to "はじめまして (Hajimemashite)"),
        "Hello, I am Carlos. Nice to meet you!" to mapOf("Telugu" to "హలో, నేను కార్లోస్. మిమ్మల్ని కలవడం సంతోషం!", "Hindi" to "नमस्ते, मैं कार्लोस हूँ। आपसे मिलकर अच्छा लगा!", "Malayalam" to "ஹലോ, ഞാൻ കാർലോസ്. കണ്ടുമുട്ടിയതിൽ സന്തോഷം!", "Tamil" to "ஹलो, நான் கார்லோஸ். உங்களை சந்தித்ததில் மகிழ்ச்சி!", "Kannada" to "ಹಲೋ, ನಾನು ಕಾರ್ಲೋಸ್. ಭೇಟಿಯಾಗಿದ್ದಕ್ಕೆ ಸಂತೋಷ!", "Spanish" to "Hola, soy Carlos. ¡Mucho gusto!", "French" to "Bonjour, je m'appelle Carlos. Enchanté !", "German" to "Hallo, ich bin Carlos. Freut mich!"),
        "To be (Permanent vs Temporary)" to mapOf("Telugu" to "ఉండుట (శాశ్వత vs తాత్కాలిక)", "Hindi" to "होना (स्थायी बनाम अस्थायी)", "Malayalam" to "ആയിരിക്കുക (സ്ഥിരം vs താൽക്കാലിക)", "Tamil" to "இருத்தல் (நிலையான vs தற்காலிக)", "Kannada" to "ಇರುವುದು (ಶಾಶ್ವತ vs ತಾತ್ಕಾಲಿಕ)", "Spanish" to "Ser vs Estar", "French" to "Être (permanent vs temporaire)", "German" to "Sein (dauerhaft vs vorübergehend)"),
        "I am a student. I am happy." to mapOf("Telugu" to "నేను విద్యార్థిని (శాశ్వతం). నేను సంతోషంగా ఉన్నాను (తాత్కాలికం).", "Hindi" to "मैं एक छात्र हूँ। मैं खुश हूँ।", "Malayalam" to "ഞാൻ ഒരു വിദ്യാർത്ഥിയാണ്. ഞാൻ സന്തോഷവാനാണ്.", "Tamil" to "நான் ఒక மாணவன். நான் மகிழ்ச்சியாக இருக்கிறேன்.", "Kannada" to "ನಾನು ವಿದ್ಯಾರ್ಥಿ. ನಾನು ಸಂತೋಷವಾಗಿದ್ದೇನೆ.", "Spanish" to "Soy estudiante. Estoy feliz.", "French" to "Je suis étudiant. Je suis heureux.", "German" to "Ich bin Student. Ich bin glücklich."),
        // French
        "Hello / Good morning" to mapOf("Telugu" to "హలో / శుభోదయం", "Hindi" to "नमस्ते / सुप्रभात", "Malayalam" to "ஹलो / സുപ്രഭാതം", "Tamil" to "வணக்கம் / காலை வணக்கம்", "Kannada" to "ನಮಸ್ಕಾರ / ಸುಪ್ರಭಾತ", "Japanese" to "こんにちは / おはよう"),
        "Good morning, how are you?" to mapOf("Telugu" to "శుభోదయం, ఎలా ఉన్నారు?", "Hindi" to "सुप्रभात, आप कैसे हैं?", "Malayalam" to "സുപ്രഭാതം, സുഖമാണോ?", "Tamil" to "காலை வணக்கம், எப்படி இருக்கிறீர்கள்?", "Kannada" to "ಶುಭೋದಯ, ಹೇಗಿದ್ದೀರಾ?"),
        "Cat" to mapOf("Telugu" to "పిల్లి (Pilli)", "Hindi" to "बिल्ली (Billi)", "Malayalam" to "പൂച്ച (Poocha)", "Tamil" to "பூனை (Poonai)", "Kannada" to "ಬೆಕ್ಕು (Bekku)"),
        "The black cat is sleeping on the couch." to mapOf("Telugu" to "నల్ల పిల్లి సోఫా మీద పడుకుంది.", "Hindi" to "काली बिल्ली सोफे पर सो रही है।", "Malayalam" to "കറുത്ത പൂച്ച സോഫയിൽ ഉറങ്ങുന്നു.", "Tamil" to "கருப்பு പൂனை சோபாவில் தூங்குகிறது.", "Kannada" to "ಕಪ್ಪು ಬೆಕ್ಕು സോഫാദ മേലെ മലകിതെ."),
        "Water" to mapOf("Telugu" to "నీరు (Neeru)", "Hindi" to "पानी (Paani)", "Malayalam" to "വെള്ളം (Vellam)", "Tamil" to "தண்ணீர் (Thanneer)", "Kannada" to "ನೀರು (Neeru)"),
        "A glass of water, please." to mapOf("Telugu" to "దయచేసి ఒక గ్లాసు మంచి నీరు ఇవ్వండి.", "Hindi" to "कृपया एक गिलास पानी।", "Malayalam" to "ഒരു ഗ്ലാസ് വെള്ളം തരുമോ, ദയവായി.", "Tamil" to "தயவுசெய்து ஒரு ग्लास தண்ணீர்.", "Kannada" to "ದಯವಿಟ್ಟು ಒಂದು ಗ್ಲಾಸ್ ನೀರು ಕೊಡಿ."),
        "Please (formal)" to mapOf("Telugu" to "దయచేసి (మర్యాదపూర్వకంగా)", "Hindi" to "कृपया (औपचारिक)", "Malayalam" to "ദയവായി (ഔപചാരികം)", "Tamil" to "தயவுசெய்து", "Kannada" to "ದಯವಿಟ್ಟು"),
        "Repeat slowly, please." to mapOf("Telugu" to "దయచేసి నెమ్మదిగా పునరావృతం చేయండి.", "Hindi" to "कृपया धीरे से दोहराएं।", "Malayalam" to "ദയവായി സാവധാനം പറയൂ.", "Tamil" to "தயவுசெய்து மெதுவாக சொல்லுங்கள்.", "Kannada" to "ದಯವಿಟ್ಟು ನಿಧಾನವಾಗಿ ಹೇಳಿ."),
        "I love you" to mapOf("Telugu" to "నేను నిన్ను ప్రేమిస్తున్నాను", "Hindi" to "मैं तुमसे प्यार करता हूँ", "Malayalam" to "ഞാൻ నిന്നെ സ്നേഹിക്കുന്നു", "Tamil" to "நான் உன்னை காதலிக்கிறேன்", "Kannada" to "ನಾನು ನಿන්නನ್ನು ಪ್ರೀತಿಸುತ್ತೇನೆ"),
        "I love you with all my heart." to mapOf("Telugu" to "నేను నిన్ను హృదయపూర్వకంగా ప్రేమిస్తున్నాను.", "Hindi" to "मैं तुमसे पूरे दिल से प्यार करता हूँ।", "Malayalam" to "എന്റെ പൂർണ്ണ ഹൃദയത്തോടെ ഞാൻ നിന്നെ സ്നേഹിക്കുന്നു.", "Tamil" to "நான் என் முழு முழு மனதுடனும் உன்னை காதலிக்கிறேன்.", "Kannada" to "ನನ್ನ ಪೂರ್ಣ ಹೃದಯದಿಂದ ನಾನು ನಿನ್ನನ್ನು ಪ್ರೀತಿಸುತ್ತೇನೆ."),
        "Masculine 'the' (le) / Feminine 'the' (la)" to mapOf("Telugu" to "పురుషలింగ 'the' (le) / స్త్రీలింగ 'the' (la)", "Hindi" to "पुल्लिंग 'the' (le) / स्त्रीलिंग 'the' (la)", "Malayalam" to "पुല്ലിംഗം 'the' (le) / സ്ത്രീലിംഗം 'the' (la)", "Tamil" to "ஆண்பால் 'the' (le) / பெண்பால் 'the' (la)", "Kannada" to "ಪುಲ್ಲಿಂಗ 'the' (le) / ಸ್ತ್ರೀಲಿಂಗ 'the' (la)"),
        "The book, the table." to mapOf("Telugu" to "పుస్తకం, బల్ల.", "Hindi" to "पुस्तक, मेज।", "Malayalam" to "പുസ്തകം, മേശ.", "Tamil" to "புத்தகம், மேஜை.", "Kannada" to "ಪುಸ್ತಕ, ಮೇಜು."),
        
        // General common translations for other preseeded elements
        "Flower" to mapOf("Telugu" to "పువ్వు (Puvvu)", "Hindi" to "फूल (Phool)", "Malayalam" to "പൂവ് (Poovu)", "Tamil" to "மலர் (Malar)", "Kannada" to "ಹೂವು (Hoovu)"),
        "The cherry blossoms are beautiful in spring." to mapOf("Telugu" to "వసంతకాలంలో చెర్రీ పువ్వులు చాలా అందంగా ఉంటాయి.", "Hindi" to "वसंत ऋतु में चेरी के फूल सुंदर होते हैं।", "Malayalam" to "വസന്തകാലത്ത് ചെറി പൂക്കൾ മനോഹരമാണ്.", "Tamil" to "வசந்த காலத்தில் செர்ரி மலர்கள் அழகாக இருக்கும்.", "Kannada" to "ವಸಂತಕಾಲದಲ್ಲಿ ಚೆರ್ರಿ ಹೂವುಗಳು ಸುಂದರುತ್ತವೆ."),
        "Train" to mapOf("Telugu" to "రైలు (Railu)", "Hindi" to "रेलगाड़ी (Railgadi)", "Malayalam" to "തീവണ്ടി (Theevandi)", "Tamil" to "இரயில் (Rayil)", "Kannada" to "ರೈಲು (Railu)"),
        "I take the bullet train to Tokyo." to mapOf("Telugu" to "నేను టోక్యోకు బుల్లెట్ రైలులో వెళ్తాను.", "Hindi" to "मैं टोक्यो के लिए बुलेट ट्रेन लेता हूँ।", "Malayalam" to "ഞാൻ ടോക്കിയോയിലേക്ക് ബുള്ളറ്റ് ട്രെയിൻ എടുക്കുന്നു.", "Tamil" to "நான் டோக்கியோவிற்கு புல்லட் ரயில் எடுக்கிறேன்.", "Kannada" to "ನಾನು ಟೋಕಿಯೊಗೆ ಬುಲೆಟ್ ರೈಲು ಹತ್ತುತ್ತೇನೆ."),
        "Where" to mapOf("Telugu" to "ఎక్కడ", "Hindi" to "कहाँ", "Malayalam" to "എവിടെ", "Tamil" to "எங்கே", "Kannada" to "ಎಲ್ಲಿದೆ"),
        "Where is the train station?" to mapOf("Telugu" to "రైల్వే స్టేషన్ ఎక్కడ ఉంది?", "Hindi" to "रेलवे स्टेशन कहाँ है?", "Malayalam" to "റെയിൽവേ സ്റ്റേഷൻ എവിടെയാണ്?", "Tamil" to "இரயில் நிலையம் எங்கே இருக்கிறது?", "Kannada" to "ರೈಲ್ವೇ ಸ್ಟೇಷನ್ ಎಲ್ಲಿದೆ?"),
        "Excuse me" to mapOf("Telugu" to "నన్ను క్షమించండి", "Hindi" to "माफ़ कीजिये", "Malayalam" to "exception", "Tamil" to "மன்னிக்கவும்", "Kannada" to "ಕ್ಷಮಿಸಿ"),
        "Excuse me, do you speak English?" to mapOf("Telugu" to "క్షమించండి, మీరు ఇంగ్లీష్ మాట్లాడగలరా?", "Hindi" to "माफ़ कीजिये, क्या आप अंग्रेजी बोलते हैं?", "Malayalam" to "ക്ഷമിക്കണം, നിങ്ങൾ ഇംഗ്ലീഷ് സംസാരിക്കുമോ?", "Tamil" to "மன்னிக்கவும், நீங்கள் ஆங்கிலம் பேசுவீர்களா?", "Kannada" to "ಕ್ಷಮಿಸಿ, ನೀವು ಇಂಗ್ಲಿಷ್ ಮಾತನಾಡುತ್ತೀರಾ?"),
        "Book" to mapOf("Telugu" to "పుస్తకం (Pustakam)", "Hindi" to "पुस्तक (Pustak)", "Malayalam" to "പുസ്തകം (Pusthakam)", "Tamil" to "புத்தகம் (Puthagam)", "Kannada" to "ಪುಸ್ತಕ (Pustaka)"),
        "I read an interesting book." to mapOf("Telugu" to "నేను ఒక ఆసక్తికరమైన పుస్తకాన్ని చదివాను.", "Hindi" to "मैंने एक दिलचस्प किताब पढ़ी।", "Malayalam" to "ഞാൻ രസകരമായ ഒരു പുസ്തകം വായിച്ചു.", "Tamil" to "நான் ஒரு சுவாரஸ்யமான புத்தகம் படித்தேன்.", "Kannada" to "ನಾನು ಒಂದು ಆಸಕ್ತಿದಾಯಕ ಪುಸ್ತಕ ಓದಿದೆ."),
        "Bread" to mapOf("Telugu" to "రొట్టె / బ్రెడ్", "Hindi" to "रोटी (Roti) / ब्रेड", "Malayalam" to "റൊട്ടി / ബ്രെഡ്", "Tamil" to "ரொட்டி / ரொட்டி", "Kannada" to "ರೊಟ್ಟಿ / ಬ್ರೆಡ್"),
        "I buy fresh bread every morning." to mapOf("Telugu" to "నేను ప్రతి ఉదయం తాజా బ్రెడ్ కొంటాను.", "Hindi" to "मैं हर सुबह ताजा ब्रेड खरीदता हूँ।", "Malayalam" to "ഞാൻ എല്ലാ ദിവസവും രാവിലെ പുതിയ ബ്രെഡ് വാങ്ങുന്നു.", "Tamil" to "நான் தினமும் காலையில் புதிய ரொட்டி வாங்குகிறேன்.", "Kannada" to "ನಾನು ಪ್ರತಿದิน ಬೆಳಿಗ್ಗೆ ತಾಜಾ ಬ್ರೆಡ್ ಖರೀದಿಸುತ್ತೇನೆ."),
        "Friend / Buddy" to mapOf("Telugu" to "స్నేహితుడు", "Hindi" to "मित्र / दोस्त", "Malayalam" to "കൂട്ടുകാരൻ", "Tamil" to "நண்பன்", "Kannada" to "ಗೆಳೆಯ"),
        "He is my best friend." to mapOf("Telugu" to "అతను నా ప్రాణ స్నేహితుడు.", "Hindi" to "वह मेरा सबसे अच्छा दोस्त है।", "Malayalam" to "അവൻ എന്റെ ഏറ്റവും നല്ല കൂട്ടുകാരനാണ്.", "Tamil" to "அவன் என் சிறந்த நண்பன்.", "Kannada" to "ಅವನು ನನ್ನ ಅತ್ಯುತ್ತम ಸ್ನೇಹಿತ."),
        "House" to mapOf("Telugu" to "ఇల్లు (Illu)", "Hindi" to "घर (Ghar)", "Malayalam" to "വീട് (Veedu)", "Tamil" to "வீடு (Veedu)", "Kannada" to "ಮನೆ (Mane)"),
        "My house is near the school." to mapOf("Telugu" to "నా ఇల్లు పాఠశాల దగ్గర ఉంది.", "Hindi" to "मेरा घर स्कूल के पास है।", "Malayalam" to "എന്റെ വീട് സ്കൂളിനടുത്താണ്.", "Tamil" to "என் வீடு பள்ளிக்கு அருகில் உள்ளது.", "Kannada" to "ನನ್ನ ಮನೆ ಶಾಲೆಯ ಹತ್ತಿರ ಇದೆ.")
    )
    
    val lower = originalTranslation.lowercase()
    if (map.containsKey(originalTranslation)) {
        return map[originalTranslation]?.get(nativeLang) ?: originalTranslation
    }
    
    if (nativeLang == "Telugu") {
        if (lower.contains("where is")) return "ఎక్కడ ఉంది?"
        if (lower.contains("thank you")) return "ధన్యవాదాలు"
        if (lower.contains("hello")) return "హలో"
    } else if (nativeLang == "Hindi") {
        if (lower.contains("where is")) return "कहाँ है?"
        if (lower.contains("thank you")) return "धन्यवाद"
        if (lower.contains("hello")) return "नमस्ते"
    } else if (nativeLang == "Malayalam") {
        if (lower.contains("where is")) return "എവിടെയാണ്?"
        if (lower.contains("thank you")) return "നന്ദി"
        if (lower.contains("hello")) return "ഹലോ"
    }
    
    return originalTranslation
}
