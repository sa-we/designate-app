package com.ingpsy.designate

////////////////////////////////////////////////////////////
// logs all answers

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.appendText

class QuestionnaireLogger(val context: Context, socialMedia: String, userId: String?) {

    private val fileName = "questionnaires_${socialMedia}_$userId.csv"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())


    fun logQuestionnaire(context: Context, startTime: String, a1: Int, a2: Int, a3a: String, a3b: String, a3c: String, a4: Int){
        val csvFile = File(context.filesDir, fileName)

        if (!csvFile.exists()) {
            csvFile.appendText("startTime, endTime, a1, a2, a3a, a3b, a3c, a4\n")
        }

        val endTime = dateFormat.format(Date())
        val line = "$startTime, $endTime, $a1, $a2, $a3a, $a3b, $a3c, $a4\n"
        csvFile.appendText(line)
    }

}
