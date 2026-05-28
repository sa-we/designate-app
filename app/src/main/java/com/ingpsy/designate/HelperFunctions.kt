package com.ingpsy.designate

/////////////////////////////////////////////////////////////////////////
// helper functions, e.g. to handle online status, load and save
// preferences values, handle zip files, read and write commands


import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream



fun isDeviceOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

fun loadPrefString(context: Context, entryName: String): String? {
    val sharedPref = context.getSharedPreferences(Config.APP_PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPref.getString(entryName, null)  // null if doesn't exists
}

fun savePrefString(context: Context, entryName: String, entryValue: String) {
    val sharedPref = context.getSharedPreferences(Config.APP_PREFS_NAME, Context.MODE_PRIVATE)
    sharedPref.edit {
        putString(entryName, entryValue)
    }
}

fun loadPrefBoolean(context: Context, entryName: String): Boolean? {
    val sharedPref = context.getSharedPreferences(Config.APP_PREFS_NAME, Context.MODE_PRIVATE)
    return sharedPref.getBoolean(entryName, false)  // false if doesn't exists
}

fun savePrefBoolean(context: Context, entryName: String, entryValue: Boolean) {
    val sharedPref = context.getSharedPreferences(Config.APP_PREFS_NAME, Context.MODE_PRIVATE)
    sharedPref.edit {
        putBoolean(entryName, entryValue)
    }
}

fun zipCsvFiles(context: Context): Int {
    var response = 0

    writeDeviceInfoToCSV(context)

    val csvFiles = File(context.filesDir.path)
        .listFiles { _, name -> name.endsWith(".csv") } ?: emptyArray()

    if (csvFiles.count() > 1) {  //screen_info.csv is always stored
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS", Locale.getDefault())

        //val androidID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
        val userId: String? = loadPrefString(context, "user_id")
        createSubDirectoryExport(context)
        val zipFileName = "export/${sdf.format(Date())}_$userId.zip"
        val zipFile = File(context.filesDir, zipFileName)

        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            csvFiles.forEach { file ->
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(file.name)
                    zipOut.putNextEntry(zipEntry)
                    fis.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }

        // delete csv-files
        csvFiles.forEach { file ->
            val deleted = file.delete()
            if (!deleted) {
                Log.e("DeleteCSV", "Can't delete file: ${file.name}")
            }
        }
        response = csvFiles.count()
    }
    return  response
}

private fun createSubDirectoryExport(context: Context) {
    val dirName = "export"
    val subDir = File(context.filesDir, dirName)
    if (!subDir.exists()) {
        val created = subDir.mkdirs()
        if (created) {
            Log.d("MainActivity","Directory: '$dirName' successfully created.")
        } else {
            Log.d("MainActivity","Error creating directory: '$dirName'.")
        }
    } else {
        Log.d("MainActivity","Directory '$dirName' already exists.")
    }
}

@SuppressLint("HardwareIds")
private fun writeDeviceInfoToCSV(context: Context) {
    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = pInfo.versionName
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    val androidVersion = Build.VERSION.RELEASE
    val apiLevel = Build.VERSION.SDK_INT
    val buildModel = Build.MODEL
    val buildDevice = Build.DEVICE
    val buildDisplay = Build.DISPLAY
    val metrics: DisplayMetrics = context.resources.displayMetrics
    val widthPixels = metrics.widthPixels
    val heightPixels = metrics.heightPixels
    val densityDpi = metrics.densityDpi

    // CSV file with header and data
    val csvHeader = "DesignateVersion, AndroidID, AndroidVersion, APILevel, BuildModel, BuildDevice, BuildDisplay, WidthPixels,HeightPixels,DensityDpi\n"
    val csvData = "$versionName, $androidId, $androidVersion, $apiLevel, $buildModel, $buildDevice, $buildDisplay, $widthPixels,$heightPixels,$densityDpi\n"

    // create file in storage
    val csvFile = File(context.filesDir, "device_info.csv")
    FileWriter(csvFile).use { writer ->
        writer.write(csvHeader)
        writer.write(csvData)
    }
}

fun getDaysSinceInstallation(context: Context): Long {
    return try {
        val packageInfo = context.packageManager
            .getPackageInfo(context.packageName, 0)

        // installation date in milliseconds
        val firstInstallTime = packageInfo.firstInstallTime

        // time period between installation date and now
        val diffMillis = System.currentTimeMillis() - firstInstallTime

        // convert ms into d
        TimeUnit.MILLISECONDS.toDays(diffMillis)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.d("HelperFunctions", "getDaysSinceInstallation error: ", e)
        -1 // error case
    }
}


data class RemainingTime(val days: Long, val hours: Long, val minutes: Long)
fun getRemainingTime(context: Context): RemainingTime {
    return try {
        val packageInfo = context.packageManager
            .getPackageInfo(context.packageName, 0)

        val firstInstallTime = packageInfo.firstInstallTime
        val diffMillis = System.currentTimeMillis() - firstInstallTime
        val totalStudyMillis = Config.STUDY_DAYS * 24 * 60 * 60 * 1000L  // 1 Tag = 86400000ms

        val remainingMillis = totalStudyMillis - diffMillis

        val days = TimeUnit.MILLISECONDS.toDays(remainingMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)

        RemainingTime(days, hours, minutes)

    } catch (e: PackageManager.NameNotFoundException) {
        Log.d("HelperFunctions", "getRemainingTime error: ", e)
        RemainingTime(-1, -1, -1)
    }
}

fun getZipFileSizeInKB(path: String): Long {
    val bytes = Files.size(Paths.get(path))
    val kilobytes = bytes / 1024
    return kilobytes
}

fun addZipSizeToFile(context: Context, sizeKB: Long): Long {
    val file = File(context.filesDir, "sentKB.txt")
    if (!file.exists()) {
        file.createNewFile() // create file if not exists
        file.writeText("0")
    }
    val previous = file.readText().toLongOrNull() ?: 0L
    val newValue = previous + sizeKB
    file.writeText(newValue.toString())
    return newValue
}



