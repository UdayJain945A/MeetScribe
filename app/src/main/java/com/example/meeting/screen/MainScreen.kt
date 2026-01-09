package com.example.meeting.screen

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.meeting.R
import com.example.meeting.databinding.ActivityMainScreenBinding

class MainScreen : AppCompatActivity() {
    private lateinit var binding: ActivityMainScreenBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnStrtnew.setOnClickListener {
            var intent = Intent(this, RecordingScreen::class.java)
            startActivity(intent)
        }
        binding.btnviewsavd.setOnClickListener {
            var intent1= Intent(this, Explorefiles::class.java)
            startActivity(intent1)
        }
        binding.Exportdata.setOnClickListener {
            var intent2 = Intent(this, Exportfiles::class.java)
            startActivity(intent2)
        }
    }
}