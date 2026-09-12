package com.ticket.member.oauth.infrastructure;

import com.ticket.member.account.domain.SocialProvider;
import com.ticket.member.oauth.domain.OAuth2UserInfo;
import com.ticket.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class OAuth2UserInfoMapperTest {

    @Test
    void google_raw_attribute를_정규화한다() {
        //given
        //when
        OAuth2UserInfo result = OAuth2UserInfoMapper.map("google", Map.of(
                "sub", "sub-1",
                "email", "user@example.com",
                "email_verified", true,
                "name", "사용자"
        ));

        //then
        assertThat(result.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(result.providerId()).isEqualTo("sub-1");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.name()).isEqualTo("사용자");
    }

    @Test
    void kakao_raw_attribute를_정규화한다() {
        //given
        //when
        OAuth2UserInfo result = OAuth2UserInfoMapper.map("kakao", Map.of(
                "id", 1L,
                "kakao_account", Map.of(
                        "email", "user@example.com",
                        "is_email_valid", true,
                        "is_email_verified", true,
                        "profile", Map.of("nickname", "사용자")
                )
        ));

        //then
        assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.providerId()).isEqualTo("1");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.name()).isEqualTo("사용자");
    }

    @Test
    void kakao_email은_유효성과_검증이_모두_true일_때만_검증된_것으로_본다() {
        final OAuth2UserInfo result = OAuth2UserInfoMapper.map("kakao", Map.of(
                "id", 1L,
                "kakao_account", Map.of(
                        "email", "user@example.com",
                        "is_email_valid", false,
                        "is_email_verified", true
                )
        ));

        assertThat(result.emailVerified()).isFalse();
    }

    @Test
    void 지원하지_않는_등록아이디면_예외를_던진다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> OAuth2UserInfoMapper.map("naver", Map.of()))
                .isInstanceOf(InvalidRequestException.class);
    }
}
