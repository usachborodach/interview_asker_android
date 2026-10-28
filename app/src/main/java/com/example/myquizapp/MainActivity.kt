package com.example.myquizapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

data class QaPair(
    val question: String,
    val answer: String
)

class MainActivity : AppCompatActivity() {

    private companion object {
        const val KEY_QUESTION = "question"
        const val KEY_ANSWER = "answer"
        const val KEY_IS_ANSWER_SHOWN = "isAnswerShown"
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

        qaPairs = runCatching { loadQaPairs() }
            .getOrElse {
                // Если JSON сломан — не падаем, показываем понятное сообщение
                listOf(QaPair("Ошибка загрузки", "Не удалось прочитать qanda.json: ${it.message}"))
            }

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
            // Activity пересоздана (поворот, разблокировка, возврат из фона).
            // НЕ выбираем новый вопрос — восстанавливаем прежний.
            currentQuestion = savedInstanceState.getString(KEY_QUESTION).orEmpty()
            currentAnswer = savedInstanceState.getString(KEY_ANSWER).orEmpty()
            isAnswerShown = savedInstanceState.getBoolean(KEY_IS_ANSWER_SHOWN, false)

            textView.text = if (isAnswerShown) currentAnswer else currentQuestion
            button.setText(
                if (isAnswerShown) R.string.next_question else R.string.show_answer
            )
        } else {
            // Холодный старт — единственный случай, когда нужен новый вопрос.
            showRandomQuestion()
        }
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
        button.setText(R.string.show_answer)
    }

    private fun loadQaPairs(): List<QaPair> {
        val json = assets.open("qanda.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            QaPair(
                question = obj.getString("question"),
                answer = obj.getString("answer")
            )
        }
    }
}