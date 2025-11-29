package com.example.momentag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.momentag.data.SessionExpirationManager
import com.example.momentag.repository.TokenRepository
import com.example.momentag.ui.theme.MomenTagTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var tokenRepository: TokenRepository

    @Inject
    lateinit var sessionExpirationManager: SessionExpirationManager

    @Inject
    lateinit var onboardingPreferences: com.example.momentag.data.OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomenTagTheme {
                AppNavigation(
                    tokenRepository = tokenRepository,
                    sessionExpirationManager = sessionExpirationManager,
                    onboardingPreferences = onboardingPreferences,
                )
            }
        }
    }
}
