package com.ingpsy.designate

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class FullscreenWebViewActivity : AppCompatActivity() {

    private val myWebView: WebView by lazy { findViewById(R.id.fullscreen_webView_id) }
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var mainContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var appLogger: AppLogger? = null
    private var touchLogger: TouchLogger? = null
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

        setContentView(R.layout.activity_fullscreen_webview)

        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        mainContainer = findViewById(R.id.mainContainer)

        // WindowInsets for status bar (compatible with Android 8+)
        setupWindowInsets()

        // Light NavigationBar (modern API, deprecated-free)
        setupNavigationBar()

        // Disable Force Dark + WebView Setup
        setupWebView()

        // BACK BUTTON - fullscreen + webview + activity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    // close fullscreen
                    exitImmersiveMode()
                } else if (myWebView.canGoBack()) {
                    myWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(myWebView, true)

        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)
        Log.d("WebViewActivity", "User ID: $userId")
        appLogger = AppLogger(this, userId)

        recordingOption = intent.getBooleanExtra("recording_option", false)
        Log.d("WebViewActivity", "recording_option = $recordingOption")

        handleSocialMediaIntent(intent)
    }


    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    private fun setupNavigationBar() {
        // Modern API for Light NavigationBar (compatible with Android 15+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightNavigationBars = true
                isAppearanceLightStatusBars = true
            }
        } else {
            // Fallback for older versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        myWebView.apply {
            settings.javaScriptEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = DesignateWebViewClient()

            // WebChromeClient - activates fullscreen
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback

                    // Prevent standby in full screen mode
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                    // IMMERSIVE MODE - Hide navigation
                    enterImmersiveMode()

                    fullscreenContainer.visibility = View.VISIBLE
                    fullscreenContainer.addView(
                        customView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    mainContainer.visibility = View.GONE
                }

                override fun onHideCustomView() {
                    exitImmersiveMode()
                }
            }
        }
    }

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun exitImmersiveMode() {
        // remove standby flag
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        fullscreenContainer.removeView(customView)
        fullscreenContainer.visibility = View.GONE
        mainContainer.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && customView == null) {
            // Edge-to-edge in normal mode only
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }
    }

    // WebView lifecycle for standby protection
    override fun onResume() {
        super.onResume()
        myWebView.onResume()
        myWebView.resumeTimers()

        if (recordingOption) {
            appLogger?.logAppEvent(this, "DESIGNATE", selectedSocialMedia, "recording", "foreground")
        } else {
            appLogger?.logAppEvent(this, "DESIGNATE", selectedSocialMedia, "login", "foreground")
        }
    }

    override fun onPause() {
        myWebView.onPause()
        myWebView.pauseTimers()

        if (recordingOption) {
            appLogger?.logAppEvent(this, "DESIGNATE", selectedSocialMedia, "recording", "background")
        } else {
            appLogger?.logAppEvent(this, "DESIGNATE", selectedSocialMedia, "login", "background")
        }
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (recordingOption) {
            openTime = System.currentTimeMillis()
            startQuestionnaireTimer()
        }
    }

    override fun onStop() {
        stopQuestionnaireTimer()
        super.onStop()
    }

    override fun onDestroy() {
        Log.d("WebViewActivity", "onDestroy")
        customViewCallback?.onCustomViewHidden()
        customView = null
        myWebView.stopLoading()
        myWebView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        myWebView.clearHistory()
        handleSocialMediaIntent(intent)
    }

    private fun handleSocialMediaIntent(intent: Intent) {
        selectedSocialMedia = intent.getStringExtra("selected_social_media").toString()
        Log.d("WebViewActivity", "selected_social_media new = $selectedSocialMedia")

        val userId: String? = loadPrefString(this, Config.KEY_USER_ID)
        Log.d("WebViewActivity", "User ID: $userId")
        touchLogger = TouchLogger(this, selectedSocialMedia, userId)

        when (selectedSocialMedia) {
            "Facebook" -> myWebView.loadUrl("https://www.facebook.com")
            "Instagram" -> myWebView.loadUrl("https://www.instagram.com")
            "X" -> myWebView.loadUrl("https://www.x.com")
            "YouTube" -> myWebView.loadUrl("https://m.youtube.com/")
            else -> Log.d("WebViewActivity - selectedSocialMedia", "is unknown")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val x = ev?.x
        val y = ev?.y
        val action = ev?.action
        val me = "$action, $x, $y"
        Log.d("WebViewActivity - TouchLogger: motion event ", me)

        if (ev != null && recordingOption) {
            touchLogger?.logTouchEvent(ev)
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
                (questionnaireSessionCount + 1) * Config.QUESTIONNAIRE_TIMER_MS.toLong()
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
        if (System.currentTimeMillis() - openTime >= 4 * 1000) {
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
