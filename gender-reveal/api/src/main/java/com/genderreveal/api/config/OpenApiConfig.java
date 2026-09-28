package com.genderreveal.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI genderRevealOpenApi() {
        return new OpenAPI().info(new Info()
            .title("젠더리빌 API")
            .description("게스트 참여형 성별 공개 이벤트 페이지 API. 소유자 API는 owner_session 쿠키(매직링크 로그인) 필요.")
            .version("v1"));
    }
}
