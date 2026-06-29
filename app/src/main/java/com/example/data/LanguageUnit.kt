package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(tableName = "language_units")
data class LanguageUnit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val language: String,            // "Spanish", "French", "Japanese", "German"
    val category: String,            // "Vocabulary", "Phrases", "Grammar"
    val original: String,            // e.g. "Hola"
    val ipa: String,                 // transliteration/pronunciation text e.g. "oh-lah"
    val translation: String,         // e.g. "Hello"
    val exampleOriginal: String,     // e.g. "¡Hola! ¿Cómo estás?"
    val exampleTranslation: String,  // e.g. "Hello! How are you?"
    val isLearned: Boolean = false,
    val isFavorite: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_scores")
data class QuizScore(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val language: String,
    val category: String,
    val score: Int,
    val totalQuestions: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "speaking_practice_logs")
data class SpeakingPracticeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val unitId: Int,
    val original: String,
    val language: String,
    val translation: String,
    val accuracy: Int,
    val pointsEarned: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface LanguageUnitDao {
    @Query("SELECT * FROM language_units ORDER BY addedTimestamp DESC")
    fun getAllUnits(): Flow<List<LanguageUnit>>

    @Query("SELECT * FROM language_units WHERE language = :language ORDER BY addedTimestamp DESC")
    fun getUnitsForLanguage(language: String): Flow<List<LanguageUnit>>

    @Query("SELECT * FROM language_units WHERE language = :language AND category = :category ORDER BY addedTimestamp DESC")
    fun getUnitsForCategory(language: String, category: String): Flow<List<LanguageUnit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: LanguageUnit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(units: List<LanguageUnit>)

    @Update
    suspend fun updateUnit(unit: LanguageUnit)

    @Query("UPDATE language_units SET isLearned = :isLearned WHERE id = :id")
    suspend fun updateLearnedStatus(id: Int, isLearned: Boolean)

    @Query("UPDATE language_units SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean)

    @Query("DELETE FROM language_units WHERE id = :id")
    suspend fun deleteUnit(id: Int)
}

@Dao
interface QuizScoreDao {
    @Query("SELECT * FROM quiz_scores ORDER BY timestamp DESC")
    fun getAllScores(): Flow<List<QuizScore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: QuizScore)

    @Query("DELETE FROM quiz_scores")
    suspend fun clearAllScores()
}

@Dao
interface SpeakingPracticeDao {
    @Query("SELECT * FROM speaking_practice_logs ORDER BY timestamp DESC")
    fun getAllSpeakingLogs(): Flow<List<SpeakingPracticeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakingLog(log: SpeakingPracticeLog)

    @Query("SELECT SUM(pointsEarned) FROM speaking_practice_logs")
    fun getTotalSpeakingPoints(): Flow<Int?>

    @Query("DELETE FROM speaking_practice_logs")
    suspend fun clearAllLogs()
}

@Database(entities = [LanguageUnit::class, QuizScore::class, SpeakingPracticeLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun languageUnitDao(): LanguageUnitDao
    abstract fun quizScoreDao(): QuizScoreDao
    abstract fun speakingPracticeDao(): SpeakingPracticeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "language_learning_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.languageUnitDao()
                    dao.insertAll(getPrepopulatedWords())
                }
            }
        }
    }
}

class LanguageRepository(private val db: AppDatabase) {
    val languageUnitDao = db.languageUnitDao()
    val quizScoreDao = db.quizScoreDao()
    val speakingPracticeDao = db.speakingPracticeDao()

    fun getAllUnits(): Flow<List<LanguageUnit>> = languageUnitDao.getAllUnits()
    fun getUnitsForLanguage(language: String): Flow<List<LanguageUnit>> = languageUnitDao.getUnitsForLanguage(language)
    fun getUnitsForCategory(language: String, category: String): Flow<List<LanguageUnit>> = languageUnitDao.getUnitsForCategory(language, category)
    fun getAllScores(): Flow<List<QuizScore>> = quizScoreDao.getAllScores()
    fun getAllSpeakingLogs(): Flow<List<SpeakingPracticeLog>> = speakingPracticeDao.getAllSpeakingLogs()
    fun getTotalSpeakingPoints(): Flow<Int?> = speakingPracticeDao.getTotalSpeakingPoints()

    suspend fun insertUnit(unit: LanguageUnit) = languageUnitDao.insertUnit(unit)
    suspend fun insertAll(units: List<LanguageUnit>) = languageUnitDao.insertAll(units)
    suspend fun updateUnit(unit: LanguageUnit) = languageUnitDao.updateUnit(unit)
    suspend fun updateLearnedStatus(id: Int, isLearned: Boolean) = languageUnitDao.updateLearnedStatus(id, isLearned)
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean) = languageUnitDao.updateFavoriteStatus(id, isFavorite)
    suspend fun deleteUnit(id: Int) = languageUnitDao.deleteUnit(id)
    suspend fun insertScore(score: QuizScore) = quizScoreDao.insertScore(score)
    suspend fun insertSpeakingLog(log: SpeakingPracticeLog) = speakingPracticeDao.insertSpeakingLog(log)
}

fun getPrepopulatedWords(): List<LanguageUnit> {
    return listOf(
        // === SPANISH ===
        // Vocabulary
        LanguageUnit(
            language = "Spanish",
            category = "Vocabulary",
            original = "Manzana",
            ipa = "mahn-sah-nah",
            translation = "Apple",
            exampleOriginal = "Me gusta comer una manzana roja.",
            exampleTranslation = "I like eating a red apple."
        ),
        LanguageUnit(
            language = "Spanish",
            category = "Vocabulary",
            original = "Gracias",
            ipa = "grah-syahs",
            translation = "Thank you",
            exampleOriginal = "Muchas gracias por tu ayuda.",
            exampleTranslation = "Thank you very much for your help."
        ),
        LanguageUnit(
            language = "Spanish",
            category = "Vocabulary",
            original = "Perro",
            ipa = "peh-rroh",
            translation = "Dog",
            exampleOriginal = "El perro corre en el parque.",
            exampleTranslation = "The dog runs in the park."
        ),
        // Phrases
        LanguageUnit(
            language = "Spanish",
            category = "Phrases",
            original = "¿Dónde está el baño?",
            ipa = "dohn-deh ehs-tah ehl bah-nyoh",
            translation = "Where is the bathroom?",
            exampleOriginal = "Disculpe señor, ¿Dónde está el baño?",
            exampleTranslation = "Excuse me sir, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Spanish",
            category = "Phrases",
            original = "Mucho gusto",
            ipa = "moo-choh goos-toh",
            translation = "Nice to meet you",
            exampleOriginal = "Hola, soy Carlos. ¡Mucho gusto!",
            exampleTranslation = "Hello, I am Carlos. Nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Spanish",
            category = "Grammar",
            original = "Ser vs Estar",
            ipa = "sehr vs ehs-tahr",
            translation = "To be (Permanent vs Temporary)",
            exampleOriginal = "Yo soy estudiante (permanent). Yo estoy feliz (temporary).",
            exampleTranslation = "I am a student. I am happy."
        ),

        // === FRENCH ===
        // Vocabulary
        LanguageUnit(
            language = "French",
            category = "Vocabulary",
            original = "Bonjour",
            ipa = "bohn-zhoor",
            translation = "Hello / Good morning",
            exampleOriginal = "Bonjour, comment allez-vous ?",
            exampleTranslation = "Good morning, how are you?"
        ),
        LanguageUnit(
            language = "French",
            category = "Vocabulary",
            original = "Chat",
            ipa = "shah",
            translation = "Cat",
            exampleOriginal = "Le chat noir dort sur le canapé.",
            exampleTranslation = "The black cat is sleeping on the couch."
        ),
        LanguageUnit(
            language = "French",
            category = "Vocabulary",
            original = "Eau",
            ipa = "oh",
            translation = "Water",
            exampleOriginal = "Un verre d'eau, s'il vous plaît.",
            exampleTranslation = "A glass of water, please."
        ),
        // Phrases
        LanguageUnit(
            language = "French",
            category = "Phrases",
            original = "S'il vous plaît",
            ipa = "seel voo pleh",
            translation = "Please (formal)",
            exampleOriginal = "Répétez lentement, s'il vous plaît.",
            exampleTranslation = "Repeat slowly, please."
        ),
        LanguageUnit(
            language = "French",
            category = "Phrases",
            original = "Je t'aime",
            ipa = "zhuh tehm",
            translation = "I love you",
            exampleOriginal = "Je t'aime de tout mon coeur.",
            exampleTranslation = "I love you with all my heart."
        ),
        // Grammar
        LanguageUnit(
            language = "French",
            category = "Grammar",
            original = "Gender Agreement (Le/La)",
            ipa = "luh / lah",
            translation = "Masculine 'the' (le) / Feminine 'the' (la)",
            exampleOriginal = "Le livre (masculine), la table (feminine).",
            exampleTranslation = "The book, the table."
        ),

        // === JAPANESE ===
        // Vocabulary
        LanguageUnit(
            language = "Japanese",
            category = "Vocabulary",
            original = "ありがとう (Arigatou)",
            ipa = "ah-ree-gah-toh",
            translation = "Thank you",
            exampleOriginal = "お返事ありがとう。(Ohenji arigatou.)",
            exampleTranslation = "Thank you for your reply."
        ),
        LanguageUnit(
            language = "Japanese",
            category = "Vocabulary",
            original = "水 (Mizu)",
            ipa = "mee-zoo",
            translation = "Water",
            exampleOriginal = "冷たい水を飲みます。(Tsumetai mizu o nomimasu.)",
            exampleTranslation = "I will drink cold water."
        ),
        LanguageUnit(
            language = "Japanese",
            category = "Vocabulary",
            original = "友達 (Tomodachi)",
            ipa = "toh-moh-dah-chee",
            translation = "Friend",
            exampleOriginal = "彼は私の友達です。(Kare wa watashi no tomodachi desu.)",
            exampleTranslation = "He is my friend."
        ),
        // Phrases
        LanguageUnit(
            language = "Japanese",
            category = "Phrases",
            original = "すみません (Sumimasen)",
            ipa = "soo-mee-mah-sen",
            translation = "Excuse me / Sorry",
            exampleOriginal = "すみません、駅はどこですか？ (Sumimasen, eki wa doko desu ka?)",
            exampleTranslation = "Excuse me, where is the station?"
        ),
        LanguageUnit(
            language = "Japanese",
            category = "Phrases",
            original = "美味しい (Oishii)",
            ipa = "oy-shee",
            translation = "Delicious",
            exampleOriginal = "このラーメンは本当に美味しい！ (Kono ramen wa hontou ni oishii!)",
            exampleTranslation = "This ramen is really delicious!"
        ),
        // Grammar
        LanguageUnit(
            language = "Japanese",
            category = "Grammar",
            original = "Topic Marker は (Wa)",
            ipa = "wah",
            translation = "Particle 'wa' identifies the main topic of sentence",
            exampleOriginal = "私は学生です。(Watashi wa gakusei desu.)",
            exampleTranslation = "As for me, I am a student."
        ),

        // === GERMAN ===
        // Vocabulary
        LanguageUnit(
            language = "German",
            category = "Vocabulary",
            original = "Apfel",
            ipa = "up-fel",
            translation = "Apple",
            exampleOriginal = "Ich esse einen grünen Apfel.",
            exampleTranslation = "I am eating a green apple."
        ),
        LanguageUnit(
            language = "German",
            category = "Vocabulary",
            original = "Familie",
            ipa = "fah-mee-lyuh",
            translation = "Family",
            exampleOriginal = "Ich liebe meine Familie.",
            exampleTranslation = "I love my family."
        ),
        LanguageUnit(
            language = "German",
            category = "Vocabulary",
            original = "Buch",
            ipa = "bookh",
            translation = "Book",
            exampleOriginal = "Das Buch ist sehr spannend.",
            exampleTranslation = "The book is very exciting."
        ),
        // Phrases
        LanguageUnit(
            language = "German",
            category = "Phrases",
            original = "Wie geht es dir?",
            ipa = "vee gayt es deer",
            translation = "How are you?",
            exampleOriginal = "Hallo Marie, wie geht es dir heute?",
            exampleTranslation = "Hello Marie, how are you today?"
        ),
        LanguageUnit(
            language = "German",
            category = "Phrases",
            original = "Gesundheit",
            ipa = "gheh-zoont-hyt",
            translation = "Bless you / Health",
            exampleOriginal = "Atschie! - Gesundheit!",
            exampleTranslation = "Achoo! - Bless you!"
        ),
        // Grammar
        LanguageUnit(
            language = "German",
            category = "Grammar",
            original = "Three Genders (Der/Die/Das)",
            ipa = "dehr / dee / dahs",
            translation = "Masculine 'the' (der), Feminine 'the' (die), Neuter 'the' (das)",
            exampleOriginal = "Der Mann, die Frau, das Kind.",
            exampleTranslation = "The man, the woman, the child."
        ),

        // === ITALIAN ===
        // Vocabulary
        LanguageUnit(
            language = "Italian",
            category = "Vocabulary",
            original = "Mela",
            ipa = "meh-lah",
            translation = "Apple",
            exampleOriginal = "Mi piace mangiare una mela rossa.",
            exampleTranslation = "I like to eat a red apple."
        ),
        LanguageUnit(
            language = "Italian",
            category = "Vocabulary",
            original = "Grazie",
            ipa = "grah-tsyeh",
            translation = "Thank you",
            exampleOriginal = "Grazie mille per l'aiuto.",
            exampleTranslation = "Thank you very much for the help."
        ),
        LanguageUnit(
            language = "Italian",
            category = "Vocabulary",
            original = "Gatto",
            ipa = "gaht-toh",
            translation = "Cat",
            exampleOriginal = "Il gatto dorme sul divano.",
            exampleTranslation = "The cat sleeps on the sofa."
        ),
        // Phrases
        LanguageUnit(
            language = "Italian",
            category = "Phrases",
            original = "Dov'è il bagno?",
            ipa = "doh-veh eel bah-nyoh",
            translation = "Where is the bathroom?",
            exampleOriginal = "Scusi, signore, dov'è il bagno?",
            exampleTranslation = "Excuse me, sir, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Italian",
            category = "Phrases",
            original = "Piacere di conoscerti",
            ipa = "pyah-cheh-reh dee koh-noh-shehr-tee",
            translation = "Nice to meet you",
            exampleOriginal = "Ciao, sono Marco. Piacere di conoscerti!",
            exampleTranslation = "Hello, I am Marco. Nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Italian",
            category = "Grammar",
            original = "Articles (Il vs La)",
            ipa = "eel vs lah",
            translation = "Definite Articles: Masculine (Il) / Feminine (La)",
            exampleOriginal = "Il libro (the book), la sedia (the chair).",
            exampleTranslation = "The book, the chair."
        ),

        // === PORTUGUESE ===
        // Vocabulary
        LanguageUnit(
            language = "Portuguese",
            category = "Vocabulary",
            original = "Maçã",
            ipa = "mah-sahn",
            translation = "Apple",
            exampleOriginal = "Eu gosto de comer uma maçã vermelha.",
            exampleTranslation = "I like eating a red apple."
        ),
        LanguageUnit(
            language = "Portuguese",
            category = "Vocabulary",
            original = "Obrigado",
            ipa = "oh-bree-gah-doo",
            translation = "Thank you",
            exampleOriginal = "Muito obrigado por tudo.",
            exampleTranslation = "Thank you very much for everything."
        ),
        LanguageUnit(
            language = "Portuguese",
            category = "Vocabulary",
            original = "Gato",
            ipa = "gah-too",
            translation = "Cat",
            exampleOriginal = "O gato está dormindo sob a mesa.",
            exampleTranslation = "The cat is sleeping under the table."
        ),
        // Phrases
        LanguageUnit(
            language = "Portuguese",
            category = "Phrases",
            original = "Onde fica o banheiro?",
            ipa = "ohn-deh fee-kah oo bah-nyay-roo",
            translation = "Where is the bathroom?",
            exampleOriginal = "Com licença, onde fica o banheiro?",
            exampleTranslation = "Excuse me, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Portuguese",
            category = "Phrases",
            original = "Muito prazer",
            ipa = "mwee-too prah-zehr",
            translation = "Nice to meet you",
            exampleOriginal = "Oi, eu sou Ana. Muito prazer!",
            exampleTranslation = "Hi, I am Ana. Nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Portuguese",
            category = "Grammar",
            original = "Ser vs Estar",
            ipa = "sehr vs ehs-tahr",
            translation = "To be (Permanent vs Temporary states)",
            exampleOriginal = "Eu sou feliz (personality). Eu estou feliz (current emotion).",
            exampleTranslation = "I am happy (permanent character). I am happy (in this moment)."
        ),

        // === CHINESE ===
        // Vocabulary
        LanguageUnit(
            language = "Chinese",
            category = "Vocabulary",
            original = "苹果 (Píngguǒ)",
            ipa = "ping-gwaw",
            translation = "Apple",
            exampleOriginal = "我喜欢吃红苹果。(Wǒ xǐhuān chī hóng píngguǒ.)",
            exampleTranslation = "I like eating red apples."
        ),
        LanguageUnit(
            language = "Chinese",
            category = "Vocabulary",
            original = "谢谢 (Xièxiè)",
            ipa = "shyeh-shyeh",
            translation = "Thank you",
            exampleOriginal = "非常感谢你的帮助。(Fēicháng gǎnxiè nǐ de bāngzhù.)",
            exampleTranslation = "Thank you very much for your help."
        ),
        LanguageUnit(
            language = "Chinese",
            category = "Vocabulary",
            original = "猫 (Māo)",
            ipa = "mow",
            translation = "Cat",
            exampleOriginal = "小猫在沙发上睡觉。(Xiǎomāo zài shāfā shàng shuìjiào.)",
            exampleTranslation = "The little cat is sleeping on the sofa."
        ),
        // Phrases
        LanguageUnit(
            language = "Chinese",
            category = "Phrases",
            original = "洗手间在哪里？ (Xǐshǒujiān zài nǎlǐ?)",
            ipa = "shee-show-jyan dzye nah-lee",
            translation = "Where is the bathroom?",
            exampleOriginal = "请问，洗手间在哪里？(Qǐngwèn, xǐshǒujiān zài nǎlǐ?)",
            exampleTranslation = "Excuse me, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Chinese",
            category = "Phrases",
            original = "很高兴认识你 (Hěn gāoxìng rènshí nǐ)",
            ipa = "hen gaow-shing rhen-shee nee",
            translation = "Nice to meet you",
            exampleOriginal = "你好！很高兴认识你。(Nǐ hǎo! Hěn gāoxìng rènshí nǐ.)",
            exampleTranslation = "Hello! Nice to meet you."
        ),
        // Grammar
        LanguageUnit(
            language = "Chinese",
            category = "Grammar",
            original = "Measure Word 个 (Gè)",
            ipa = "guh",
            translation = "General classifier / measure word for nouns",
            exampleOriginal = "一个人 (one person), 一个苹果 (one apple).",
            exampleTranslation = "One person, one apple."
        ),

        // === KOREAN ===
        // Vocabulary
        LanguageUnit(
            language = "Korean",
            category = "Vocabulary",
            original = "사과 (Sagwa)",
            ipa = "sah-gwah",
            translation = "Apple",
            exampleOriginal = "저는 빨간 사과를 좋아해요. (Jeoneun ppalgan sagwareul joahayo.)",
            exampleTranslation = "I like red apples."
        ),
        LanguageUnit(
            language = "Korean",
            category = "Vocabulary",
            original = "감사합니다 (Gamsahabnida)",
            ipa = "gahm-sah-hahm-nee-dah",
            translation = "Thank you",
            exampleOriginal = "도와주셔서 감사합니다. (Dowajusyeoseo gamsahabnida.)",
            exampleTranslation = "Thank you for helping me."
        ),
        LanguageUnit(
            language = "Korean",
            category = "Vocabulary",
            original = "고양이 (Goyangi)",
            ipa = "goh-yahng-ee",
            translation = "Cat",
            exampleOriginal = "고양이가 침대 위에서 자요. (Goyangiga chimdae wieseo jayo.)",
            exampleTranslation = "The cat is sleeping on the bed."
        ),
        // Phrases
        LanguageUnit(
            language = "Korean",
            category = "Phrases",
            original = "화장실이 어디예요? (Hwajangshiri eodiyeyo?)",
            ipa = "hwah-jahng-shee-ree aw-dee-yay-yoh",
            translation = "Where is the bathroom?",
            exampleOriginal = "실례합니다, 화장실이 어디예요? (Sillyehamnida, hwajangshiri eodiyeyo?)",
            exampleTranslation = "Excuse me, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Korean",
            category = "Phrases",
            original = "만나서 반갑습니다 (Mannaseo bangapseubnida)",
            ipa = "mahn-nah-saw bahn-gahp-seup-nee-dah",
            translation = "Nice to meet you",
            exampleOriginal = "안녕하세요, 만나서 반갑습니다! (Annyeonghaseyo, mannaseo bangapseubnida!)",
            exampleTranslation = "Hello, nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Korean",
            category = "Grammar",
            original = "Subject Particles (이/가)",
            ipa = "ee / gah",
            translation = "Identifies the subjective target in a clause",
            exampleOriginal = "고양이가 요기 있어요 (The cat is here).",
            exampleTranslation = "The cat is here."
        ),

        // === RUSSIAN ===
        // Vocabulary
        LanguageUnit(
            language = "Russian",
            category = "Vocabulary",
            original = "Яблоко",
            ipa = "yah-blah-kah",
            translation = "Apple",
            exampleOriginal = "Я ем спелое яблоко.",
            exampleTranslation = "I am eating a ripe apple."
        ),
        LanguageUnit(
            language = "Russian",
            category = "Vocabulary",
            original = "Спасибо",
            ipa = "spah-see-bah",
            translation = "Thank you",
            exampleOriginal = "Спасибо большое за подарок.",
            exampleTranslation = "Thank you very much for the gift."
        ),
        LanguageUnit(
            language = "Russian",
            category = "Vocabulary",
            original = "Кот",
            ipa = "kot",
            translation = "Cat",
            exampleOriginal = "Рыжий кот спит на кресле.",
            exampleTranslation = "The ginger cat is sleeping on the armchair."
        ),
        // Phrases
        LanguageUnit(
            language = "Russian",
            category = "Phrases",
            original = "Где туалет?",
            ipa = "gdeh too-ah-lyet",
            translation = "Where is the bathroom?",
            exampleOriginal = "Извините, пожалуйста, где туалет?",
            exampleTranslation = "Excuse me, please, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Russian",
            category = "Phrases",
            original = "Приятно познакомиться",
            ipa = "pree-yat-nah paz-nah-ko-meet-syah",
            translation = "Nice to meet you",
            exampleOriginal = "Меня зовут Иван. Приятно познакомиться!",
            exampleTranslation = "My name is Ivan. Nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Russian",
            category = "Grammar",
            original = "Noun Genders",
            ipa = "Masc / Fem / Neut",
            translation = "Nouns are masculine, feminine, or neuter.",
            exampleOriginal = "Дом (M), Машина (F), Письмо (N).",
            exampleTranslation = "House (masc), Car (fem), Letter (neuter)."
        ),

        // === ARABIC ===
        // Vocabulary
        LanguageUnit(
            language = "Arabic",
            category = "Vocabulary",
            original = "تفاحة",
            ipa = "toof-fah-hah",
            translation = "Apple",
            exampleOriginal = "أحب تناول تفاحة حمراء.",
            exampleTranslation = "I like eating a red apple."
        ),
        LanguageUnit(
            language = "Arabic",
            category = "Vocabulary",
            original = "شكراً",
            ipa = "shook-ran",
            translation = "Thank you",
            exampleOriginal = "شكراً جزيلاً لجهودك.",
            exampleTranslation = "Thank you very much for your efforts."
        ),
        LanguageUnit(
            language = "Arabic",
            category = "Vocabulary",
            original = "قطة",
            ipa = "qit-tah",
            translation = "Cat",
            exampleOriginal = "القطة نائمة تحت الكرسي.",
            exampleTranslation = "The cat is sleeping under the chair."
        ),
        // Phrases
        LanguageUnit(
            language = "Arabic",
            category = "Phrases",
            original = "أين الحمام؟",
            ipa = "ay-nah al-ham-mam",
            translation = "Where is the bathroom?",
            exampleOriginal = "من فضلك، أين الحمام؟",
            exampleTranslation = "Please, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Arabic",
            category = "Phrases",
            original = "تشرفت بمعرفتك",
            ipa = "ta-shar-raf-too bee ma-ree-fah-teek",
            translation = "Nice to meet you",
            exampleOriginal = "أنا سمير، تشرفت بمعرفتك!",
            exampleTranslation = "I am Samir, nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Arabic",
            category = "Grammar",
            original = "Definite Article ال (Al-)",
            ipa = "ahl",
            translation = "Noun prefix representing 'the'",
            exampleOriginal = "كتاب (book) becomes الكتاب (the book).",
            exampleTranslation = "Kitab (book) becomes Al-kitab (the book)."
        ),

        // === HINDI ===
        // Vocabulary
        LanguageUnit(
            language = "Hindi",
            category = "Vocabulary",
            original = "सेब",
            ipa = "sayb",
            translation = "Apple",
            exampleOriginal = "मुझे एक लाल सेब खाना पसंद है।",
            exampleTranslation = "I like eating a red apple."
        ),
        LanguageUnit(
            language = "Hindi",
            category = "Vocabulary",
            original = "धन्यवाद",
            ipa = "dhun-yuh-vaadh",
            translation = "Thank you",
            exampleOriginal = "आपकी मदद के लिए धन्यवाद।",
            exampleTranslation = "Thank you for your help."
        ),
        LanguageUnit(
            language = "Hindi",
            category = "Vocabulary",
            original = "बिल्ली",
            ipa = "bil-lee",
            translation = "Cat",
            exampleOriginal = "बिल्ली चटाई पर सो रही है।",
            exampleTranslation = "The cat is sleeping on the mat."
        ),
        // Phrases
        LanguageUnit(
            language = "Hindi",
            category = "Phrases",
            original = "शौचालय कहाँ है?",
            ipa = "show-cha-lay kuh-han hai",
            translation = "Where is the bathroom?",
            exampleOriginal = "सुनिए, शौचालय कहाँ है?",
            exampleTranslation = "Excuse me, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Hindi",
            category = "Phrases",
            original = "आपसे मिलकर खुशी हुई",
            ipa = "aap-say mil-kar khoo-shee hoo-ee",
            translation = "Nice to meet you",
            exampleOriginal = "मेरा नाम राहुल है। आपसे मिलकर खुशी हुई!",
            exampleTranslation = "My name is Rahul. Nice to meet you!"
        ),
         // Grammar
        LanguageUnit(
            language = "Hindi",
            category = "Grammar",
            original = "Verb Ending (Hona)",
            ipa = "hoh-nah",
            translation = "The auxiliary verb 'To Be' ends sentences.",
            exampleOriginal = "वह एक छात्र है। (He is a student).",
            exampleTranslation = "He is a student."
        ),

        // === SWEDISH ===
        // Vocabulary
        LanguageUnit(
            language = "Swedish",
            category = "Vocabulary",
            original = "Äpple",
            ipa = "eh-pleh",
            translation = "Apple",
            exampleOriginal = "Jag vill ha ett rött äpple.",
            exampleTranslation = "I want a red apple."
        ),
        LanguageUnit(
            language = "Swedish",
            category = "Vocabulary",
            original = "Tack",
            ipa = "tahk",
            translation = "Thank you",
            exampleOriginal = "Tack snälla för din hjälp.",
            exampleTranslation = "Thank you very much for your help."
        ),
        LanguageUnit(
            language = "Swedish",
            category = "Vocabulary",
            original = "Katt",
            ipa = "kaht",
            translation = "Cat",
            exampleOriginal = "Katten ligger och sover på sängen.",
            exampleTranslation = "The cat lies sleeping on the bed."
        ),
        // Phrases
        LanguageUnit(
            language = "Swedish",
            category = "Phrases",
            original = "Var är toaletten?",
            ipa = "vahr ehr toh-ah-leh-tehn",
            translation = "Where is the bathroom?",
            exampleOriginal = "Ursäkta mig, var är toaletten?",
            exampleTranslation = "Excuse me, where is the bathroom?"
        ),
        LanguageUnit(
            language = "Swedish",
            category = "Phrases",
            original = "Trevligt att träffas",
            ipa = "trehv-leet aht trehf-fahs",
            translation = "Nice to meet you",
            exampleOriginal = "Hej, jag heter Karl. Trevligt att träffas!",
            exampleTranslation = "Hello, I am Karl. Nice to meet you!"
        ),
        // Grammar
        LanguageUnit(
            language = "Swedish",
            category = "Grammar",
            original = "Noun Genders (En vs Ett)",
            ipa = "ehn vs eht",
            translation = "Nouns are divided into 'en' words and 'ett' words.",
            exampleOriginal = "En sked (a spoon), ett glas (a glass).",
            exampleTranslation = "A spoon, a glass."
        )
    )
}
