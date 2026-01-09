package com.example.meeting.screen

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.meeting.R
import com.example.meeting.databinding.ActivitySigninBinding

class Signin : AppCompatActivity() {
    private lateinit var binding : ActivitySigninBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySigninBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSkip.setOnClickListener {
            val intent = Intent(this, MainScreen::class.java)
            startActivity(intent)
        }
        binding.btnEmail.setOnClickListener {
            val intent = Intent(this, EmailSignin::class.java)
            startActivity(intent)
        }
    }
}