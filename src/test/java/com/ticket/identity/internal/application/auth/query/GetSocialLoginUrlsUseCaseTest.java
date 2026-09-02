package com.ticket.identity.internal.application.auth.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class GetSocialLoginUrlsUseCaseTest {

    private final GetSocialLoginUrlsUseCase useCase = new GetSocialLoginUrlsUseCase();

    @Test
    void 마지막_슬래시를_정규화해_소셜_로그인_URL을_반환한다() {
        //given
        //when
        GetSocialLoginUrlsUseCase.Output output =
                useCase.execute(new GetSocialLoginUrlsUseCase.Input("https://ticket.example.com/"));

        //then
        assertThat(output.urls()).containsEntry("google", "https://ticket.example.com/api/v1/auth/oauth2/authorize/google");
        assertThat(output.urls()).containsEntry("kakao", "https://ticket.example.com/api/v1/auth/oauth2/authorize/kakao");
    }

    @Test
    void baseUrl이_비어있으면_예외를_던진다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetSocialLoginUrlsUseCase.Input(" ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void baseUrl이_null이면_예외를_던진다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetSocialLoginUrlsUseCase.Input(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }
}

