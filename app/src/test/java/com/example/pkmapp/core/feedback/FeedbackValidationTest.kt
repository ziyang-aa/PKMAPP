package com.example.pkmapp.core.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackValidationTest {

    @Test
    fun categoryAndStatusLabels_mapConsistentlyWithMiniProgram() {
        assertEquals("功能建议", FeedbackRepository.Category.labelFor("suggestion"))
        assertEquals("体验优化", FeedbackRepository.Category.labelFor("ux"))
        assertEquals("问题报错", FeedbackRepository.Category.labelFor("bug"))
        assertEquals("其他反馈", FeedbackRepository.Category.labelFor("unknown"))

        assertEquals("离线暂存", FeedbackRepository.statusLabel("local_saved"))
        assertEquals("已收到", FeedbackRepository.statusLabel("pending"))
        assertEquals("处理中", FeedbackRepository.statusLabel("reviewing"))
        assertEquals("已回复", FeedbackRepository.statusLabel("resolved"))
    }
}
