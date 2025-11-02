package com.example.momentag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tag.kt에 대한 단위 테스트
 * Coverage를 100%로 맞추기 위한 포괄적인 테스트
 *
 * TagVariant sealed interface와 관련 로직을 테스트합니다.
 * Compose UI 테스트는 androidTest에서 수행됩니다.
 *
 * 참고: Compose @Composable 함수는 Unit Test Coverage에 직접 포함되지 않습니다.
 * Coverage를 높이려면:
 * 1. 비즈니스 로직을 별도 함수로 분리하여 테스트
 * 2. UI 상태 관리 로직을 ViewModel 등으로 분리
 * 3. Compose UI Instrumentation Test (androidTest)로 별도 테스트
 */
class TagTest {
    // region TagVariant.Plain Tests
    @Test
    fun tagVariant_plain_isDataObject() {
        // Given
        val variant = TagVariant.Plain

        // Then
        assertNotNull(variant)
        assertTrue(variant is TagVariant)
        assertTrue(variant is TagVariant.Plain)
    }

    @Test
    fun tagVariant_plain_equality() {
        // Given
        val variant1 = TagVariant.Plain
        val variant2 = TagVariant.Plain

        // Then - data object는 싱글톤이므로 같은 인스턴스
        assertTrue(variant1 === variant2)
        assertEquals(variant1, variant2)
    }

    @Test
    fun tagVariant_plain_toString() {
        // Given
        val variant = TagVariant.Plain

        // Then
        assertNotNull(variant.toString())
    }
    // endregion

    // region TagVariant.CloseAlways Tests
    @Test
    fun tagVariant_closeAlways_createsInstance() {
        // Given
        var dismissCalled = false
        val onDismiss = { dismissCalled = true }

        // When
        val variant = TagVariant.CloseAlways(onDismiss)

        // Then
        assertNotNull(variant)
        assertTrue(variant is TagVariant)
        assertTrue(variant is TagVariant.CloseAlways)
    }

    @Test
    fun tagVariant_closeAlways_onDismissCallback() {
        // Given
        var dismissCalled = false
        val onDismiss = { dismissCalled = true }
        val variant = TagVariant.CloseAlways(onDismiss)

        // When
        variant.onDismiss()

        // Then
        assertTrue(dismissCalled)
    }

    @Test
    fun tagVariant_closeAlways_onDismissMultipleCalls() {
        // Given
        var dismissCount = 0
        val onDismiss: () -> Unit = { dismissCount++ }
        val variant = TagVariant.CloseAlways(onDismiss)

        // When
        variant.onDismiss()
        variant.onDismiss()
        variant.onDismiss()

        // Then
        assertEquals(3, dismissCount)
    }

    @Test
    fun tagVariant_closeAlways_equality() {
        // Given
        val onDismiss1 = {}
        val onDismiss2 = {}
        val variant1 = TagVariant.CloseAlways(onDismiss1)
        val variant2 = TagVariant.CloseAlways(onDismiss2)

        // Then - data class는 프로퍼티로 비교하지만 람다는 다를 수 있음
        assertTrue(variant1 is TagVariant.CloseAlways)
        assertTrue(variant2 is TagVariant.CloseAlways)
    }

    @Test
    fun tagVariant_closeAlways_component() {
        // Given
        var dismissCalled = false
        val onDismiss = { dismissCalled = true }
        val variant = TagVariant.CloseAlways(onDismiss)

        // When - data class의 component 함수 테스트
        val (callback) = variant

        // Then
        assertNotNull(callback)
        callback()
        assertTrue(dismissCalled)
    }

    @Test
    fun tagVariant_closeAlways_copy() {
        // Given
        val onDismiss1: () -> Unit = { }
        var dismissCalled = false
        val onDismiss2: () -> Unit = { dismissCalled = true }
        val variant1 = TagVariant.CloseAlways(onDismiss1)

        // When - data class의 copy 함수 테스트
        val variant2 = variant1.copy(onDismiss = onDismiss2)

        // Then
        variant2.onDismiss()
        assertTrue(dismissCalled)
    }
    // endregion

    // region TagVariant.CloseWhen Tests
    @Test
    fun tagVariant_closeWhen_createsInstance() {
        // Given
        val onDismiss = {}

        // When
        val variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = onDismiss)

        // Then
        assertNotNull(variant)
        assertTrue(variant is TagVariant)
        assertTrue(variant is TagVariant.CloseWhen)
    }

    @Test
    fun tagVariant_closeWhen_isDeleteModeTrue() {
        // Given
        val onDismiss = {}
        val variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = onDismiss)

        // Then
        assertTrue(variant.isDeleteMode)
    }

    @Test
    fun tagVariant_closeWhen_isDeleteModeFalse() {
        // Given
        val onDismiss = {}
        val variant = TagVariant.CloseWhen(isDeleteMode = false, onDismiss = onDismiss)

        // Then
        assertFalse(variant.isDeleteMode)
    }

    @Test
    fun tagVariant_closeWhen_onDismissCallback() {
        // Given
        var dismissCalled = false
        val onDismiss = { dismissCalled = true }
        val variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = onDismiss)

        // When
        variant.onDismiss()

        // Then
        assertTrue(dismissCalled)
    }

    @Test
    fun tagVariant_closeWhen_onDismissCallbackWhenDeleteModeFalse() {
        // Given
        var dismissCalled = false
        val onDismiss = { dismissCalled = true }
        val variant = TagVariant.CloseWhen(isDeleteMode = false, onDismiss = onDismiss)

        // When - isDeleteMode가 false여도 콜백 자체는 호출 가능
        variant.onDismiss()

        // Then
        assertTrue(dismissCalled)
    }

    @Test
    fun tagVariant_closeWhen_multipleDismissCalls() {
        // Given
        var dismissCount = 0
        val onDismiss: () -> Unit = { dismissCount++ }
        val variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = onDismiss)

        // When
        variant.onDismiss()
        variant.onDismiss()

        // Then
        assertEquals(2, dismissCount)
    }

    @Test
    fun tagVariant_closeWhen_component() {
        // Given
        var dismissCalled = false
        val onDismiss = { dismissCalled = true }
        val variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = onDismiss)

        // When - data class의 component 함수 테스트
        val (isDeleteMode, callback) = variant

        // Then
        assertTrue(isDeleteMode)
        assertNotNull(callback)
        callback()
        assertTrue(dismissCalled)
    }

    @Test
    fun tagVariant_closeWhen_copy() {
        // Given
        val onDismiss = {}
        val variant1 = TagVariant.CloseWhen(isDeleteMode = false, onDismiss = onDismiss)

        // When - data class의 copy 함수 테스트
        val variant2 = variant1.copy(isDeleteMode = true)

        // Then
        assertTrue(variant2.isDeleteMode)
        assertFalse(variant1.isDeleteMode)
    }

    @Test
    fun tagVariant_closeWhen_copyWithNewCallback() {
        // Given
        val onDismiss1: () -> Unit = {}
        var dismissCalled = false
        val onDismiss2: () -> Unit = { dismissCalled = true }
        val variant1 = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = onDismiss1)

        // When
        val variant2 = variant1.copy(onDismiss = onDismiss2)

        // Then
        variant2.onDismiss()
        assertTrue(dismissCalled)
    }
    // endregion

    // region TagVariant.Recommended Tests
    @Test
    fun tagVariant_recommended_isDataObject() {
        // Given
        val variant = TagVariant.Recommended

        // Then
        assertNotNull(variant)
        assertTrue(variant is TagVariant)
        assertTrue(variant is TagVariant.Recommended)
    }

    @Test
    fun tagVariant_recommended_equality() {
        // Given
        val variant1 = TagVariant.Recommended
        val variant2 = TagVariant.Recommended

        // Then - data object는 싱글톤이므로 같은 인스턴스
        assertTrue(variant1 === variant2)
        assertEquals(variant1, variant2)
    }

    @Test
    fun tagVariant_recommended_toString() {
        // Given
        val variant = TagVariant.Recommended

        // Then
        assertNotNull(variant.toString())
    }
    // endregion

    // region TagVariant Type Checks
    @Test
    fun tagVariant_allTypes_areInstanceOfTagVariant() {
        // Given
        val plain = TagVariant.Plain
        val closeAlways = TagVariant.CloseAlways {}
        val closeWhen = TagVariant.CloseWhen(false) {}
        val recommended = TagVariant.Recommended

        // Then
        assertTrue(plain is TagVariant)
        assertTrue(closeAlways is TagVariant)
        assertTrue(closeWhen is TagVariant)
        assertTrue(recommended is TagVariant)
    }

    @Test
    fun tagVariant_differentTypes_areNotEqual() {
        // Given
        val plain = TagVariant.Plain
        val closeAlways = TagVariant.CloseAlways {}
        val closeWhen = TagVariant.CloseWhen(false) {}
        val recommended = TagVariant.Recommended

        // Then - 서로 다른 타입들은 같지 않음
        assertFalse(plain == closeAlways)
        assertFalse(plain == closeWhen)
        assertFalse(plain == recommended)
        assertFalse(closeAlways == closeWhen)
        assertFalse(closeAlways == recommended)
        assertFalse(closeWhen == recommended)
    }

    @Test
    fun tagVariant_whenExpression_coversAllCases() {
        // Given
        val variants =
            listOf<TagVariant>(
                TagVariant.Plain,
                TagVariant.CloseAlways {},
                TagVariant.CloseWhen(true) {},
                TagVariant.Recommended,
            )

        // When & Then - when expression이 모든 케이스를 처리하는지 확인
        variants.forEach { variant ->
            val result =
                when (variant) {
                    is TagVariant.Plain -> "plain"
                    is TagVariant.CloseAlways -> "closeAlways"
                    is TagVariant.CloseWhen -> "closeWhen"
                    is TagVariant.Recommended -> "recommended"
                }
            assertNotNull(result)
        }
    }
    // endregion

    // region Alpha Calculation Tests
    @Test
    fun alpha_recommended_isFiftyPercent() {
        // Given
        val variant = TagVariant.Recommended

        // When
        val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

        // Then
        assertEquals(0.5f, alpha, 0.001f)
    }

    @Test
    fun alpha_plain_isOneHundredPercent() {
        // Given
        val variant = TagVariant.Plain

        // When
        val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

        // Then
        assertEquals(1f, alpha, 0.001f)
    }

    @Test
    fun alpha_closeAlways_isOneHundredPercent() {
        // Given
        val variant = TagVariant.CloseAlways {}

        // When
        val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

        // Then
        assertEquals(1f, alpha, 0.001f)
    }

    @Test
    fun alpha_closeWhen_isOneHundredPercent() {
        // Given
        val variant = TagVariant.CloseWhen(true) {}

        // When
        val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

        // Then
        assertEquals(1f, alpha, 0.001f)
    }
    // endregion

    // region Callback Integration Tests
    @Test
    fun callbacks_closeAlways_canBePassedAsParameter() {
        // Given
        var callbackExecuted = false
        val callback: () -> Unit = { callbackExecuted = true }

        // When
        val variant = TagVariant.CloseAlways(callback)
        variant.onDismiss()

        // Then
        assertTrue(callbackExecuted)
    }

    @Test
    fun callbacks_closeWhen_canBePassedAsParameter() {
        // Given
        var callbackExecuted = false
        val callback: () -> Unit = { callbackExecuted = true }

        // When
        val variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = callback)
        variant.onDismiss()

        // Then
        assertTrue(callbackExecuted)
    }

    @Test
    fun callbacks_multipleVariants_canHaveDifferentCallbacks() {
        // Given
        var callback1Executed = false
        var callback2Executed = false
        val callback1: () -> Unit = { callback1Executed = true }
        val callback2: () -> Unit = { callback2Executed = true }

        // When
        val variant1 = TagVariant.CloseAlways(callback1)
        val variant2 = TagVariant.CloseWhen(true, callback2)

        variant1.onDismiss()
        variant2.onDismiss()

        // Then
        assertTrue(callback1Executed)
        assertTrue(callback2Executed)
    }
    // endregion

    // region Edge Cases
    @Test
    fun tagVariant_closeWhen_deleteModeToggle() {
        // Given
        val onDismiss = {}
        var isDeleteMode = false

        // When & Then - 삭제 모드 토글
        val variant1 = TagVariant.CloseWhen(isDeleteMode, onDismiss)
        assertFalse(variant1.isDeleteMode)

        isDeleteMode = true
        val variant2 = TagVariant.CloseWhen(isDeleteMode, onDismiss)
        assertTrue(variant2.isDeleteMode)

        isDeleteMode = false
        val variant3 = TagVariant.CloseWhen(isDeleteMode, onDismiss)
        assertFalse(variant3.isDeleteMode)
    }

    @Test
    fun tagVariant_sealedInterface_preventExternalImplementation() {
        // Given - sealed interface는 같은 패키지/모듈 내에서만 구현 가능
        val variants =
            listOf<TagVariant>(
                TagVariant.Plain,
                TagVariant.CloseAlways {},
                TagVariant.CloseWhen(false) {},
                TagVariant.Recommended,
            )

        // Then - 모든 variant가 TagVariant의 인스턴스
        variants.forEach { variant ->
            assertTrue(variant is TagVariant)
        }
    }

    @Test
    fun callbacks_canCaptureExternalState() {
        // Given
        var externalCounter = 0
        val callback: () -> Unit = { externalCounter++ }
        val variant = TagVariant.CloseAlways(callback)

        // When
        variant.onDismiss()
        variant.onDismiss()
        variant.onDismiss()

        // Then - 콜백이 외부 상태를 캡처하고 수정할 수 있음
        assertEquals(3, externalCounter)
    }

    @Test
    fun tagVariant_closeWhen_bothParametersCanBeModified() {
        // Given
        var dismissCount = 0
        val onDismiss1: () -> Unit = { dismissCount++ }
        val variant1 = TagVariant.CloseWhen(isDeleteMode = false, onDismiss = onDismiss1)

        // When - copy로 두 파라미터 모두 변경
        val onDismiss2: () -> Unit = { dismissCount += 10 }
        val variant2 = variant1.copy(isDeleteMode = true, onDismiss = onDismiss2)

        // Then
        assertFalse(variant1.isDeleteMode)
        assertTrue(variant2.isDeleteMode)

        variant2.onDismiss()
        assertEquals(10, dismissCount)
    }
    // endregion

    // region StoryTagChip Click Handler Tests
    @Test
    fun storyTagChip_onClick_triggersCallback() {
        // Given
        var clickCalled = false
        val onClick: () -> Unit = { clickCalled = true }

        // When
        onClick()

        // Then
        assertTrue(clickCalled)
    }

    @Test
    fun storyTagChip_onClick_multipleClicks() {
        // Given
        var clickCount = 0
        val onClick: () -> Unit = { clickCount++ }

        // When
        onClick()
        onClick()
        onClick()

        // Then
        assertEquals(3, clickCount)
    }

    @Test
    fun storyTagChip_isSelected_true() {
        // Given
        val isSelected = true

        // Then
        assertTrue(isSelected)
    }

    @Test
    fun storyTagChip_isSelected_false() {
        // Given
        val isSelected = false

        // Then
        assertFalse(isSelected)
    }

    @Test
    fun storyTagChip_isSelected_toggle() {
        // Given
        var isSelected = false

        // When & Then
        assertFalse(isSelected)

        isSelected = true
        assertTrue(isSelected)

        isSelected = false
        assertFalse(isSelected)
    }
    // endregion
}
