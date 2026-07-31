package com.einkphoto.app.ui.aialbum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiChatStateTest {
    @Test fun ordinarySendNeverPretendsToReachAnUnavailableAiService() {
        assertEquals("请先输入想和小智说的话", chatSendNotice("", online = true, configured = false))
        assertEquals("相框未连接，消息已保留在输入框中", chatSendNotice("你好", online = false, configured = false))
        assertEquals("请先完成 AI 模型配置，当前没有发送消息", chatSendNotice("你好", online = true, configured = false))
        assertEquals("AI 对话服务尚未接入，当前没有发送消息", chatSendNotice("你好", online = true, configured = true))
    }

    @Test fun generationCanOpenConfirmationOnlyWithPromptAndConnection() {
        assertEquals("请先描述想生成的画面", chatGenerationNotice(" ", online = true))
        assertEquals("相框未连接，画面描述已保留", chatGenerationNotice("春日山水", online = false))
        assertNull(chatGenerationNotice("春日山水", online = true))
    }

    @Test fun savedConversationRoundTripsTextAndDeliveryState() {
        val original = XiaozhiChatMessage(
            id = "message-1",
            sender = ChatSender.Xiaozhi,
            text = "桃花、远山与小河 | 适合六色墨水屏",
            timeLabel = "14:31",
            deliveryState = ChatDeliveryState.Sent,
            canGenerateFromReply = true,
        )

        assertEquals(original, decodeMessage(encodeMessage(original)))
    }

    @Test fun onlyChatRouteUsesImmersiveAppChrome() {
        assertTrue(isImmersiveAiConversation(AiAlbumRoute.Chat))
        assertFalse(isImmersiveAiConversation(AiAlbumRoute.Home))
        assertFalse(isImmersiveAiConversation(AiAlbumRoute.ModelConfig))
        assertFalse(isImmersiveAiConversation(AiAlbumRoute.ModelTutorial))
    }
}
