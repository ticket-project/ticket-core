package com.ticket.security.http;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.exception.UnauthenticatedException;

public class AuthenticatedMemberArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return AuthenticatedMember.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            final MethodParameter parameter,
            final @Nullable ModelAndViewContainer mavContainer,
            final NativeWebRequest webRequest,
            final @Nullable WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedMember authenticatedMember) {
            return authenticatedMember;
        }
        throw unauthorized();
    }

    private UnauthenticatedException unauthorized() {
        return new UnauthenticatedException();
    }
}
