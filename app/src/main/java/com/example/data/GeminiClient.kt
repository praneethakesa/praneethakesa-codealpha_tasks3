package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.util.Log
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class GeminiLanguageUnit(
    val original: String,
    val ipa: String,
    val translation: String,
    val exampleOriginal: String,
    val exampleTranslation: String
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    
    // We construct OkHttpClient with 60s timeouts as mandated by the Gemini API guidelines
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listAdapter = moshi.adapter<List<GeminiLanguageUnit>>(
        Types.newParameterizedType(List::class.java, GeminiLanguageUnit::class.java)
    )

    /**
     * Check if Gemini API key is configured.
     */
    fun isKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return !key.isNullOrEmpty() && key != "MY_GEMINI_API_KEY" && key != "placeholder"
    }

    private suspend fun generateWithModel(
        modelName: String,
        targetLanguage: String, 
        categoryTopic: String,
        nativeLanguage: String,
        apiKey: String
    ): List<GeminiLanguageUnit> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        
        val prompt = """
            You are an expert language teacher. Generate exactly 5 learning items (words, phrases, or grammar rules) 
            for someone learning the language "$targetLanguage" (with translations and explanations translated to "$nativeLanguage" instead of English!) under the specific topic/category: "$categoryTopic".
            
            Return ONLY a valid JSON array of objects without any markdown formatting, markdown code blocks, or backticks. 
            Do NOT wrap it in ```json ... ``` formatting.
            
            Each object in the array must contain exactly these 5 keys:
            1. "original": The word, phrase, or short sentence in the target language ($targetLanguage).
            2. "ipa": Interactive phonetic transliteration/pronunciation text for $nativeLanguage speakers.
            3. "translation": The translation in $nativeLanguage.
            4. "exampleOriginal": A natural example sentence in the target language ($targetLanguage) using the item.
            5. "exampleTranslation": The translation of that example sentence in $nativeLanguage.
            
            Double-checked constraints:
            - Output must be parseable by Kotlin Moshi library.
            - Provide high-quality, practical vocabulary or sentences.
        """.trimIndent()

        // Build the request body matching the direct REST format from example
        val jsonRequest = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        jsonRequest.put("contents", contentsArray)

        // Request format can also request JSON mime type
        val generationConfig = JSONObject()
        generationConfig.put("responseMimeType", "application/json")
        jsonRequest.put("generationConfig", generationConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonRequest.toString().toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Request failed for model $modelName: ${response.code} $errorBody")
                throw Exception("API call failed with code ${response.code}: $errorBody")
            }
            
            val responseBodyStr = response.body?.string()
                ?: throw Exception("Empty response from Gemini API")
            
            Log.d(TAG, "Raw response from model $modelName: $responseBodyStr")
            
            // Extract text response
            val mainJsonObj = JSONObject(responseBodyStr)
            val candidates = mainJsonObj.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val firstPart = parts.getJSONObject(0)
            val rawText = firstPart.getString("text").trim()
            
            Log.d(TAG, "Extracted text: $rawText")
            
            // Clean markdown code blocks if any (fallback resilience)
            var jsonText = rawText
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.substringAfter("```json").substringAfter("```")
                jsonText = jsonText.substringBeforeLast("```")
            }
            jsonText = jsonText.trim()
            
            val items = listAdapter.fromJson(jsonText)
                ?: throw Exception("Parsed result was null")
            
            items
        }
    }

    /**
     * Generate 5 custom language learning items for a target language and custom category.
     * With robust automatic retry and secondary model fallback for resilience against 503 errors.
     */
    suspend fun generateCustomItems(
        targetLanguage: String, 
        categoryTopic: String,
        nativeLanguage: String
    ): List<GeminiLanguageUnit> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isKeyConfigured()) {
            throw IllegalStateException("Gemini API Key is not configured in the Secrets panel.")
        }

        // We try gemini-3.5-flash first, then fall back to gemini-3.1-flash-lite-preview as a high availability backup.
        val modelsToTry = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview")
        var lastException: Exception? = null

        for (model in modelsToTry) {
            // Try up to 2 attempts for each model
            for (attempt in 1..2) {
                try {
                    Log.d(TAG, "Attempting generation with model $model (attempt $attempt/2)")
                    return generateWithModel(model, targetLanguage, categoryTopic, nativeLanguage, apiKey)
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Attempt $attempt with model $model failed: ${e.message}")
                    if (attempt < 2) {
                        // Wait with exponential backoff before retrying
                        delay(1200L * attempt)
                    }
                }
            }
        }

        throw lastException ?: Exception("All Gemini models failed to generate content.")
    }
}
