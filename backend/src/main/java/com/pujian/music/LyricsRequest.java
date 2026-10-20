package com.pujian.music;

import jakarta.validation.constraints.NotBlank;

public record LyricsRequest(@NotBlank(message = "先写下歌曲主题") String theme, String style, String mood, String description) {}
