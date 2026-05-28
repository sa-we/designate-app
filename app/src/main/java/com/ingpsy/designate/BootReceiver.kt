package com.ingpsy.designate

/////////////////////////////////////////////////////////////////////
// BootReceiver is notified when the system has been restarted.
// It then checks whether SocialMediaService was previously started.
// If this is the case, the service is restarted.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot detected - check state")

            val prefs = context.getSharedPreferences("service_status", Context.MODE_PRIVATE)
            val wasRunningBeforeReboot = prefs.getBoolean("service_active", false)

            if (wasRunningBeforeReboot) {
                Log.d("BootReceiver", "Service was active → restart!")
                val serviceIntent = Intent(context, SocialMediaMonitoringService::class.java).apply {
                    action = SocialMediaMonitoringService.ServiceAction.START.name
                }
                context.startForegroundService(serviceIntent)
            } else {
                Log.d("BootReceiver", "Service was not active → skip")
            }
        }
    }
}


