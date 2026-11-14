package com.example.myquizapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
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

        const val DECK_PREFS = "deck"
        const val KEY_DECK_ORDER = "deckOrder"
        const val KEY_DECK_INDEX = "deckIndex"
        const val KEY_DECK_CURRENT = "deckCurrentQuestion"

        const val REMOTE_URL = "https://interviewasker.usachborodach.ru/qanda"
        const val CACHE_FILE = "qanda.json"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
    }

    private lateinit var qaPairs: List<QaPair>

    // «Колода»: перемешанный список вопросов текущего круга.
    // Проход по кругу гарантирует, что внутри круга вопросов нет.
    private var deck: List<QaPair> = emptyList()
    private var deckIndex = 0

    private lateinit var textView: TextView
    private lateinit var button: Button
    private lateinit var buttonBack: Button
    private lateinit var markwon: Markwon

    private var currentQuestion: String = ""
    private var currentAnswer: String = ""
    private var isAnswerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        button = findViewById(R.id.button)
        buttonBack = findViewById(R.id.buttonBack)

        markwon = buildMarkwon()

        // 1. Мгновенно: кеш → assets → пусто
        qaPairs = loadFromCache() ?: loadFromAssets() ?: emptyList()
        initDeck(qaPairs)

        button.setOnClickListener {
            if (isAnswerShown) {
                showNextQuestion()
            } else {
                markwon.setMarkdown(textView, currentAnswer)
                isAnswerShown = true
                button.setText(R.string.next_question)
            }
        }

        buttonBack.setOnClickListener {
            showPreviousQuestion()
        }

        if (savedInstanceState != null) {
            currentQuestion = savedInstanceState.getString(KEY_QUESTION).orEmpty()
            currentAnswer = savedInstanceState.getString(KEY_ANSWER).orEmpty()
            isAnswerShown = savedInstanceState.getBoolean(KEY_IS_ANSWER_SHOWN, false)

            if (currentQuestion.isBlank()) {
                showCurrentQuestion()
            } else {
                updateBackButton()
                markwon.setMarkdown(
                    textView,
                    if (isAnswerShown) currentAnswer else currentQuestion
                )
                button.setText(
                    if (isAnswerShown) R.string.next_question else R.string.show_answer
                )
            }
        } else {
            showCurrentQuestion()
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

    private fun buildMarkwon(): Markwon =
        Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()

    // ─── Колода ─────────────────────────────────────────────────────────────

    // Восстанавливаем колоду прошлого запуска (пройденные вопросы остаются
    // пройденными), если она совместима с текущим списком. Иначе перемешиваем.
    private fun initDeck(pairs: List<QaPair>) {
        val prefs = getSharedPreferences(DECK_PREFS, Context.MODE_PRIVATE)
        val byQuestion = pairs.associateBy { it.question }
        val savedOrder = prefs.getString(KEY_DECK_ORDER, null)

        if (savedOrder != null) {
            val questions = try {
                val arr = JSONArray(savedOrder)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
            val restored = questions.mapNotNull { byQuestion[it] }
            if (restored.isNotEmpty()) {
                deck = restored
                val savedCurrent = prefs.getString(KEY_DECK_CURRENT, null)
                val fromCurrent = savedCurrent
                    ?.let { q -> restored.indexOfFirst { it.question == q } }
                    ?.takeIf { it >= 0 }
                deckIndex = fromCurrent
                    ?: prefs.getInt(KEY_DECK_INDEX, 0).coerceIn(0, restored.size - 1)
                return
            }
        }

        deck = pairs.shuffled()
        deckIndex = 0
        saveDeck()
    }

    private fun saveDeck() {
        val order = JSONArray()
        deck.forEach { order.put(it.question) }
        getSharedPreferences(DECK_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DECK_ORDER, order.toString())
            .putInt(KEY_DECK_INDEX, deckIndex)
            .putString(KEY_DECK_CURRENT, deck.getOrNull(deckIndex)?.question.orEmpty())
            .apply()
    }

    private fun showCurrentQuestion() {
        if (deck.isEmpty()) {
            textView.text = "Список вопросов пуст"
            button.isEnabled = false
            buttonBack.isEnabled = false
            return
        }
        deckIndex = deckIndex.coerceIn(0, deck.size - 1)
        val pair = deck[deckIndex]
        currentQuestion = pair.question
        currentAnswer = pair.answer
        markwon.setMarkdown(textView, currentQuestion)
        isAnswerShown = false
        button.isEnabled = true
        button.setText(R.string.show_answer)
        updateBackButton()
    }

    private fun showNextQuestion() {
        if (deck.isEmpty()) return
        if (deckIndex >= deck.size - 1) {
            // круг пройден — раздаём новую колоду
            deck = qaPairs.shuffled()
            deckIndex = 0
        } else {
            deckIndex++
        }
        saveDeck()
        showCurrentQuestion()
    }

    private fun showPreviousQuestion() {
        if (deckIndex == 0) return
        deckIndex--
        saveDeck()
        showCurrentQuestion()
    }

    private fun updateBackButton() {
        buttonBack.isEnabled = deck.isNotEmpty() && deckIndex > 0
    }

    // Обновление списка с сервером применяется сразу: пройденная часть
    // колоды сохраняется, непройденные и новые вопросы перемешиваются
    // и ставятся после текущей позиции.
    private fun rebuildDeck(newPairs: List<QaPair>) {
        if (newPairs.isEmpty()) return
        val byQuestion = newPairs.associateBy { it.question }
        val oldQuestions = deck.map { it.question }.toSet()

        val seen = deck.take(deckIndex + 1).filter { it.question in byQuestion }
        val unseen = deck.drop(deckIndex + 1).filter { it.question in byQuestion }
        val added = newPairs.filter { it.question !in oldQuestions }

        deck = seen + (unseen + added).shuffled()
        // Текущий вопрос — последний в пройденной части
        deckIndex = (seen.size - 1).coerceIn(0, deck.size - 1)
        saveDeck()
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
                val pairs = parseQaJson(json)
                if (pairs.isNotEmpty()) {
                    writeCache(json)
                }
                pairs
            } catch (e: Exception) {
                null
            } ?: return@Thread

            if (freshPairs.isEmpty()) return@Thread

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val wasEmpty = deck.isEmpty()
                qaPairs = freshPairs
                rebuildDeck(freshPairs)
                // Текущий вопрос на экране не трогаем, кроме случая,
                // когда до обновления показывали пустоту
                if (wasEmpty && deck.isNotEmpty()) {
                    showCurrentQuestion()
                } else {
                    updateBackButton()
                }
            }
        }.start()
    }

    private fun writeCache(json: String) {
        val dst = File(filesDir, CACHE_FILE)
        val tmp = File(filesDir, "$CACHE_FILE.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(dst)) {
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