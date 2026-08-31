package com.be.productimage.validation;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RemoteImageUrlValidator {
    public URI parseHttpUri(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUrl();
        }

        try {
            URI uri = new URI(value.trim().replace(" ", "%20"));
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw invalidUrl();
            }
            return uri;
        } catch (URISyntaxException error) {
            throw invalidUrl();
        }
    }

    public URI validateForDownload(String value) {
        URI uri = parseHttpUri(value);
        validatePublicHost(uri.getHost());
        return uri;
    }

    private void validatePublicHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")) {
            throw invalidUrl();
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw invalidUrl();
                }
            }
        } catch (UnknownHostException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 서버 주소를 찾을 수 없습니다.");
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127);
        }
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private BusinessException invalidUrl() {
        return new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 URL이 비어 있거나 올바르지 않습니다.");
    }
}
