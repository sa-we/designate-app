package com.ingpsy.designate

////////////////////////////////////////////////////////////
// browser view to display social media content

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Date
import java.util.Locale

class WebViewActivity : AppCompatActivity() {

    private val myWebView : WebView by lazy { findViewById(R.id.webview_id) }

    private var appLogger : AppLogger? = null
    private var touchLogger : TouchLogger? = null
    private var openTime: Long = 0L

    var selectedSocialMedia = ""
    var recordingOption = false

    private val handlerQuestionnaireTimer = Handler(Looper.getMainLooper())
    private var runnableQuestionnaireTimer: Runnable? = null

    private var questionnaireSessionCount = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_web_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myWebView.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false  // display zoom keys
            settings.domStorageEnabled = true // store passwd

            webViewClient = DesignateWebViewClient()

        }

        // register Back-Button Callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (myWebView.canGoBack()) {
                    myWebView.goBack()  // if webview can go back, do it
                } else {
                    isEnabled = false   // if not, deactivate callback temporarily
                    onBackPressedDispatcher.onBackPressed()  // execute standard Go-Back-Logic (close activity)
                }
            }
        })


        // save login information
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(myWebView, true)

        // get user id
        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)
        Log.d("WebViewActivity", "User ID: $userId")

        appLogger = AppLogger(this, userId)

        recordingOption = intent.getBooleanExtra("recording_option", false)
        Log.d("WebViewActivity", "recording_option = $recordingOption")


        handleSocialMediaIntent(intent = intent)
    }

    override fun onStart() {
        super.onStart()
        // start timer for questionnaires
        if (recordingOption) {
            openTime = System.currentTimeMillis()
            startQuestionnaireTimer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (recordingOption) {
            appLogger?.logAppEvent(this,"DESIGNATE", selectedSocialMedia, "recording","foreground")
        } else {
            appLogger?.logAppEvent(this,"DESIGNATE", selectedSocialMedia, "login","foreground")
        }
    }


    override fun onPause() {
        if (recordingOption) {
            appLogger?.logAppEvent(this,"DESIGNATE", selectedSocialMedia, "recording","background")
        } else {
            appLogger?.logAppEvent(this,"DESIGNATE", selectedSocialMedia, "login","background")
        }
        super.onPause()
    }

    override fun onStop() {
        stopQuestionnaireTimer()
        super.onStop()
    }

    override fun onDestroy() {
        Log.d("WebViewActivity", "onDestroy")
        myWebView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let {
            setIntent(it) // Important so that getIntent() will return the new intent in the future
            myWebView.clearHistory()    // clear history to avoid back navigation to old social media content
            handleSocialMediaIntent(it)
        }
    }


    private fun handleSocialMediaIntent(intent: Intent) {
        //val data = intent.getStringExtra("key")
        selectedSocialMedia = intent.getStringExtra("selected_social_media").toString()
        Log.d("WebViewActivity", "selected_social_media new = $selectedSocialMedia")

        // get user id
        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)
        Log.d("WebViewActivity", "User ID: $userId")

        touchLogger = TouchLogger(this, selectedSocialMedia, userId)

        // load social media
        when (selectedSocialMedia) {
            "Facebook" -> myWebView.loadUrl("https://www.facebook.com")
            "Instagram" -> myWebView.loadUrl("https://www.instagram.com")
            "X" -> myWebView.loadUrl("https://www.x.com")
            "YouTube" -> myWebView.loadUrl("https://m.youtube.com/")
            else -> { // Note the block
                Log.d("WebViewActivity - selectedSocialMedia", "is unknown")
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {

        val x = ev?.x
        val y = ev?.y
        val action = ev?.action
        val me = "$action, $x, $y"
        Log.d("WebViewActivity - TouchLogger: motion event ", me)

        val event = ev
        if (event != null) {
            if (recordingOption) {
                touchLogger?.logTouchEvent(event)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun startQuestionnaireTimer() {
        val questionnaireTodayCount = getTodayOpenedQuestionnaireCount(this)
        runnableQuestionnaireTimer = Runnable {
            if (canOpenQuestionnaire(this)) {
                openQuestionnaire()
                questionnaireSessionCount++
            } else {
                Log.d("WebViewActivity", "Timer: no more questionnaire today, count = $questionnaireTodayCount")
            }
            stopQuestionnaireTimer()
        }

        if (canOpenQuestionnaire(this)) {
            val questionnaireDelay: Long = if (questionnaireSessionCount == 0) {
                Config.QUESTIONNAIRE_TIMER_MS.toLong()
            } else {
                (questionnaireSessionCount+1) * Config.QUESTIONNAIRE_TIMER_MS.toLong()
            }
            handlerQuestionnaireTimer.postDelayed(runnableQuestionnaireTimer!!, questionnaireDelay)
        } else {
            Log.d("WebViewActivity", "OnStart: no more questionnaire today, count = $questionnaireTodayCount")
        }

    }

    private fun stopQuestionnaireTimer() {
        runnableQuestionnaireTimer?.let {
            handlerQuestionnaireTimer.removeCallbacks(it)
            runnableQuestionnaireTimer = null
        }
    }

    private fun openQuestionnaire() {
        //ToDo: check if avoiding fast opening is necessary
        if (System.currentTimeMillis() - openTime >= 4 * 1000) { // 10 seconds -> avoid to fast opening
            Log.d("WebViewActivity", "open questionnaire here")
            Toast.makeText(this, getString(R.string.toast_open_questionnaire), Toast.LENGTH_SHORT).show()
            val intent = Intent(this, QuestionnaireActivity::class.java)
            intent.putExtra("selected_social_media", selectedSocialMedia)
            startActivity(intent)
        } else {
            Log.d("WebViewActivity", "openQuestionnaire -> too fast")
        }
    }

    fun getTodayOpenedQuestionnaireCount(context: Context): Int {
        val prefs = context.getSharedPreferences(Config.QUESTIONNAIRE_PREFS_NAME, MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return prefs.getInt("opened_$today", 0)
    }

    private fun canOpenQuestionnaire(context: Context): Boolean {
        val count = getTodayOpenedQuestionnaireCount(context)
        return count < Config.QUESTIONNAIRES_TODAY_MAX
    }

}