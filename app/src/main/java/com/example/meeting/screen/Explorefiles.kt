package com.example.meeting.screen

import android.app.DatePickerDialog
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.DatePicker
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
import com.example.meeting.databinding.ActivityExplorefilesBinding
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.text.format
import kotlin.text.get

class Explorefiles : AppCompatActivity() {
    private lateinit var binding: ActivityExplorefilesBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityExplorefilesBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupRecyclerView()


        binding.btnshown.setOnClickListener {
            val calender = Calendar.getInstance()
            val year = calender.get(Calendar.YEAR)
            val month = calender.get(Calendar.MONTH)
            val day = calender.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedyear, selectedMonth, selectedDay ->
                    val displayDate = "$selectedDay/${selectedMonth + 1}/$selectedyear"
                    binding.textviewdate.text = displayDate

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

    private fun setupRecyclerView() {
        binding.recyclerViewFiles.layoutManager = LinearLayoutManager(this)
    }

    private fun fetchFilesFromSupabase(date: String) {
        val userid = Supabaseclient.getCurrentUserId()
        if(userid==null){
            Toast.makeText(this,"Please sign in first",Toast.LENGTH_SHORT).show()
        }
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewFiles.visibility = View.GONE

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
                            is java.util.Date -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(createdAt)
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
                    binding.progressBar.visibility = View.GONE

                    if (fileItems.isEmpty()) {
                        Toast.makeText(
                            this@Explorefiles,
                            "No files found for this date",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        binding.recyclerViewFiles.visibility = View.VISIBLE
                        val adapter = FileAdapter(fileItems) { file ->
                            openFile(file)
                        }
                        binding.recyclerViewFiles.adapter = adapter
                        Log.d("Supabase", "Adapter set with ${fileItems.size} items")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@Explorefiles,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("Supabase", "Error fetching files", e)
                }
            }
        }
    }


    private fun openFile(file: FileItem) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bucket = Supabaseclient.supabase.storage.from("notes_bucket")
                val fileContent = bucket.downloadAuthenticated(file.path).decodeToString()

                withContext(Dispatchers.Main) {
                    // Open in a new activity to display content
                    val intent = Intent(this@Explorefiles, FileViewerActivity::class.java)
                    intent.putExtra("FILE_NAME", file.name)
                    intent.putExtra("FILE_CONTENT", fileContent)
                    startActivity(intent)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@Explorefiles,
                        "Error opening file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
