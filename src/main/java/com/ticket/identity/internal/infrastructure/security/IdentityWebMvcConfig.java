package com.ticket.identity.internal.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * identity가 자기 argument resolver를 직접 등록한다.
 *
 * <p>{@link AuthenticatedMemberArgumentResolver}는 다른 module의 controller가
 * {@code com.ticket.identity.AuthenticatedMember} 파라미터를 받게 해 주는 identity 소유 구현이다.
 * 전역 설정 module이 이것을 등록해 주면 그 방향의 참조를 열기 위해 {@code @NamedInterface}가
 * 필요해지므로, 등록도 소유 module이 한다.
 *
 * <p>Spring은 {@link WebMvcConfigurer} 구현을 <b>여러 개 모아</b> 순서대로 적용하므로, module마다
 * 자기 것을 하나씩 둬도 된다 — 새 module이 자기 resolver를 추가할 때 전역 설정 파일을 고칠 필요가
 * 없다.
 */
@Configuration
class IdentityWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedMemberArgumentResolver());
    }
}
