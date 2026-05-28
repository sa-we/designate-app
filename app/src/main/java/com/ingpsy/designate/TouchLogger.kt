package com.ingpsy.designate

////////////////////////////////////////////////////////////
// logs all touch events


import android.content.Context
import android.view.MotionEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.appendText
import kotlin.io.writeText
import kotlin.ranges.until

class TouchLogger(val context: Context, socialMedia: String, userId: String?) {

    private val fileName = "touch_events_${socialMedia}_$userId.csv"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS", Locale.getDefault())


    fun logTouchEvent(event: MotionEvent) {
        val csvFile = File(context.filesDir, fileName)

        if (!csvFile.exists()) {
            csvFile.writeText("time,action,id,x,y,pressure,size\n")
        }

        val time = dateFormat.format(Date())
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"                   // First pointer touches the screen; starts a gesture
            MotionEvent.ACTION_UP -> "UP"                       // Last pointer leaves the screen; ends the gesture
            MotionEvent.ACTION_MOVE -> "MOVE"                   // Pointer moves on the screen; pointer id = finger
            MotionEvent.ACTION_POINTER_DOWN -> "PNTR_DOWN"      // Additional pointer (e.g., second finger) touches during multitouch
            MotionEvent.ACTION_POINTER_UP -> "PNTR_UP"          // Additional pointer leaves the screen
            MotionEvent.ACTION_CANCEL -> "CANCEL"               // Gesture is canceled (e.g., by the system)
            else -> "OTHER"
        }
        val pointerCount = event.pointerCount

        for (i in 0 until pointerCount) {
            val id = event.getPointerId(i)
            val x = event.getX(i)
            val y = event.getY(i)
            val pressure = event.getPressure(i)
            val size = event.getSize(i)

            val line = "$time,$action,$id,$x,$y,$pressure,$size\n"
            csvFile.appendText(line)
        }
    }
}
