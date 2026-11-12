package com.spyou.watch_app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class CrashReportActivity : AppCompatActivity() {
    private lateinit var reportText: TextView
    private lateinit var reportFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        reportFile = File(filesDir, ZangetsuApp.CRASH_FILE_NAME)
        reportText = findViewById(R.id.crashReportText)
        val copyButton = findViewById<Button>(R.id.copyCrashButton)
        val clearButton = findViewById<Button>(R.id.clearCrashButton)

        reportText.text = if (reportFile.exists()) reportFile.readText() else
            "No saved crash report found. If the app crashes again, reopen it to view the captured details."

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Zangetsu crash report", reportText.text))
            Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show()
        }

        clearButton.setOnClickListener {
            reportFile.delete()
            Toast.makeText(this, "Report cleared. Reopening app…", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ))
            finish()
        }
    }
}
