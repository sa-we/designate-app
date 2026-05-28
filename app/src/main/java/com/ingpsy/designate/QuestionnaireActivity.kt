package com.ingpsy.designate

////////////////////////////////////////////////////////////
// questionnaire gui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class QuestionnaireActivity : AppCompatActivity() {

    private var appLogger : AppLogger? = null
    private var questionnaireLogger : QuestionnaireLogger? = null

    var selectedSocialMedia = "no_sm_selected"

    private var q1Moved = false
    private var q2Moved = false
    private var q4Moved = false

    override fun onStart() {
        super.onStart()
        val userId: String? = loadPrefString(this, "user_id")
        Log.d("MyService", "User ID: $userId")
        questionnaireLogger = QuestionnaireLogger(this, selectedSocialMedia, userId)
        appLogger = AppLogger(this, userId)
        appLogger?.logAppEvent(this, "DESIGNATE", selectedSocialMedia, "Questionnaire", "foreground")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questionnaire)

        selectedSocialMedia = intent.getStringExtra("selected_social_media").toString()
        Log.d("WebViewActivity", "selected_social_media = $selectedSocialMedia")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
        val startTime = dateFormat.format(Date())

        val seekQ1 = findViewById<SeekBar>(R.id.seekQ1)
        val seekQ2 = findViewById<SeekBar>(R.id.seekQ2)
        val seekQ4 = findViewById<SeekBar>(R.id.seekQ4)

        val valueQ1 = findViewById<TextView>(R.id.valueQ1)
        val valueQ2 = findViewById<TextView>(R.id.valueQ2)
        val valueQ4 = findViewById<TextView>(R.id.valueQ4)

        val inputQ3a = findViewById<EditText>(R.id.inputQ3a)
        val inputQ3b = findViewById<EditText>(R.id.inputQ3b)
        val inputQ3c = findViewById<EditText>(R.id.inputQ3c)

        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        seekQ1.setOnSeekBarChangeListener(simpleSeekBarListener { value ->
            q1Moved = true
            valueQ1.text = value.toString()
        })
        seekQ2.setOnSeekBarChangeListener(simpleSeekBarListener { value ->
            q2Moved = true
            valueQ2.text = value.toString()
        })
        seekQ4.setOnSeekBarChangeListener(simpleSeekBarListener { value ->
            q4Moved = true
            valueQ4.text = value.toString()
        })
        btnSubmit.setOnClickListener {
            val q1 = seekQ1.progress
            val q2 = seekQ2.progress
            val q3a = inputQ3a.text.toString().trim()
            val q3b = inputQ3b.text.toString().trim()
            val q3c = inputQ3c.text.toString().trim()
            val q4 = seekQ4.progress

            // check slider
            if (!(q1Moved && q4Moved)){
                Toast.makeText(this,
                    getString(R.string.toast_please_operate_all_seekbars), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // when content is remembered, at least one field must be filled in
            if (q2 > 0) {
                if (q3a.isEmpty() && q3b.isEmpty() && q3c.isEmpty()) {
                    Toast.makeText(this, getString(R.string.toast_fill_text_field), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            questionnaireLogger?.logQuestionnaire(this, startTime, q1, q2, q3a, q3b, q3c, q4)

            saveQuestionnaire(this)

            appLogger?.logAppEvent(this, "DESIGNATE", selectedSocialMedia, "Questionnaire", "background")

            Toast.makeText(this,
                getString(R.string.toast_questionnaire_successfully_completed), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveQuestionnaire(context: Context) {
        val prefs = context.getSharedPreferences(Config.QUESTIONNAIRE_PREFS_NAME, MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val count = prefs.getInt("opened_$today", 0)
        prefs.edit { putInt("opened_$today", count + 1) }
    }

    private fun simpleSeekBarListener(onProgressChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
}