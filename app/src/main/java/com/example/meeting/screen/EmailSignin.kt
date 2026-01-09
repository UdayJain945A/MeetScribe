package com.example.meeting.screen

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.meeting.R
import com.example.meeting.Supabaseclient
import com.example.meeting.databinding.ActivityEmailSigninBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlin.math.sign

class EmailSignin : AppCompatActivity() {
    private lateinit var binding : ActivityEmailSigninBinding
    private lateinit var email : String
    private lateinit var pass : String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityEmailSigninBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSignIn.setOnClickListener {
            signinWithEmail()
        }
    }

    fun signinWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val pass = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please enter all the details", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignIn.isEnabled = false

        lifecycleScope.launch {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetworkInfo
                val isConnected = activeNetwork?.isConnectedOrConnecting == true

                if (!isConnected) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSignIn.isEnabled = true
                    Toast.makeText(this@EmailSignin, "No internet connection", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    Supabaseclient.supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = pass
                    }

                    Toast.makeText(this@EmailSignin, "Sign in successful!", Toast.LENGTH_SHORT).show()
                    navigateToMainScreen()

                } catch (signInException: Exception) {
                    when {
                        signInException.message?.contains("Invalid login credentials") == true -> {
                            // Try to sign up
                            try {
                                Supabaseclient.supabase.auth.signUpWith(Email) {
                                    this.email = email
                                    this.password = pass
                                }

                                Toast.makeText(
                                    this@EmailSignin,
                                    "Account created! Please check your email to verify your account.",
                                    Toast.LENGTH_LONG
                                ).show()
                                navigateToMainScreen()

                            } catch (signUpException: Exception) {
                                throw signUpException
                            }
                        }
                        signInException.message?.contains("Email not confirmed") == true -> {
                            Toast.makeText(
                                this@EmailSignin,
                                "Please verify your email first. Check your inbox.",
                                Toast.LENGTH_LONG
                            ).show()

                            Log.d(">>>>","${signInException.message}")
                        }
                        else -> throw signInException
                    }
                }

                binding.progressBar.visibility = View.GONE
                binding.btnSignIn.isEnabled = true

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSignIn.isEnabled = true

                Toast.makeText(
                    this@EmailSignin,
                    "Authentication failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                Log.e("SigninError", "Error: ${e.message}", e)
            }
        }
    }

    private fun navigateToMainScreen() {
        val intent = Intent(this@EmailSignin, MainScreen::class.java)
        startActivity(intent)
        finish()
    }
}