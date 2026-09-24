package com.example.datawallet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import java.io.IOException
import java.util.Locale

class MainActivity : Activity() {
    private val BASE_URL = "https://data-upload-wallet.onrender.com"
    private val client = OkHttpClient()
    private val PICK = 1001
    private lateinit var status: TextView
    private lateinit var filesBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply {
            text = "📦 Data Upload Wallet"
            textSize = 24f
        }
        val upload = Button(this).apply { text = "📤 Upload File" }
        val refresh = Button(this).apply { text = "🔄 Refresh Files" }
        status = TextView(this).apply { text = "Connecting to live server..." }
        filesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(title)
        root.addView(upload)
        root.addView(refresh)
        root.addView(status)
        root.addView(filesBox)
        setContentView(root)

        upload.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, PICK)
        }
        refresh.setOnClickListener { loadFiles() }
        loadFiles()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK && resultCode == RESULT_OK) data?.data?.let { uploadFile(it) }
    }

    private fun uploadFile(uri: Uri) {
        status.text = "Uploading..."
        val fileName = getDisplayName(uri) ?: "upload.bin"

        val requestBody = object : RequestBody() {
            override fun contentType() = (contentResolver.getType(uri) ?: "application/octet-stream").toMediaTypeOrNull()
            override fun contentLength(): Long = try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (_: Exception) { -1L }

            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                } ?: throw IOException("Could not read selected file")
            }
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, requestBody)
            .build()

        val request = Request.Builder().url(BASE_URL + "/api/upload").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { status.text = "Upload failed: " + (e.message ?: "network error") }
            }
            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                runOnUiThread {
                    status.text = if (ok) "Upload complete ✅" else "Upload failed ❌"
                    if (ok) loadFiles()
                }
            }
        })
    }

    private fun loadFiles() {
        status.text = "Loading files..."
        val request = Request.Builder().url(BASE_URL + "/api/files").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { status.text = "Server connection failed" }
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    runOnUiThread { status.text = "Could not load files (" + response.code + ")" }
                    return
                }
                try {
                    val array = org.json.JSONArray(text)
                    runOnUiThread {
                        filesBox.removeAllViews()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val name = item.optString("name")
                            val size = formatSize(item.optLong("size"))
                            val button = Button(this@MainActivity).apply {
                                text = name + "\n" + size + " — Open"
                                setOnClickListener {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL + item.optString("url"))))
                                }
                            }
                            filesBox.addView(button)
                        }
                        status.text = array.length().toString() + " file(s) available"
                    }
                } catch (_: Exception) {
                    runOnUiThread { status.text = "Invalid server response" }
                }
            }
        })
    }

    private fun getDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return bytes.toString() + " B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }
}
