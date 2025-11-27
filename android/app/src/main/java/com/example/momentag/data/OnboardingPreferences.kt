package com.example.momentag.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OnboardingPreferences
 *
 * Manages onboarding completion status using SharedPreferences
 */
@Singleton
class OnboardingPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Check if user has completed onboarding
         */
        fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        /**
         * Mark onboarding as completed
         */
        fun setOnboardingCompleted() {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }

        /**
         * Reset onboarding status (for testing purposes)
         */
        fun resetOnboarding() {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, false).apply()
        }

        companion object {
            private const val PREFS_NAME = "onboarding_prefs"
            private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        }
    }
