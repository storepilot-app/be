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
            throw new IOException(responseFailureMessage(statusCode));
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

    private String responseFailureMessage(int statusCode) {
        return switch (statusCode) {
            case 404 -> "원본 이미지를 찾을 수 없습니다. 이미지가 삭제되었거나 URL이 변경되었는지 확인해주세요. (HTTP 404)";
            case 401, 403 -> "이미지 서버가 접근을 허용하지 않습니다. 로그인이 필요한 이미지이거나 외부 다운로드가 차단된 주소입니다. (HTTP "
                    + statusCode + ")";
            case 429 -> "이미지 서버 요청이 너무 많습니다. 잠시 후 다시 시도해주세요. (HTTP 429)";
            default -> statusCode >= 500
                    ? "이미지 서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요. (HTTP " + statusCode + ")"
                    : "이미지를 다운로드하지 못했습니다. 이미지 URL을 확인해주세요. (HTTP " + statusCode + ")";
        };
    }
}
