package com.example.meeting.screen

import android.app.DatePickerDialog
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeting.Adapters.FileAdapter
import com.example.meeting.DataModel.FileItem
import com.example.meeting.R
import com.example.meeting.Supabaseclient
import com.example.meeting.databinding.ActivityExportfilesBinding
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.text.insert

class Exportfiles : AppCompatActivity() {
    private lateinit var binding2: ActivityExportfilesBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding2 = ActivityExportfilesBinding.inflate(layoutInflater)
        setContentView(binding2.root)
        setupRecyclerView()


        binding2.btnshown2.setOnClickListener {
            val calender = Calendar.getInstance()
            val year = calender.get(Calendar.YEAR)
            val month = calender.get(Calendar.MONTH)
            val day = calender.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedyear, selectedMonth, selectedDay ->
                    val displayDate = "$selectedDay/${selectedMonth + 1}/$selectedyear"
                    binding2.textviewdate2.text = displayDate

                    // Format date for Supabase (yyyy-MM-dd)
                    val supabaseDate = String.format(
                        Locale.getDefault(),
                        "%04d-%02d-%02d",
                        selectedyear,
                        selectedMonth + 1,
                        selectedDay
                    )
                    // Call the fetch method here
                    fetchFilesFromSupabase(supabaseDate)
                },
                year, month, day
            )
            datePickerDialog.show()
        }
    }

    fun setupRecyclerView() {
        binding2.recyclerViewFiles2.layoutManager = LinearLayoutManager(this)
    }

    private fun fetchFilesFromSupabase(date: String) {
        val userid = Supabaseclient.getCurrentUserId()
        if (userid == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show()
        }
        binding2.progressBar2.visibility = View.VISIBLE
        binding2.recyclerViewFiles2.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bucket = Supabaseclient.supabase.storage.from("notes_bucket")
                //    val folderPath = "transcripts/$date"

                val folderPath = "users/$userid/transcripts/$date"

                Log.d("Supabase", "Fetching files from: $folderPath")

                // List all files in the date folder
                val files = bucket.list(folderPath)

                Log.d("Supabase", "Found ${files.size} files")

                val fileItems = files.map { file ->
                    // Handle the createdAt field - it might be a String or need different formatting
                    val createdAtString = try {
                        when (val createdAt = file.createdAt) {
                            is String -> createdAt
                            is java.util.Date -> SimpleDateFormat(
                                "dd/MM/yyyy HH:mm",
                                Locale.getDefault()
                            ).format(createdAt)

                            else -> "Unknown date"
                        }
                    } catch (e: Exception) {
                        Log.e("Supabase", "Error formatting date: ${e.message}")
                        "Unknown date"
                    }

                    FileItem(
                        name = file.name,
                        path = "$folderPath/${file.name}",
                        createdAt = createdAtString
                    )
                }

                withContext(Dispatchers.Main) {
                    binding2.progressBar2.visibility = View.GONE

                    if (fileItems.isEmpty()) {
                        Toast.makeText(
                            this@Exportfiles,
                            "No files found for this date",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        binding2.recyclerViewFiles2.visibility = View.VISIBLE
                        val adapter = FileAdapter(fileItems) { file ->
                            /// openFile(file)
                            exportToWordFile(file)
                        }
                        binding2.recyclerViewFiles2.adapter = adapter
                        Log.d("Supabase", "Adapter set with ${fileItems.size} items")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding2.progressBar2.visibility = View.GONE
                    Toast.makeText(
                        this@Exportfiles,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("Supabase", "Error fetching files", e)
                }
            }
        }
    }
    private fun exportToWordFile(file: FileItem) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            exportToWordFileModern(file)
        } else {
            exportToWordFileLegacy(file)
        }
    }

    // For Android 10+ (API 29+)
    private fun exportToWordFileModern(file: FileItem) {
        binding2.progressBar2.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bucket = Supabaseclient.supabase.storage.from("notes_bucket")
                val fileContent = bucket.downloadAuthenticated(file.path).decodeToString()

                // Create Word document
                val document = XWPFDocument()
                val paragraph = document.createParagraph()
                val run = paragraph.createRun()
                run.setText(fileContent)

                // Prepare file details
                val fileName = file.name.replace(".txt", "") + ".docx"
                val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

                // Save to public Downloads folder using MediaStore
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { fileUri ->
                    resolver.openOutputStream(fileUri)?.use { outputStream ->
                        document.write(outputStream)
                    }
                    document.close()

                    withContext(Dispatchers.Main) {
                        binding2.progressBar2.visibility = View.GONE
                        Toast.makeText(
                            this@Exportfiles,
                            "File exported to Downloads: $fileName",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } ?: throw Exception("Failed to create file in Downloads")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding2.progressBar2.visibility = View.GONE
                    Toast.makeText(
                        this@Exportfiles,
                        "Error exporting file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("Export", "Error", e)
                }
            }
        }
    }

    // For Android 9 and below
    private fun exportToWordFileLegacy(file: FileItem) {
        binding2.progressBar2.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bucket = Supabaseclient.supabase.storage.from("notes_bucket")
                val fileContent = bucket.downloadAuthenticated(file.path).decodeToString()

                // Create Word document
                val document = XWPFDocument()
                val paragraph = document.createParagraph()
                val run = paragraph.createRun()
                run.setText(fileContent)

                // Save to public Downloads folder
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val fileName = file.name.replace(".txt", "") + ".docx"
                val outputFile = File(downloadsDir, fileName)

                FileOutputStream(outputFile).use { outputStream ->
                    document.write(outputStream)
                }
                document.close()

                // Scan file to make it visible in file manager
                android.media.MediaScannerConnection.scanFile(
                    this@Exportfiles,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    null
                )

                withContext(Dispatchers.Main) {
                    binding2.progressBar2.visibility = View.GONE
                    Toast.makeText(
                        this@Exportfiles,
                        "File exported to Downloads: $fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding2.progressBar2.visibility = View.GONE
                    Toast.makeText(
                        this@Exportfiles,
                        "Error exporting file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("Export", "Error", e)
                }
            }
        }
    }

}

