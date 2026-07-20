package com.be.auth.service;

import com.be.auth.config.MailProperties;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class ResendEmailSender implements EmailSender {
    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

    private final MailProperties mailProperties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        if (mailProperties.apiKey() == null || mailProperties.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID, "Resend API 키가 설정되어 있지 않습니다.");
        }

        try {
            restClientBuilder.build()
                    .post()
                    .uri(RESEND_EMAILS_URL)
                    .header("Authorization", "Bearer " + mailProperties.apiKey())
                    .body(Map.of(
                            "from", mailProperties.from(),
                            "to", to,
                            "subject", "StorePilot 이메일 인증",
                            "html", verificationHtml(verificationUrl),
                            "text", "아래 링크를 열어 StorePilot 이메일 인증을 완료해주세요.\n" + verificationUrl
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.AUTH_INVALID, "인증 메일을 보내지 못했습니다.");
        }
    }

    private String verificationHtml(String verificationUrl) {
        return """
                <div>
                  <h1>StorePilot 이메일 인증</h1>
                  <p>아래 버튼을 눌러 이메일 인증을 완료해주세요.</p>
                  <p><a href="%s">이메일 인증하기</a></p>
                  <p>버튼이 열리지 않으면 아래 주소를 브라우저에 붙여넣어주세요.</p>
                  <p>%s</p>
                </div>
                """.formatted(verificationUrl, verificationUrl);
    }
}
