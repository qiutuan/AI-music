package com.pujian.music;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SongInput(
    @NotBlank(message = "歌曲标题不能为空") @Size(max = 120) String title,
    @NotBlank(message = "请选择音乐风格") String style,
    String mood, String theme, String description,
    @Size(max = 10000, message = "歌词不能超过 10000 字") String lyrics,
    String prompt
) {}
