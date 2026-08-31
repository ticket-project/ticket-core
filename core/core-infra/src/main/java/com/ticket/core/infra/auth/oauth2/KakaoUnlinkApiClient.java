package com.ticket.core.infra.auth.oauth2;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 카카오 API 선언적 HTTP 클라이언트
 * Spring Framework 7 / Spring Boot 4의 HTTP Interface Client 기능 활용
 */
@HttpExchange
public interface KakaoApiClient {

    @PostExchange(url = "/v1/user/unlink", contentType = "application/x-www-form-urlencoded")
    void unlink(
            @RequestHeader("Authorization") String authorization,
            @RequestBody MultiValueMap<String, String> formData
    );
}
