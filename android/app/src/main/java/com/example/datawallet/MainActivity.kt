package com.example.datawallet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import okhttp3.*
import java.io.IOException

class MainActivity : Activity() {
    private val BASE_URL = "http://10.0.2.2:3000"
    private val client = OkHttpClient()
    private val PICK = 1001
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32,32,32,32)
        }
        val title = TextView(this).apply { text="📦 Data Upload Wallet"; textSize=24f }
        val upload = Button(this).apply { text="📤 Upload Data" }
        status = TextView(this).apply { text="Ready" }
        box.addView(title); box.addView(upload); box.addView(status)
        setContentView(box)
        upload.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type="*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, PICK)
        }
    }

    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==PICK && resultCode==RESULT_OK) data?.data?.let { uploadFile(it) }
    }

    private fun uploadFile(uri: Uri) {
        status.text="Uploading..."
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        val name = uri.lastPathSegment ?: "upload.bin"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody())
            .build()
        val req = Request.Builder().url("$BASE_URL/api/upload").post(body).build()
        client.newCall(req).enqueue(object: Callback {
            override fun onFailure(call:Call,e:IOException) {
                runOnUiThread{status.text="Upload failed: " + e.message}
            }
            override fun onResponse(call:Call,response:Response) {
                runOnUiThread{status.text=if(response.isSuccessful) "Upload complete ✅" else "Upload failed ❌"}
                response.close()
            }
        })
    }
}
