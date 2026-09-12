package com.ticket.security.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 전역 HTTP security가 인증 주체 argument resolver를 등록한다.
 *
 * <p>{@link AuthenticatedMemberArgumentResolver}는 다른 module의 controller가
 * {@code com.ticket.member.AuthenticatedMember} 파라미터를 받게 해 준다. member는 이 MVC 배선을
 * 모르고 인증 주체 값 계약만 제공한다.
 */
@Configuration
class SecurityWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedMemberArgumentResolver());
    }
}
