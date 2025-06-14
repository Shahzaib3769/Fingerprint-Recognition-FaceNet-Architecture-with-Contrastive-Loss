package com.fingerprintrecog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)
        
        // Add fade-in animation to the icon
        val thumbIcon = findViewById<ImageView>(R.id.thumbIcon)
        val appName = findViewById<TextView>(R.id.appName)
        
        // Start MainActivity after a short delay
        android.os.Handler(mainLooper).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2000) // 2 seconds delay
    }
} 