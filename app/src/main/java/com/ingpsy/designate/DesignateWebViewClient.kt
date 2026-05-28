package com.ingpsy.designate

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class DesignateWebViewClient : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d("DesignateWebViewClient", "onPageStarted: $url")
    }


    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d("DesignateWebViewClient", "onPageFinished: $url")
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        Log.d("DesignateWebViewClient", "onPageCommitVisible: $url")
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String,
        failingUrl: String
    ) {
        // error handling for old Android versions 1-22
        Log.d("DesignateWebViewClient", "onReceivedError: $errorCode, Description: $description URL: $failingUrl")
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        // error handling for new Android versions 23+
        Log.d("DesignateWebViewClient", "onReceivedError: ${error.errorCode}, Description: ${error.description} URL: ${request.url}")
        super.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // catch errors for http state
        Log.d("DesignateWebViewClient", "onReceivedHttpError: $errorResponse")
    }


    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url.toString()
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            false // WebView loads URL normally
        } else {
            Toast.makeText(view?.context, view?.context?.getString(R.string.txt_dont_use_native_app), Toast.LENGTH_SHORT).show()
            true // indicates, that the event has been handled
        }
    }

}