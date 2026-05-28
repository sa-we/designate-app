package com.ingpsy.designate

////////////////////////////////////////////////////////////
// AppLogger logs all app relevant events, e.g. starting
// native Facebook app as well as DESIGNATE start and stop
// events


import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.appendText

class AppLogger(val context: Context, userId: String?) {

    private val fileName = "app_events_$userId.csv"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

    fun logAppEvent(context: Context, app: String, socialMedia: String, mode: String, state: String){
        val csvFile = File(context.filesDir, fileName)

        if (!csvFile.exists()) {
            csvFile.appendText("time,app,socialMedia,mode,state\n") //e.g. 2025-12-01_15:27:31, DESIGNATE, YouTube, login, foreground
        }

        val time = dateFormat.format(Date())
        val line = "$time, $app, $socialMedia, $mode, $state\n"
        csvFile.appendText(line)
    }

}
