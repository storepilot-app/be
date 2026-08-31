package com.be.productimage.client;

import com.be.productimage.validation.RemoteImageUrlValidator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemoteImageClient {
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;

    private final HttpClient httpClient;
    private final RemoteImageUrlValidator urlValidator;

    public byte[] download(String imageUrl) throws IOException, InterruptedException {
        URI uri = urlValidator.validateForDownload(imageUrl);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpResponse<InputStream> response = send(uri);
            try (InputStream body = response.body()) {
                if (isRedirect(response.statusCode())) {
                    uri = resolveRedirect(uri, response);
                    continue;
                }
                validateResponse(response);
                return readLimited(body);
            }
        }
        throw new IOException("이미지 요청의 리다이렉트 횟수가 너무 많습니다.");
    }

    private HttpResponse<InputStream> send(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "StorePilot/1.0")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private URI resolveRedirect(URI currentUri, HttpResponse<?> response) throws IOException {
        Optional<String> location = response.headers().firstValue("Location");
        if (location.isEmpty()) {
            throw new IOException("이미지 리다이렉트 주소가 없습니다.");
        }
        return urlValidator.validateForDownload(currentUri.resolve(location.get()).toString());
    }

    private void validateResponse(HttpResponse<?> response) throws IOException {
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("이미지 요청에 실패했습니다. HTTP 상태: " + statusCode);
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > MAX_IMAGE_BYTES) {
            throw new IOException("이미지는 20MB 이하만 다운로드할 수 있습니다.");
        }

        Optional<String> contentType = response.headers().firstValue("Content-Type");
        if (contentType.isPresent()
                && !contentType.get().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IOException("이미지 응답 형식이 아닙니다: " + contentType.get());
        }
    }

    private byte[] readLimited(InputStream inputStream) throws IOException {
        byte[] content = inputStream.readNBytes(MAX_IMAGE_BYTES + 1);
        if (content.length > MAX_IMAGE_BYTES) {
            throw new IOException("이미지는 20MB 이하만 다운로드할 수 있습니다.");
        }
        return content;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }
}
