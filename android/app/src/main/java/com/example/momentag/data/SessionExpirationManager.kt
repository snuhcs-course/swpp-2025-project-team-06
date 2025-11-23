package com.example.momentag.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionExpirationManager
    @Inject
    constructor() {
        private val _sessionExpired = MutableSharedFlow<Boolean>()
        val sessionExpired = _sessionExpired.asSharedFlow()

        private val sessionExpiredHandled = AtomicBoolean(false)

        suspend fun onSessionExpired() {
            if (sessionExpiredHandled.compareAndSet(false, true)) {
                _sessionExpired.emit(true)
            }
        }

        fun resetSessionExpiration() {
            sessionExpiredHandled.set(false)
        }
    }
