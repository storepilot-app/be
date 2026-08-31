package com.be.productimage.image;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class JpegImageCompressor {
    private static final int MIN_TARGET_SIZE_PERCENT = 30;
    private static final int MAX_TARGET_SIZE_PERCENT = 100;
    private static final float MIN_JPEG_QUALITY = 0.1f;
    private static final float MAX_JPEG_QUALITY = 1.0f;
    private static final int JPEG_QUALITY_SEARCH_ITERATIONS = 8;

    public byte[] compress(
            BufferedImage image,
            long originalBytes,
            Integer targetSizePercent
    ) throws IOException {
        validateTargetSizePercent(targetSizePercent);
        long targetBytes = Math.max(1L, Math.round(originalBytes * targetSizePercent / 100.0));
        return compressToTarget(image, targetBytes);
    }

    private byte[] compressToTarget(BufferedImage image, long targetBytes) throws IOException {
        byte[] highestQuality = writeJpeg(image, MAX_JPEG_QUALITY);
        if (highestQuality.length <= targetBytes) {
            return highestQuality;
        }

        byte[] lowestQuality = writeJpeg(image, MIN_JPEG_QUALITY);
        if (lowestQuality.length > targetBytes) {
            return lowestQuality;
        }

        byte[] bestResult = lowestQuality;
        float low = MIN_JPEG_QUALITY;
        float high = MAX_JPEG_QUALITY;
        for (int iteration = 0; iteration < JPEG_QUALITY_SEARCH_ITERATIONS; iteration++) {
            float quality = (low + high) / 2;
            byte[] candidate = writeJpeg(image, quality);
            if (candidate.length <= targetBytes) {
                bestResult = candidate;
                low = quality;
            } else {
                high = quality;
            }
        }
        return bestResult;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG 이미지 인코더를 찾지 못했습니다.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private void validateTargetSizePercent(Integer targetSizePercent) {
        if (targetSizePercent == null
                || targetSizePercent < MIN_TARGET_SIZE_PERCENT
                || targetSizePercent > MAX_TARGET_SIZE_PERCENT) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "목표 용량 비율은 30~100 사이여야 합니다.");
        }
    }
}
