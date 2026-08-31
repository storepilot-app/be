package com.be.productimage.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ProductImageResizer {
    private static final int PRODUCT_IMAGE_SIZE = 1000;
    private static final long MAX_SOURCE_PIXEL_COUNT = 40_000_000L;

    public BufferedImage resizeToSquare(byte[] imageBytes) throws IOException {
        BufferedImage sourceImage = readValidatedImage(imageBytes);

        BufferedImage resizedImage = new BufferedImage(
                PRODUCT_IMAGE_SIZE,
                PRODUCT_IMAGE_SIZE,
                BufferedImage.TYPE_INT_RGB
        );
        drawCentered(sourceImage, resizedImage);
        return resizedImage;
    }

    private BufferedImage readValidatedImage(byte[] imageBytes) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(imageBytes)
        )) {
            if (imageInputStream == null) {
                throw new IOException("지원하지 않는 이미지 형식입니다.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IOException("지원하지 않는 이미지 형식입니다.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_SOURCE_PIXEL_COUNT) {
                    throw new IOException("이미지 해상도는 4천만 픽셀 이하만 사용할 수 있습니다.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private void drawCentered(BufferedImage sourceImage, BufferedImage targetImage) {
        Graphics2D graphics = targetImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, PRODUCT_IMAGE_SIZE, PRODUCT_IMAGE_SIZE);

            double scale = Math.min(
                    PRODUCT_IMAGE_SIZE / (double) sourceImage.getWidth(),
                    PRODUCT_IMAGE_SIZE / (double) sourceImage.getHeight()
            );
            int width = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
            int x = (PRODUCT_IMAGE_SIZE - width) / 2;
            int y = (PRODUCT_IMAGE_SIZE - height) / 2;
            graphics.drawImage(sourceImage, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
    }
}
