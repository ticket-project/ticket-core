package com.ticket.core.app.member.query;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetCurrentMemberUseCaseTest {

    @Mock
    private MemberFinder memberFinder;

    @InjectMocks
    private GetCurrentMemberUseCase useCase;

    @Test
    void 현재_회원_정보를_반환한다() {
        //given
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(3L);
        when(member.getEmail()).thenReturn(Email.create("user@example.com"));
        when(member.getName()).thenReturn("홍길동");
        when(member.getRole()).thenReturn(Role.MEMBER);
        when(memberFinder.findActiveMemberById(3L)).thenReturn(member);

        //when
        GetCurrentMemberUseCase.Output output = useCase.execute(new GetCurrentMemberUseCase.Input(3L));

        //then
        assertThat(output.memberId()).isEqualTo(3L);
        assertThat(output.email()).isEqualTo("user@example.com");
        assertThat(output.name()).isEqualTo("홍길동");
        assertThat(output.role()).isEqualTo("MEMBER");
    }

    @Test
    void 이메일이_null이면_빈문자열로_반환한다() {
        //given
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(3L);
        when(member.getEmail()).thenReturn(null);
        when(member.getName()).thenReturn("홍길동");
        when(member.getRole()).thenReturn(Role.MEMBER);
        when(memberFinder.findActiveMemberById(3L)).thenReturn(member);

        //when
        GetCurrentMemberUseCase.Output output = useCase.execute(new GetCurrentMemberUseCase.Input(3L));

        //then
        assertThat(output.email()).isEmpty();
    }

    @Test
    void memberId가_null이면_예외를_던진다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> new GetCurrentMemberUseCase.Input(null))
                .isInstanceOf(NullPointerException.class);
    }
}
