package com.ticket.core.app.auth.command;

import com.ticket.core.app.auth.AuthService;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.RawPassword;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class RegisterMemberUseCaseTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private RegisterMemberUseCase useCase;

    @Test
    void 입력값을_값객체로_변환해_회원가입을_호출한다() {
        //given
        when(authService.register(Email.create("user@example.com"), RawPassword.create("password123!"), "홍길동"))
                .thenReturn(11L);

        //when
        RegisterMemberUseCase.Output output =
                useCase.execute(new RegisterMemberUseCase.Input("user@example.com", "password123!", "홍길동"));

        //then
        assertThat(output.memberId()).isEqualTo(11L);
        verify(authService).register(Email.create("user@example.com"), RawPassword.create("password123!"), "홍길동");
    }
}
