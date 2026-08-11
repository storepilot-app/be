package com.be.watermark.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.watermark.domain.UserWatermark;
import com.be.watermark.domain.WatermarkPosition;
import com.be.watermark.dto.UserWatermarkResponse;
import com.be.watermark.repository.UserWatermarkRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserWatermarkService {
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 4_000_000L;
    private static final int MIN_OPACITY = 10;
    private static final int MAX_OPACITY = 100;
    private static final int MIN_SIZE_PERCENT = 5;
    private static final int MAX_SIZE_PERCENT = 50;

    private final UserWatermarkRepository userWatermarkRepository;

    @Transactional(readOnly = true)
    public UserWatermarkResponse get(Long userId) {
        return userWatermarkRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(UserWatermarkResponse::empty);
    }

    @Transactional(readOnly = true)
    public WatermarkImage getRequiredImage(Long userId) {
        UserWatermark watermark = userWatermarkRepository.findByUserId(userId)
                .orElseThrow(() -> invalid("등록된 워터마크가 없습니다."));
        return new WatermarkImage(
                watermark.getImageData(),
                watermark.getContentType(),
                watermark.getPosition(),
                watermark.getOpacity(),
                watermark.getSizePercent()
        );
    }

    @Transactional
    public UserWatermarkResponse save(
            Long userId,
            MultipartFile file,
            String positionValue,
            int opacity,
            int sizePercent
    ) {
        WatermarkPosition position = parsePosition(positionValue);
        validateSettings(opacity, sizePercent);

        UserWatermark existing = userWatermarkRepository.findByUserId(userId).orElse(null);
        byte[] imageData;
        String contentType;
        String originalFilename;

        if (file != null && !file.isEmpty()) {
            imageData = validateAndReadImage(file);
            contentType = normalizeContentType(file.getContentType());
            originalFilename = safeFilename(file.getOriginalFilename());
        } else if (existing != null) {
            imageData = existing.getImageData();
            contentType = existing.getContentType();
            originalFilename = existing.getOriginalFilename();
        } else {
            throw invalid("워터마크 이미지 파일을 선택해주세요.");
        }

        UserWatermark watermark = existing == null
                ? UserWatermark.create(userId, imageData, contentType, originalFilename, position, opacity, sizePercent)
                : existing;
        if (existing != null) {
            watermark.update(imageData, contentType, originalFilename, position, opacity, sizePercent);
        }
        return toResponse(userWatermarkRepository.save(watermark));
    }

    @Transactional
    public void delete(Long userId) {
        userWatermarkRepository.deleteByUserId(userId);
    }

    private byte[] validateAndReadImage(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalid("워터마크 이미지는 2MB 이하만 업로드할 수 있습니다.");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!contentType.equals("image/png") && !contentType.equals("image/jpeg")) {
            throw invalid("워터마크 이미지는 PNG 또는 JPEG 형식만 사용할 수 있습니다.");
        }
        try {
            byte[] imageData = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                throw invalid("올바른 이미지 파일이 아닙니다.");
            }
            long pixelCount = (long) image.getWidth() * image.getHeight();
            if (pixelCount > MAX_PIXEL_COUNT) {
                throw invalid("워터마크 이미지 해상도는 400만 픽셀 이하만 사용할 수 있습니다.");
            }
            return imageData;
        } catch (IOException exception) {
            throw invalid("워터마크 이미지 파일을 읽지 못했습니다.");
        }
    }

    private void validateSettings(int opacity, int sizePercent) {
        if (opacity < MIN_OPACITY || opacity > MAX_OPACITY) {
            throw invalid("워터마크 투명도는 10~100 사이여야 합니다.");
        }
        if (sizePercent < MIN_SIZE_PERCENT || sizePercent > MAX_SIZE_PERCENT) {
            throw invalid("워터마크 크기는 5~50% 사이여야 합니다.");
        }
    }

    private WatermarkPosition parsePosition(String value) {
        try {
            return WatermarkPosition.valueOf((value == null ? "" : value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("올바른 워터마크 위치를 선택해주세요.");
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "watermark" : filename.trim();
        return value.isEmpty() ? "watermark" : value;
    }

    private UserWatermarkResponse toResponse(UserWatermark watermark) {
        return new UserWatermarkResponse(
                true,
                watermark.getOriginalFilename(),
                watermark.getFileSize(),
                watermark.getPosition().name(),
                watermark.getOpacity(),
                watermark.getSizePercent(),
                watermark.getUpdatedAt()
        );
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_WATERMARK_REQUEST, message);
    }

    public record WatermarkImage(
            byte[] content,
            String contentType,
            WatermarkPosition position,
            int opacity,
            int sizePercent
    ) {
    }
}
