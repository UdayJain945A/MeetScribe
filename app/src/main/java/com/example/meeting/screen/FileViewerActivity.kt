package com.example.meeting.screen

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.meeting.R
import com.example.meeting.databinding.ActivityFileViewerBinding

class FileViewerActivity : AppCompatActivity() {
    private lateinit var binding : ActivityFileViewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fileName = intent.getStringExtra("FILE_NAME")
        val fileContent = intent.getStringExtra("FILE_CONTENT")
        binding.tvFileName.text = fileName
        binding.tvFileContent.text = fileContent
    }
}