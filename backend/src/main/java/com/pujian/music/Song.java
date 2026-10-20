package com.pujian.music;

import java.time.Instant;

public record Song(
    String id, String title, String style, String mood, String theme,
    String description, String lyrics, String prompt, String audioUrl,
    Instant createdAt, Instant updatedAt
) {}
