package com.ticket.core.config.security;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.app.auth.token.AuthenticatedMember;
import com.ticket.core.support.exception.CoreException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class AuthenticatedMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return AuthenticatedMember.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            final MethodParameter parameter,
            final ModelAndViewContainer mavContainer,
            final NativeWebRequest webRequest,
            final WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedMember authenticatedMember) {
            return authenticatedMember;
        }
        throw unauthorized();
    }

    private CoreException unauthorized() {
        return new CoreException(ErrorType.AUTHENTICATION_ERROR);
    }
}
