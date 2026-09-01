package com.ticket.core.domain.auth.oauth2;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class OAuth2UserInfoFactoryTest {

    @Test
    void google_등록아이디면_GoogleOAuth2UserInfo를_생성한다() {
        //given
        //when
        OAuth2UserInfo result = OAuth2UserInfoFactory.create("google", Map.of(
                "sub", "sub-1",
                "email", "user@example.com",
                "name", "사용자"
        ));

        //then
        assertThat(result).isInstanceOf(GoogleOAuth2UserInfo.class);
        assertThat(result.providerId()).isEqualTo("sub-1");
    }

    @Test
    void kakao_등록아이디면_KakaoOAuth2UserInfo를_생성한다() {
        //given
        //when
        OAuth2UserInfo result = OAuth2UserInfoFactory.create("kakao", Map.of(
                "id", 1L,
                "kakao_account", Map.of(
                        "email", "user@example.com",
                        "profile", Map.of("nickname", "사용자")
                )
        ));

        //then
        assertThat(result).isInstanceOf(KakaoOAuth2UserInfo.class);
        assertThat(result.providerId()).isEqualTo("1");
    }

    @Test
    void 지원하지_않는_등록아이디면_예외를_던진다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> OAuth2UserInfoFactory.create("naver", Map.of()))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }
}

