package com.be.productimage.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.be.productimage.validation.RemoteImageUrlValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class RemoteImageClientTest {
    @Test
    @SuppressWarnings("unchecked")
    void explainsNotFoundResponseInUserFriendlyMessage() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        RemoteImageUrlValidator urlValidator = mock(RemoteImageUrlValidator.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        URI imageUri = URI.create("https://example.com/missing.jpg");

        when(urlValidator.validateForDownload(imageUri.toString())).thenReturn(imageUri);
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        RemoteImageClient client = new RemoteImageClient(httpClient, urlValidator);

        IOException error = assertThrows(IOException.class, () -> client.download(imageUri.toString()));
        assertEquals(
                "원본 이미지를 찾을 수 없습니다. 이미지가 삭제되었거나 URL이 변경되었는지 확인해주세요. (HTTP 404)",
                error.getMessage()
        );
    }
}
