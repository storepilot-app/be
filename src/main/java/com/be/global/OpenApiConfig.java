package com.be.global;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI storePilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("StorePilot Keyword API")
                        .version("v1")
                        .description("상품 엑셀 업로드와 키워드 생성 작업을 위한 MVP API 문서입니다."));
    }
}
