package com.ingpsy.designate

////////////////////////////////////////////////////////////
// MainActivity is the most important GUI component for
// controlling the DESIGNATE app.

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File


class MainActivity : ComponentActivity() {

    private val nextcloudUploader = NextcloudUploader(
        BuildConfig.NEXTCLOUD_URL,
        BuildConfig.NEXTCLOUD_USERNAME,
        BuildConfig.NEXTCLOUD_PASSWORD
    )

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    // gui elements
    lateinit var btnStartService: Button
    lateinit var btnStopService: Button
    lateinit var btnFacebook: Button
    lateinit var btnInstagram: Button
    lateinit var btnX: Button
    lateinit var btnYouTube: Button
    lateinit var btnSendData: Button
    lateinit var cbServicePermission: CheckBox
    lateinit var txtStudyTimeRemaining: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_help -> {
                    showHelpDialog()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_data_protection -> {
                    showDataProtectionDialog()
                    drawerLayout.closeDrawers()
                    true                }
                R.id.nav_about -> {
                    showAboutDialog()
                    drawerLayout.closeDrawers()
                    true                }
                else -> false
            }
        }

        btnStartService = findViewById(R.id.btnStartService)
        btnStartService.setOnClickListener {
            Toast.makeText(this, getString(R.string.toast_designate_runs_in_background), Toast.LENGTH_SHORT).show()
            Intent(this@MainActivity, SocialMediaMonitoringService::class.java).also {
                it.action = SocialMediaMonitoringService.ServiceAction.START.name
                ContextCompat.startForegroundService(this, it)
            }
            finishAffinity()
        }

        btnStopService = findViewById(R.id.btnStopService)
        btnStopService.setOnClickListener {
            Toast.makeText(this, getText(R.string.toast_designate_is_inactive), Toast.LENGTH_SHORT).show()
            Intent(this@MainActivity, SocialMediaMonitoringService::class.java).also {
                it.action = SocialMediaMonitoringService.ServiceAction.STOP.name
                startService(it)
            }
            btnStartService.isEnabled = true
            btnStopService.isEnabled = false
        }

        btnFacebook = findViewById(R.id.btnFacebook)
        btnFacebook.setOnClickListener {
            showLoginSocialMedia("Facebook")
        }

        btnInstagram = findViewById(R.id.btnInstagram)
        btnInstagram.setOnClickListener {
            showLoginSocialMedia("Instagram")
        }

        btnX= findViewById(R.id.btnX)
        btnX.setOnClickListener {
            showLoginSocialMedia("X")
        }

        btnYouTube = findViewById(R.id.btnYouTube)
        btnYouTube.setOnClickListener {
            showLoginSocialMedia("YouTube")
        }

        btnSendData = findViewById(R.id.btnSendData)
        btnSendData.setOnClickListener {
            val csvFilesCount = zipCsvFiles(this)
            Log.d("MainActivity", "SendData: $csvFilesCount files were zipped." )

            if (isDeviceOnline(this)){
                sendData(this)
            } else {
                Log.d("MainActivity", "Network error.")
                Toast.makeText(this,
                    getString(R.string.toast_no_internet_connection), Toast.LENGTH_LONG).show()
            }
        }

        cbServicePermission = findViewById(R.id.cbServicePermission)
        cbServicePermission.isChecked = loadPrefBoolean(this, Config.KEY_SERVICE_CONFIRMATION) == true
        cbServicePermission.setOnClickListener {
            savePrefBoolean(this, Config.KEY_SERVICE_CONFIRMATION, cbServicePermission.isChecked)
            activateServiceButtons()
            if (SocialMediaMonitoringService.isServiceRunning){
                Toast.makeText(this,
                    getString(R.string.toast_designate_is_inactive), Toast.LENGTH_SHORT).show()
                Intent(this@MainActivity, SocialMediaMonitoringService::class.java).also {
                    it.action = SocialMediaMonitoringService.ServiceAction.STOP.name
                    startService(it)
                }
                btnStopService.isEnabled = false
            }

            if (cbServicePermission.isChecked){
                showOpenBatterySettingsDialog()
                checkUnusedAppRestrictions()
            }
        }

        txtStudyTimeRemaining = findViewById(R.id.txt_remaining_days)


        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //// Logic
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////

        val isFirstStart = loadPrefBoolean(this, Config.KEY_FIRST_START) != true

        if (isFirstStart) {
            // delete shared pref after installation to avoid side effects
            val sharedPreferences = this.getSharedPreferences(Config.APP_PREFS_NAME, MODE_PRIVATE)
            sharedPreferences.edit { clear() }

            showWelcome {
                savePrefBoolean(this, Config.KEY_FIRST_START, true)
            }
        } else {
            if (checkPermissions()) {
                checkUserID()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        activateServiceButtons()

        val isStudyComplete = loadPrefBoolean(this, Config.KEY_STUDY_COMPLETE) == true

        val remaining = getRemainingTime(this)
        if (remaining.minutes > 0) {

            val timeString = when {
                remaining.days > 0 -> getString(R.string.txt_study_runtime_days, remaining.days)//"${remaining.days} Tage"
                remaining.hours > 0 -> getString(R.string.txt_study_runtime_hours, remaining.hours)
                remaining.minutes > 1 -> getString(R.string.txt_study_runtime_minutes, remaining.minutes)
                else -> getString(R.string.txt_study_runtime_one_minute, remaining.minutes)
            }

            txtStudyTimeRemaining.text = timeString

        } else {
            txtStudyTimeRemaining.text = getString(R.string.txt_study_is_over)
            btnFacebook.isEnabled = false
            btnInstagram.isEnabled = false
            btnX.isEnabled = false
            btnYouTube.isEnabled = false
            cbServicePermission.isEnabled = false
            btnStartService.isEnabled = false
            btnStopService.isEnabled = false

            if (isStudyComplete){
                btnSendData.isEnabled = false
                txtStudyTimeRemaining.text = getString(R.string.txt_study_is_over_uninstall)
                sayThankYou()
            }
        }
    }

    private fun activateServiceButtons(){
        val activation = loadPrefBoolean(this, Config.KEY_SERVICE_CONFIRMATION)

        if (activation == true){
            if (!SocialMediaMonitoringService.isServiceRunning){
                btnStartService.isEnabled = true
                btnStopService.isEnabled = false
            } else {
                btnStartService.isEnabled = false
                btnStopService.isEnabled = true
            }
            btnFacebook.isEnabled = false
            btnInstagram.isEnabled = false
            btnX.isEnabled = false
            btnYouTube.isEnabled = false
            btnSendData.isEnabled = true
        } else {
            btnStartService.isEnabled = false
            btnStopService.isEnabled = false
            btnFacebook.isEnabled = true
            btnInstagram.isEnabled = true
            btnX.isEnabled = true
            btnYouTube.isEnabled = true
            btnSendData.isEnabled = false
        }
    }

    private fun sendData(context: Context) {
        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)

        runBlocking {
            val deferred = async {
                nextcloudUploader.createNextcloudDirectoryWithAuthHeader(userId)

                val zipFiles = File(context.filesDir.path+"/export")
                    .listFiles { _, name -> name.endsWith(".zip") } ?: emptyArray()

                if (zipFiles.count()>0){

                    zipFiles.forEach { file ->
                        val fileSize = getZipFileSizeInKB(file.path)
                        val totalFileSize = addZipSizeToFile(context, fileSize)
                        Log.d("MainActivity", "sendZip: ${file.name}, size = ${fileSize}KB, total size = ${totalFileSize}KB")

                        val uploadFile = context.filesDir.path+"/export/"+file.name
                        val remoteFileName = file.name
                        val remoteFile = "Designate/data/${userId}/${remoteFileName}"
                        Log.d("MainActivity","remoteFile = $remoteFile")

                        val uploadStatusCode = nextcloudUploader.uploadFile(uploadFile, remoteFile)

                        Log.d("MainActivity", "StatusCode: $uploadStatusCode.value")
                        when (uploadStatusCode.value) {
                            200 -> Log.d("MainActivity","Ok - Request was successful")
                            201 -> Log.d("MainActivity","Created - Resource created")
                            204 -> Log.d("MainActivity","No Content - No content returned")
                            401 -> Log.d("MainActivity","Unauthorized - Not authorized")
                            404 -> Log.d("MainActivity","Not found - Resource not found")

                            else -> Log.d("MainActivity","Unknown Status-Code: $uploadStatusCode")
                        }

                        if (200 <= uploadStatusCode.value && uploadStatusCode.value <=204) {

                            val deleted = file.delete()
                            if (!deleted) {
                                Log.d("MainActivity", "Can't delete file: ${file.name}")
                            }
                            Toast.makeText(context,getString(R.string.toast_data_successfully_sent), Toast.LENGTH_LONG).show()
                            checkStudyIsOver(context)
                        } else {
                            Toast.makeText(context,
                                getString(R.string.toast_send_data_problem, uploadStatusCode.value), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, getString(R.string.txt_all_data_sent), Toast.LENGTH_LONG).show()
                    checkStudyIsOver(context)
                }
            }
            val result = deferred.await()
            Log.d("MainActivity", "sendZip result = $result")
        }
    }

    fun checkPermissions(): Boolean {
        var result = false
        if (!hasUsageStatsPermission() || !Settings.canDrawOverlays(this) || !hasNotificationPermission()) {
            showPermissionsDialog()
        } else
        {
            result = true
        }
        return result
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // before Android 13: no runtime permission for notifications necessary
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification granted")
        } else {
            Log.d("MainActivity", "Notification not granted")
        }
    }


    private fun showPermissionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_permissions, null)
        val usageSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switch_usage_access)
        val overlaySwitch = dialogView.findViewById<SwitchMaterial>(R.id.switch_overlay_access)
        val notificationSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switch_notification_access)

        // init switch states
        usageSwitch.isChecked = hasUsageStatsPermission()
        overlaySwitch.isChecked = Settings.canDrawOverlays(this)
        notificationSwitch.isChecked = hasNotificationPermission()

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_permissions_title))
            .setMessage(getString(R.string.dialog_permissions_text))
            .setView(dialogView)
            .setPositiveButton("OK", null) // Null listener, will be overwritten later
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                if (!hasUsageStatsPermission()) {
                    usageSwitch.isChecked = false
                    Toast.makeText(this,
                        getString(R.string.dialog_permissions_usage_stats), Toast.LENGTH_SHORT).show()

                    if (Settings.canDrawOverlays(this) and hasNotificationPermission()) {
                        showSetPermissionsManually()
                    }

                } else if (!Settings.canDrawOverlays(this)){
                    overlaySwitch.isChecked = false
                    Toast.makeText(this,
                        getString(R.string.dialog_permissions_overlay), Toast.LENGTH_SHORT).show()
                } else if (!hasNotificationPermission()) {
                    notificationSwitch.isChecked = false
                    Toast.makeText(this,
                        getString(R.string.dialog_permissions_notifications), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this,
                        getString(R.string.dialog_permissions_granted), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    checkUserID()
                }
            }
        }


        usageSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    if (intent.resolveActivity(packageManager) != null) {
                        intent.data = "package:$packageName".toUri()
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.d(
                                "MainActivity",
                                "No activity found: e = $e"
                            )
                            showSetPermissionsManually()
                        }
                    } else {
                        Log.d("MainActivity", "Problem: ACTION_USAGE_ACCESS_SETTINGS")
                    }

                }
            }
        }

        overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        }

        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasNotificationPermission()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        dialog.show()
    }

    private fun showWelcome(onComplete: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_welcome, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.button_next).setOnClickListener {
            dialog.dismiss()
            onComplete()
            checkPermissions()
        }

        dialog.show()
    }


    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }

    private fun showDataProtectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_data_protection, null)
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }

    private fun showAboutDialog() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = pInfo.versionName
            val appName = getString(R.string.app_name)

            AlertDialog.Builder(this)
                .setTitle("")
                .setMessage("$appName\n\nVersion: $versionName \n\n© 2026 Sascha Weber")
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .show()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }


    private fun showSetPermissionsManually() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_permissions_manually, null)
        val linkText = dialogView.findViewById<TextView>(R.id.txt_set_permissions_manually_link)

        linkText.setOnClickListener {
            val url = "https://tu-dresden.de/mn/psychologie/iaosp/applied-cognition/forschung/ema-studie/"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    fun checkUserID(): Boolean {
        var result = false
        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)
        if (userId == null){
            showUserIdDialog()
        } else
        {
            val txtUserId = findViewById<TextView>(R.id.txt_user_id)
            txtUserId.text = getString(R.string.txt_check_user_id, userId)
            result = true
        }
        return result
    }

    fun showUserIdDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_user_id, null)
        val editText = dialogView.findViewById<EditText>(R.id.edt_user_id)
        val linkText = dialogView.findViewById<TextView>(R.id.txt_user_id_link)

        // input filter: letters/numbers only
        editText.inputType = InputType.TYPE_CLASS_TEXT
        editText.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            if (source.matches(Regex("[a-zA-Z0-9]*"))) source else ""
        })

        linkText.setOnClickListener {
            val url = "https://tu-dresden.de/mn/psychologie/iaosp/applied-cognition/forschung/ema-studie/"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_user_id_title))
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.dialog_user_id_save), null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val input = editText.text.toString()
                when {
                    input.isEmpty() -> {
                        editText.error = getString(R.string.dialog_user_id_not_empty)
                    }
                    !input.matches(Regex("^[a-zA-Z0-9]+$")) -> {
                        editText.error = getString(R.string.dialog_user_id_only_letters_and_numbers)
                    }
                    input.length != 8 -> {
                        editText.error = "Die Benutzer-ID muss genau 8 Zeichen lang sein"
                    }
                    else -> {
                        savePrefString(this, Config.KEY_USER_ID, input)
                        val txtUserId = findViewById<TextView>(R.id.txt_user_id)
                        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)
                        txtUserId.text = getString(R.string.dialog_user_id_user_id, userId)
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }




    private fun checkStudyIsOver(context: Context){
        // check if study is over

        val remaining = getRemainingTime(this)
        if (remaining.minutes <= 0) {
            savePrefBoolean(context, Config.KEY_STUDY_COMPLETE, true)
            val btnSendData = findViewById<Button>(R.id.btnSendData)
            btnSendData.isEnabled = false
            txtStudyTimeRemaining.text = getString(R.string.txt_study_is_over_uninstall)

            // stop SocialMediaMonitoringService -> only ForeGround notifications are still running
            Intent(this@MainActivity, SocialMediaMonitoringService::class.java).also {
                it.action = SocialMediaMonitoringService.ServiceAction.STOP.name
                startService(it)
            }

            setNotificationUninstallApp()

            sayThankYou()
        }
    }

    private fun sayThankYou() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_thank_you, null)

        // activate internal link
        val textView: TextView = dialogView.findViewById(R.id.txt_thank_you)
        textView.movementMethod = LinkMovementMethod.getInstance()

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }

    private fun showLoginSocialMedia(socialMedia: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        dialogView.findViewById<TextView>(R.id.text_login).text = getString(R.string.dialog_login_social_media, socialMedia)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.button_next).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, FullscreenWebViewActivity::class.java)
            intent.putExtra("selected_social_media", socialMedia)
            intent.putExtra("recording_option", false)
            startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.button_back).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Avoid deactivating the background service by Androids Hibernation-Option

    private fun checkUnusedAppRestrictions(){
        try {
            // check, if the hibernation mode is available on this device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
                Log.d("MainActivity", "Hibernation on Android 12+ (SDK 31), your version = ${Build.VERSION.SDK_INT}")
                showHibernationDialog()
            } else
                Log.d("MainActivity", "No Hibernation on this device SDK < 31, your version = ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Hibernation-Check failed", e)
        }
    }

    private fun showHibernationDialog(){
        val dialogView = layoutInflater.inflate(R.layout.dialog_hibernation, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Zur Einstellung", null) // Null listener, will be overwritten later
            .setNegativeButton("Erledigt", null)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                //dialog.dismiss()
                openHibernationSettings()
            }

            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negativeButton.setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun openHibernationSettings() {
        try {
            // navigate to Hibernation option
            val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS").apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: searching for app info
            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Suche Designate → App-Info → 'App pausieren'", Toast.LENGTH_LONG).show()
            Log.d("MainActivity", "openHibernationSettings: error = $e")
        }
    }

    private fun showOpenBatterySettingsDialog(){
        val dialogView = layoutInflater.inflate(R.layout.dialog_battery_settings, null)
        val linkText = dialogView.findViewById<TextView>(R.id.txt_battery_settings_link)

        linkText.setOnClickListener {
            val url = "https://tu-dresden.de/mn/psychologie/iaosp/applied-cognition/forschung/ema-studie/"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Zur Einstellung", null) // Null listener, will be overwritten later
            .setNegativeButton("Erledigt", null)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                openBatterySettings(this)
            }

            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negativeButton.setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun openBatterySettings(context: Context) {
        try {
            // navigate to battery option
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: searching for app info
            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Suche Designate → App-Info → 'Akku'", Toast.LENGTH_LONG).show()
            Log.d("MainActivity", "openBatterySettings: error = $e")
        }
    }


    private fun setNotificationUninstallApp() {

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "social_media_monitoring_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Designate")
            .setContentText("Sie können die DESIGNATE-App jetzt deinstallieren.")
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Batterie-schonend
            .build()


        notificationManager.notify(99, notification)
    }

}


