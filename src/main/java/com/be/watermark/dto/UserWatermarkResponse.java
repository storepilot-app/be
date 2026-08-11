package com.be.watermark.dto;

import java.time.Instant;

public record UserWatermarkResponse(
        boolean exists,
        String originalFilename,
        long fileSize,
        String position,
        int opacity,
        int sizePercent,
        Instant updatedAt
) {
    public static UserWatermarkResponse empty() {
        return new UserWatermarkResponse(false, null, 0, "BOTTOM_RIGHT", 50, 20, null);
    }
}
