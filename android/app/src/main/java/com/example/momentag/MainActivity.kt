package com.example.momentag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.momentag.ui.theme.MomenTagTheme

class  git rev-parse --verify 980a169870fadb31092f7bc6b5b87044d07708fbMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomenTagTheme {
                AppNavigation()
            }
        }
    }
}

