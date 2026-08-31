package com.be.productimage.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.be.global.exception.BusinessException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ProductImageProcessingTest {
    private final ProductImageResizer imageResizer = new ProductImageResizer();
    private final JpegImageCompressor imageCompressor = new JpegImageCompressor();

    @Test
    void resizesImageToSquareAndCompressesAsJpeg() throws Exception {
        byte[] source = createPng(200, 100);

        BufferedImage resized = imageResizer.resizeToSquare(source);
        byte[] compressed = imageCompressor.compress(resized, source.length, 100);

        assertEquals(1000, resized.getWidth());
        assertEquals(1000, resized.getHeight());
        assertTrue(compressed.length > 0);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(compressed)));
    }

    @Test
    void rejectsInvalidTargetSizePercent() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        assertThrows(BusinessException.class, () -> imageCompressor.compress(image, 100, 29));
    }

    private byte[] createPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
