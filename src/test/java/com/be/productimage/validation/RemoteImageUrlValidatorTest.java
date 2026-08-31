package com.be.productimage.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.be.global.exception.BusinessException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class RemoteImageUrlValidatorTest {
    private final RemoteImageUrlValidator validator = new RemoteImageUrlValidator();

    @Test
    void parsesHttpUrlAndEncodesSpaces() {
        URI uri = validator.parseHttpUri("https://example.com/product image.jpg");

        assertEquals("https://example.com/product%20image.jpg", uri.toString());
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(BusinessException.class, () -> validator.parseHttpUri("file:///etc/passwd"));
    }

    @Test
    void rejectsLocalhostForDownload() {
        assertThrows(BusinessException.class, () -> validator.validateForDownload("http://localhost/image.jpg"));
    }
}
