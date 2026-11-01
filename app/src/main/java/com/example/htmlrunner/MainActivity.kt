package com.example.htmlrunner

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
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
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutWelcome: LinearLayout
    private lateinit var textFileStatus: TextView
    private lateinit var btnPickFile: Button
    private lateinit var btnReload: Button

    private var currentFileUri: Uri? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Native file picker to select and load an HTML file
    private val openHtmlLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            currentFileUri = uri
            textFileStatus.text = getFileName(uri)
            btnReload.visibility = View.VISIBLE

            loadHtmlFromUri(uri)
        }
    }

    // Picker triggered by <input type="file"> inside the HTML
    private val webFileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult

        val resultUris: Array<Uri>? = if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data
            val clipData = data?.clipData
            if (clipData != null) {
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
        } else {
            null
        }

        filePathCallback?.onReceiveValue(resultUris)
        filePathCallback = null
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

        // Bridge to allow JS blob downloads to call native code
        webView.addJavascriptInterface(BlobDownloadInterface(), "AndroidDownloader")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBlobDownloadHandler()
            }
        }

        // File upload chooser handler
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                mWebView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                    }
                    webFileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        // Intercept standard data: URI downloads
        webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            if (url.startsWith("data:")) {
                saveDataUriToDownloads(url, mimetype)
            }
        }
    }

    // Injects a JS interceptor so blob: and data: download links send bytes to Android
    private fun injectBlobDownloadHandler() {
        val script = """
            (function() {
                var originalClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    if (this.download && this.href) {
                        if (this.href.startsWith("blob:") || this.href.startsWith("data:")) {
                            var filename = this.download || "exported_file";
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', this.href, true);
                            xhr.responseType = 'blob';
                            xhr.onload = function(e) {
                                if (this.status == 200 || (this.status == 0 && xhr.response)) {
                                    var blob = this.response;
                                    var reader = new FileReader();
                                    reader.readAsDataURL(blob);
                                    reader.onloadend = function() {
                                        var base64data = reader.result;
                                        window.AndroidDownloader.saveBase64File(base64data, filename);
                                    }
                                }
                            };
                            xhr.send();
                            return;
                        }
                    }
                    originalClick.apply(this, arguments);
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    inner class BlobDownloadInterface {
        @JavascriptInterface
        fun saveBase64File(base64Data: String, filename: String) {
            runOnUiThread {
                try {
                    val cleanBase64 = if (base64Data.contains(",")) {
                        base64Data.substringAfter(",")
                    } else {
                        base64Data
                    }
                    val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    saveBytesToDownloads(bytes, filename)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveDataUriToDownloads(dataUri: String, mimeType: String) {
        try {
            val base64Part = dataUri.substringAfter(",")
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val extension = if (mimeType.contains("json")) "json" else "txt"
            val filename = "export_${System.currentTimeMillis()}.$extension"
            saveBytesToDownloads(bytes, filename)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBytesToDownloads(bytes: ByteArray, filename: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                outputStream?.use { it.write(bytes) }
                Toast.makeText(this, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHtmlFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val htmlContent = reader.readText()

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