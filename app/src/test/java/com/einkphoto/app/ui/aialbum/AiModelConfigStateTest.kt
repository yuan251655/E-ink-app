package com.einkphoto.app.ui.aialbum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelConfigStateTest {
    @Test fun keyMaskExposesOnlyAValidFourCharacterSuffix() {
        assertEquals("••••••••A7K9", maskApiKeySuffix("A7K9"))
        assertNull(maskApiKeySuffix(null))
        assertNull(maskApiKeySuffix("short"))
    }

    @Test fun directModeRequiresANewOrPreviouslySavedCredential() {
        val missing = draft(mode = AiServiceMode.Direct, newKey = "")
        val supplied = draft(mode = AiServiceMode.Direct, newKey = "x".repeat(20))

        assertTrue(validateAiConfigDraft(missing, hasSavedCredential = false).any { it.label == "API Key" && it.state == AiFieldState.Invalid })
        assertTrue(validateAiConfigDraft(missing, hasSavedCredential = true).any { it.label == "API Key" && it.state == AiFieldState.Valid })
        assertTrue(validateAiConfigDraft(supplied, hasSavedCredential = false).all { it.state == AiFieldState.Valid })
        assertFalse(supplied.toString().contains("x".repeat(20)))
    }

    @Test fun gatewayValidationDoesNotRequireADeviceStoredProviderKey() {
        assertTrue(validateAiConfigDraft(draft(AiServiceMode.Gateway, ""), hasSavedCredential = false).all { it.state == AiFieldState.Valid })
    }

    @Test fun invalidUrlAndMissingModelsAreReportedSeparately() {
        val checks = validateAiConfigDraft(
            AiConfigDraft(AiServiceMode.Gateway, "火山方舟", "invalid", "", "", ""),
            hasSavedCredential = false,
        )
        assertEquals(setOf("服务地址", "对话模型", "生图模型"), checks.filter { it.state == AiFieldState.Invalid }.map { it.label }.toSet())
    }

    @Test fun configurationReturnsToAiAlbumHome() {
        assertEquals(AiAlbumRoute.Home, aiBackDestination(AiAlbumRoute.ModelConfig))
    }

    @Test fun tutorialAlwaysReturnsToConfigBeforeConfigReturnsHome() {
        val fromTutorial = aiBackDestination(AiAlbumRoute.ModelTutorial)
        assertEquals(AiAlbumRoute.ModelConfig, fromTutorial)
        assertEquals(AiAlbumRoute.Home, aiBackDestination(fromTutorial))
    }

    @Test fun tutorialProgressIsBoundedAndStepsNavigateSafely() {
        assertEquals(0, normalizeTutorialStep(-2))
        assertEquals(6, normalizeTutorialStep(99))
        assertEquals(1, nextTutorialStep(0))
        assertEquals(6, nextTutorialStep(6))
        assertEquals(0, previousTutorialStep(0))
        assertEquals(5, previousTutorialStep(6))
        assertEquals(0f, tutorialProgress(-1), 0f)
        assertEquals(3f / 7f, tutorialProgress(3), 0.0001f)
        assertEquals(1f, tutorialProgress(9), 0f)
    }

    @Test fun tutorialCompletionCanBeAddedAndRemovedWithoutDuplicates() {
        val completed = toggleTutorialStep(emptySet(), 2)
        assertEquals(setOf(2), completed)
        assertEquals(emptySet<Int>(), toggleTutorialStep(completed, 2))
        assertEquals(setOf(6), toggleTutorialStep(emptySet(), 100))
    }

    @Test fun tutorialUsesTheProductNameInsteadOfTheOldOfficialLabel() {
        assertEquals("模型配置教程", MODEL_TUTORIAL_TITLE)
        assertEquals(MODEL_TUTORIAL_TITLE, AiAlbumRoute.ModelTutorial.title)
        assertFalse(MODEL_TUTORIAL_TITLE.contains("官方配置教程"))
        assertEquals(MODEL_TUTORIAL_STEP_COUNT, modelTutorialSteps.size)
    }

    private fun draft(mode: AiServiceMode, newKey: String) = AiConfigDraft(
        mode = mode,
        provider = "火山方舟",
        serviceUrl = "https://example.invalid/api",
        chatModel = "chat-endpoint",
        imageModel = "image-endpoint",
        newApiKey = newKey,
    )
}
