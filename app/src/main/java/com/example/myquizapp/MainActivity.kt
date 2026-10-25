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

    private lateinit var qaPairs: List<QaPair>

    private lateinit var textView: TextView
    private lateinit var button: Button

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

        showRandomQuestion()
    }

    private fun showRandomQuestion() {
        if (qaPairs.isEmpty()) {
            textView.text = "Список вопросов пуст"
            button.isEnabled = false
            return
        }
        val pair = qaPairs.random()
        textView.text = pair.question
        currentAnswer = pair.answer
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