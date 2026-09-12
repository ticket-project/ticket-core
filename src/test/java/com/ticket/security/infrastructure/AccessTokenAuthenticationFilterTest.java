package com.ticket.security.infrastructure;

import com.ticket.member.AuthenticatedMember;
import com.ticket.member.AccessTokenReadResult;
import com.ticket.member.AccessTokenReader;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
class AccessTokenAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void downstream의_IllegalArgumentException을_토큰_오류로_오인해_filter_chain을_다시_실행하지_않는다() {
        final AccessTokenReader accessTokenReader = mock(AccessTokenReader.class);
        final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(accessTokenReader);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final AtomicInteger invocations = new AtomicInteger();
        final FilterChain downstream = (ignoredRequest, ignoredResponse) -> {
            invocations.incrementAndGet();
            throw new IllegalArgumentException("downstream failure");
        };

        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        when(accessTokenReader.read("access-token"))
                .thenReturn(AccessTokenReadResult.authenticated(new AuthenticatedMember(7L, "MEMBER")));

        assertThatThrownBy(() -> filter.doFilter(request, response, downstream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("downstream failure");
        assertThat(invocations).hasValue(1);
        assertThat(request.getAttribute("jwt.error")).isNull();
    }

    @Test
    void Authorization_header가_없으면_토큰을_읽지_않고_chain을_한_번_실행한다() throws Exception {
        final AccessTokenReader accessTokenReader = mock(AccessTokenReader.class);
        final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(accessTokenReader);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final AtomicInteger invocations = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> invocations.incrementAndGet());

        assertThat(invocations).hasValue(1);
        verify(accessTokenReader, never()).read(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void Bearer_형식이_아니면_invalid를_기록하고_chain을_한_번_실행한다() throws Exception {
        final AccessTokenReader accessTokenReader = mock(AccessTokenReader.class);
        final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(accessTokenReader);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final AtomicInteger invocations = new AtomicInteger();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic access-token");

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> invocations.incrementAndGet());

        assertThat(invocations).hasValue(1);
        assertThat(request.getAttribute("jwt.error")).isEqualTo("invalid");
        verify(accessTokenReader, never()).read(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 만료된_토큰이면_expired를_기록하고_chain을_한_번_실행한다() throws Exception {
        final AccessTokenReader accessTokenReader = mock(AccessTokenReader.class);
        final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(accessTokenReader);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final AtomicInteger invocations = new AtomicInteger();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
        when(accessTokenReader.read("expired-token")).thenReturn(AccessTokenReadResult.expired());

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> invocations.incrementAndGet());

        assertThat(invocations).hasValue(1);
        assertThat(request.getAttribute("jwt.error")).isEqualTo("expired");
    }

    @Test
    void 유효한_토큰이면_chain에서_인증주체를_볼_수_있고_완료후_context를_비운다() throws Exception {
        final AccessTokenReader accessTokenReader = mock(AccessTokenReader.class);
        final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(accessTokenReader);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final AuthenticatedMember member = new AuthenticatedMember(7L, "MEMBER");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        when(accessTokenReader.read("access-token")).thenReturn(AccessTokenReadResult.authenticated(member));

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(member));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
