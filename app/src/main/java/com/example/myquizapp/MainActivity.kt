package com.example.myquizapp

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class QaPair(
    val question: String,
    val answer: String
)

class MainActivity : AppCompatActivity() {

    private companion object {
        const val KEY_QUESTION = "question"
        const val KEY_ANSWER = "answer"
        const val KEY_IS_ANSWER_SHOWN = "isAnswerShown"

        const val REMOTE_URL = "https://interviewasker.usachborodach.ru/qanda"
        const val CACHE_FILE = "qanda.json"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
    }

    private lateinit var qaPairs: List<QaPair>
    private lateinit var textView: TextView
    private lateinit var button: Button

    private var currentQuestion: String = ""
    private var currentAnswer: String = ""
    private var isAnswerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        button = findViewById(R.id.button)

        // 1. Мгновенно: кеш → assets → пусто
        qaPairs = loadFromCache() ?: loadFromAssets() ?: emptyList()

        button.setOnClickListener {
            if (isAnswerShown) {
                showRandomQuestion()
            } else {
                textView.text = currentAnswer
                isAnswerShown = true
                button.setText(R.string.next_question)
            }
        }

        if (savedInstanceState != null) {
            currentQuestion = savedInstanceState.getString(KEY_QUESTION).orEmpty()
            currentAnswer = savedInstanceState.getString(KEY_ANSWER).orEmpty()
            isAnswerShown = savedInstanceState.getBoolean(KEY_IS_ANSWER_SHOWN, false)

            if (currentQuestion.isBlank()) {
                showRandomQuestion()
            } else {
                textView.text = if (isAnswerShown) currentAnswer else currentQuestion
                button.setText(
                    if (isAnswerShown) R.string.next_question else R.string.show_answer
                )
            }
        } else {
            showRandomQuestion()
        }

        // 2. В фоне: обновить кеш с сервера (только на Wi-Fi)
        refreshFromNetwork()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_QUESTION, currentQuestion)
        outState.putString(KEY_ANSWER, currentAnswer)
        outState.putBoolean(KEY_IS_ANSWER_SHOWN, isAnswerShown)
    }

    private fun showRandomQuestion() {
        if (qaPairs.isEmpty()) {
            textView.text = "Список вопросов пуст"
            button.isEnabled = false
            return
        }
        val pair = qaPairs.random()
        currentQuestion = pair.question
        currentAnswer = pair.answer
        textView.text = currentQuestion
        isAnswerShown = false
        button.isEnabled = true
        button.setText(R.string.show_answer)
    }

    // ─── Загрузка ─────────────────────────────────────────────────────────

    private fun loadFromCache(): List<QaPair>? = try {
        val f = File(filesDir, CACHE_FILE)
        if (!f.exists()) null else parseQaJson(f.readText(Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    private fun loadFromAssets(): List<QaPair>? = try {
        val json = assets.open(CACHE_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        parseQaJson(json)
    } catch (e: Exception) {
        null
    }

    private fun parseQaJson(json: String): List<QaPair> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            QaPair(
                question = obj.getString("question"),
                answer = obj.getString("answer"),
            )
        }
    }

    // ─── Сеть ─────────────────────────────────────────────────────────────

    private fun refreshFromNetwork() {
        if (!isOnWifi()) return

        Thread {
            val freshPairs = try {
                val json = httpGet(REMOTE_URL)
                val pairs = parseQaJson(json)          // сначала валидируем
                if (pairs.isNotEmpty()) {
                    writeCache(json)
                }
                pairs
            } catch (e: Exception) {
                null                                  // таймаут/нет сети/битый JSON
            } ?: return@Thread

            if (freshPairs.isEmpty()) return@Thread

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val wasEmpty = qaPairs.isEmpty()
                qaPairs = freshPairs
                // если до этого данных вообще не было — показываем сразу;
                // иначе текущий вопрос не трогаем, новый набор
                // подхватится при следующем «Следующий вопрос»
                if (wasEmpty) showRandomQuestion()
            }
        }.start()
    }

    private fun writeCache(json: String) {
        // атомарно: пишем в .tmp, потом переименовываем
        val dst = File(filesDir, CACHE_FILE)
        val tmp = File(filesDir, "$CACHE_FILE.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(dst)) {
            // если rename не сработал (бывает на некоторых ФС) — пишем напрямую
            dst.writeText(json, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}