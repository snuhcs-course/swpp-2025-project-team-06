package com.example.momentag.data

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpirationManagerTest {
    private lateinit var sessionExpirationManager: SessionExpirationManager

    @Before
    fun setUp() {
        sessionExpirationManager = SessionExpirationManager()
    }

    @Test
    fun `onSessionExpired emits event when called first time`() =
        runTest {
            sessionExpirationManager.sessionExpired.test {
                sessionExpirationManager.onSessionExpired()

                val event = awaitItem()
                assertTrue(event)
            }
        }

    @Test
    fun `onSessionExpired does not emit duplicate events`() =
        runTest {
            sessionExpirationManager.sessionExpired.test {
                // First call should emit
                sessionExpirationManager.onSessionExpired()
                val firstEvent = awaitItem()
                assertTrue(firstEvent)

                // Second call should not emit
                sessionExpirationManager.onSessionExpired()
                expectNoEvents()
            }
        }

    @Test
    fun `onSessionExpired called multiple times only emits once`() =
        runTest {
            sessionExpirationManager.sessionExpired.test {
                // Call multiple times rapidly
                sessionExpirationManager.onSessionExpired()
                sessionExpirationManager.onSessionExpired()
                sessionExpirationManager.onSessionExpired()

                // Should only emit once
                val event = awaitItem()
                assertTrue(event)

                // No more events should be emitted
                expectNoEvents()
            }
        }

    @Test
    fun `resetSessionExpiration allows new emission`() =
        runTest {
            sessionExpirationManager.sessionExpired.test {
                // First call emits
                sessionExpirationManager.onSessionExpired()
                val firstEvent = awaitItem()
                assertTrue(firstEvent)

                // Second call without reset does not emit
                sessionExpirationManager.onSessionExpired()
                expectNoEvents()

                // Reset and call again - should emit
                sessionExpirationManager.resetSessionExpiration()
                sessionExpirationManager.onSessionExpired()
                val secondEvent = awaitItem()
                assertTrue(secondEvent)
            }
        }

    @Test
    fun `resetSessionExpiration can be called multiple times safely`() =
        runTest {
            sessionExpirationManager.sessionExpired.test {
                sessionExpirationManager.onSessionExpired()
                val firstEvent = awaitItem()
                assertTrue(firstEvent)

                // Reset multiple times
                sessionExpirationManager.resetSessionExpiration()
                sessionExpirationManager.resetSessionExpiration()
                sessionExpirationManager.resetSessionExpiration()

                // Should still work correctly
                sessionExpirationManager.onSessionExpired()
                val secondEvent = awaitItem()
                assertTrue(secondEvent)
            }
        }

    @Test
    fun `multiple collectors receive the same event`() =
        runTest {
            val events1 = mutableListOf<Boolean>()
            val events2 = mutableListOf<Boolean>()

            val job1 =
                launch {
                    sessionExpirationManager.sessionExpired.collect { event ->
                        events1.add(event)
                    }
                }

            val job2 =
                launch {
                    sessionExpirationManager.sessionExpired.collect { event ->
                        events2.add(event)
                    }
                }

            advanceUntilIdle()

            sessionExpirationManager.onSessionExpired()
            advanceUntilIdle()

            // Both collectors should receive the event
            assertEquals(1, events1.size)
            assertEquals(1, events2.size)
            assertTrue(events1[0])
            assertTrue(events2[0])

            job1.cancel()
            job2.cancel()
        }

    @Test
    fun `late subscriber does not receive past events`() =
        runTest {
            // Emit event
            sessionExpirationManager.onSessionExpired()
            advanceUntilIdle()

            // New subscriber starts collecting after event was emitted
            sessionExpirationManager.sessionExpired.test {
                // Should not receive past event (SharedFlow doesn't replay by default)
                expectNoEvents()
            }
        }

    @Test
    fun `reset and emit cycle can be repeated multiple times`() =
        runTest {
            sessionExpirationManager.sessionExpired.test {
                // First cycle
                sessionExpirationManager.onSessionExpired()
                assertTrue(awaitItem())

                // Second cycle
                sessionExpirationManager.resetSessionExpiration()
                sessionExpirationManager.onSessionExpired()
                assertTrue(awaitItem())

                // Third cycle
                sessionExpirationManager.resetSessionExpiration()
                sessionExpirationManager.onSessionExpired()
                assertTrue(awaitItem())

                expectNoEvents()
            }
        }

    @Test
    fun `concurrent calls to onSessionExpired only emit once`() =
        runTest {
            val events = mutableListOf<Boolean>()

            val collectorJob =
                launch {
                    sessionExpirationManager.sessionExpired.collect { event ->
                        events.add(event)
                    }
                }

            // Launch multiple concurrent calls
            val jobs =
                List(10) {
                    launch {
                        sessionExpirationManager.onSessionExpired()
                    }
                }

            jobs.forEach { it.join() }
            advanceUntilIdle()

            // Despite 10 concurrent calls, should only emit once
            assertEquals(1, events.size)
            assertTrue(events[0])

            collectorJob.cancel()
        }

    @Test
    fun `onSessionExpired without collectors does not throw`() =
        runTest {
            // Should not throw even without active collectors
            sessionExpirationManager.onSessionExpired()
            advanceUntilIdle()

            // Verify can still collect after
            sessionExpirationManager.resetSessionExpiration()
            sessionExpirationManager.sessionExpired.test {
                sessionExpirationManager.onSessionExpired()
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `resetSessionExpiration without prior emission works correctly`() =
        runTest {
            // Reset without ever calling onSessionExpired
            sessionExpirationManager.resetSessionExpiration()

            sessionExpirationManager.sessionExpired.test {
                sessionExpirationManager.onSessionExpired()
                assertTrue(awaitItem())
            }
        }
}
