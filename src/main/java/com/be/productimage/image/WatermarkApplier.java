package com.be.productimage.image;

import com.be.watermark.domain.WatermarkPosition;
import com.be.watermark.service.UserWatermarkService.WatermarkImage;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class WatermarkApplier {
    public void apply(BufferedImage productImage, WatermarkImage watermark) throws IOException {
        BufferedImage watermarkImage = ImageIO.read(new ByteArrayInputStream(watermark.content()));
        if (watermarkImage == null) {
            throw new IOException("저장된 워터마크 이미지를 읽지 못했습니다.");
        }

        WatermarkSize size = calculateSize(productImage, watermarkImage, watermark.sizePercent());
        int margin = Math.max(10, productImage.getWidth() / 50);
        WatermarkCoordinates coordinates = calculateCoordinates(
                productImage.getWidth(),
                productImage.getHeight(),
                size.width(),
                size.height(),
                margin,
                watermark.position()
        );
        draw(productImage, watermarkImage, size, coordinates, watermark.opacity());
    }

    private WatermarkSize calculateSize(
            BufferedImage productImage,
            BufferedImage watermarkImage,
            int sizePercent
    ) {
        int targetWidth = Math.max(1, productImage.getWidth() * sizePercent / 100);
        int targetHeight = Math.max(1, (int) Math.round(
                targetWidth * watermarkImage.getHeight() / (double) watermarkImage.getWidth()
        ));
        if (targetHeight > productImage.getHeight()) {
            targetHeight = productImage.getHeight();
            targetWidth = Math.max(1, (int) Math.round(
                    targetHeight * watermarkImage.getWidth() / (double) watermarkImage.getHeight()
            ));
        }
        return new WatermarkSize(targetWidth, targetHeight);
    }

    private WatermarkCoordinates calculateCoordinates(
            int imageWidth,
            int imageHeight,
            int watermarkWidth,
            int watermarkHeight,
            int margin,
            WatermarkPosition position
    ) {
        return switch (position) {
            case TOP_LEFT -> new WatermarkCoordinates(margin, margin);
            case TOP_RIGHT -> new WatermarkCoordinates(imageWidth - watermarkWidth - margin, margin);
            case CENTER -> new WatermarkCoordinates(
                    (imageWidth - watermarkWidth) / 2,
                    (imageHeight - watermarkHeight) / 2
            );
            case BOTTOM_LEFT -> new WatermarkCoordinates(margin, imageHeight - watermarkHeight - margin);
            case BOTTOM_RIGHT -> new WatermarkCoordinates(
                    imageWidth - watermarkWidth - margin,
                    imageHeight - watermarkHeight - margin
            );
        };
    }

    private void draw(
            BufferedImage productImage,
            BufferedImage watermarkImage,
            WatermarkSize size,
            WatermarkCoordinates coordinates,
            int opacity
    ) {
        Graphics2D graphics = productImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity / 100.0f));
            graphics.drawImage(
                    watermarkImage,
                    coordinates.x(),
                    coordinates.y(),
                    size.width(),
                    size.height(),
                    null
            );
        } finally {
            graphics.dispose();
        }
    }

    private record WatermarkSize(int width, int height) {
    }

    private record WatermarkCoordinates(int x, int y) {
    }
}
