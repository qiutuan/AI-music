package com.pujian.music;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AgentStudioServiceTest {
    @Test
    void parsesGeneratedTitleAndEditableLyrics() {
        var result = AgentStudioService.parseLyrics("标题：回声\n[主歌1]\n风穿过街道\n[副歌]\n你是我的光", "默认标题");
        assertEquals("回声", result.title());
        assertTrue(result.lyrics().startsWith("[主歌1]"));
    }

    @Test
    void keepsLyricsWhenModelOmitsTitle() {
        var result = AgentStudioService.parseLyrics("[主歌]\n我写下一段歌", "雨天");
        assertEquals("雨天", result.title());
        assertEquals("[主歌]\n我写下一段歌", result.lyrics());
    }
}
