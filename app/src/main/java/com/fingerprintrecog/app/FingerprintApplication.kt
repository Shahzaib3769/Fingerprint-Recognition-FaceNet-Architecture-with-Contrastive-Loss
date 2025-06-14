package com.fingerprintrecog.app

import android.app.Application
import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

class FingerprintApplication : Application() {
    companion object {
        private lateinit var instance: FingerprintApplication

        fun getInstance(): FingerprintApplication = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }
    }
} 