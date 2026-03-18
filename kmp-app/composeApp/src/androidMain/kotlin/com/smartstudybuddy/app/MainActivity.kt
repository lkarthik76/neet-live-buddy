package com.smartstudybuddy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppContextHolder.appContext = applicationContext
        setContent {
            NeetLiveBuddyApp()
        }
    }

    override fun onResume() {
        super.onResume()
        ActivityHolder.currentActivity = this
    }

    override fun onPause() {
        if (ActivityHolder.currentActivity == this) {
            ActivityHolder.currentActivity = null
        }
        super.onPause()
    }
}
