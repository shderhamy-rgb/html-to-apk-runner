package com.example.htmlrunner

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutWelcome: LinearLayout
    private lateinit var textFileStatus: TextView
    private lateinit var btnPickFile: Button
    private lateinit var btnReload: Button

    private var currentFileUri: Uri? = null

    // Native system file picker contract
    private val openHtmlLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistable permission to reload across launches
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            currentFileUri = uri
            val displayName = getFileName(uri)
            textFileStatus.text = displayName
            btnReload.visibility = View.VISIBLE

            loadHtmlFromUri(uri)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.main_webview)
        layoutWelcome = findViewById(R.id.layout_welcome)
        textFileStatus = findViewById(R.id.text_file_status)
        btnPickFile = findViewById(R.id.btn_pick_file)
        btnReload = findViewById(R.id.btn_reload)

        configureWebView()

        btnPickFile.setOnClickListener {
            // Open system picker filtering for HTML documents
            openHtmlLauncher.launch(arrayOf("text/html", "text/*"))
        }

        btnReload.setOnClickListener {
            currentFileUri?.let { uri ->
                loadHtmlFromUri(uri)
                Toast.makeText(this, "Reloaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = true
        s.displayZoomControls = false

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun loadHtmlFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val htmlContent = reader.readText()

                // Render HTML into the WebView
                webView.loadDataWithBaseURL(
                    uri.toString(),
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )

                layoutWelcome.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Selected File"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}