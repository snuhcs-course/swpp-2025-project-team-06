package com.example.momentag.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * A centralized object for managing animation specifications across the application.
 * This ensures consistency in user experience for UI transitions.
 *
 * It defines standard durations, easing curves, and pre-configured transition effects.
 */
object Animation {
    /**
     * Standard animation durations.
     */
    object Duration {
        const val SHORT = 200 // For quick feedback, like icon transformations.
        const val MEDIUM = 400 // For general-purpose transitions like fades and slides.
        const val LONG = 600 // For larger view transitions or attention-grabbing effects.
    }

    /**
     * Standard easing curves for natural-looking motion. (Easing Curve : 움직임의 속도 변화)
     */
    object Easing {
        val Standard: androidx.compose.animation.core.Easing = FastOutSlowInEasing // Default easing for most movements.
        val Emphasized: androidx.compose.animation.core.Easing = LinearOutSlowInEasing // For elements entering the screen.
    }

    /**
     * Pre-configured TweenSpec instances for convenience. (TweenSpec : 뷰의 이동, 확대 / 축소, 회전 에서 사용될 속도,타이밍 정의)
     */
    object Spec {
        fun <T> shortTween(): TweenSpec<T> = tween(durationMillis = Duration.SHORT, easing = Easing.Standard)

        fun <T> mediumTween(): TweenSpec<T> = tween(durationMillis = Duration.MEDIUM, easing = Easing.Standard)

        fun <T> longTween(): TweenSpec<T> = tween(durationMillis = Duration.LONG, easing = Easing.Standard)
    }

    /**
     * Convenience wrappers to access specs without referencing [Spec] externally. (TweenSpec : 애니메이션의 속도와 타이밍을 정의)
     */
    fun <T> shortTween(): TweenSpec<T> = Spec.shortTween()

    fun <T> mediumTween(): TweenSpec<T> = Spec.mediumTween()

    fun <T> longTween(): TweenSpec<T> = Spec.longTween()

    /**
     * Pre-defined fade transitions. (페이드 인/아웃 전환)
     */
    val DefaultFadeIn: EnterTransition = fadeIn(animationSpec = mediumTween())
    val DefaultFadeOut: ExitTransition = fadeOut(animationSpec = mediumTween())

    /**
     * Pre-defined vertical slide transitions. (수직 슬라이드 인/아웃 전환)
     */
    val DefaultSlideInVertically: EnterTransition = slideInVertically(animationSpec = mediumTween()) { it }
    val DefaultSlideOutVertically: ExitTransition = slideOutVertically(animationSpec = mediumTween()) { it }

    /**
     * Combined "enter & exit" transition for elements appearing from the bottom of the screen.
     * (화면 하단에서 나타나는 요소 (ex. Floating Button, Suggestion) 위한 결합된 "입장&퇴장" 전환)
     */
    val EnterFromBottom: EnterTransition = DefaultFadeIn + DefaultSlideInVertically
    val ExitToBottom: ExitTransition = DefaultFadeOut + DefaultSlideOutVertically

    /**
     * Faster variants for micro-interactions where a snappier feel is preferred.
     */
    val QuickFadeIn: EnterTransition = fadeIn(animationSpec = shortTween())
    val QuickFadeOut: ExitTransition = fadeOut(animationSpec = shortTween())

}
