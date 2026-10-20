package com.pujian.music;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MusicGenerationRequest(
    @Min(value = 10, message = "生成时长不能少于 10 秒")
    @Max(value = 600, message = "ACE-Step 单次生成最多 600 秒（10 分钟）")
    Integer durationSeconds
) {}
