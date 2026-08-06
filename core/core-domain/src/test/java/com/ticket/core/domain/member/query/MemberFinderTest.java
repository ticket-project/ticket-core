package com.ticket.core.domain.member.query;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class MemberFinderTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberFinder memberFinder;

    @Test
    void 활성회원이_있으면_그대로_반환한다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "사용자", Role.MEMBER);
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        //when
        Member result = memberFinder.findActiveMemberById(1L);

        //then
        assertThat(result).isSameAs(member);
    }

    @Test
    void 활성회원이_없으면_찾을수없음_예외를_던진다() {
        //given
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> memberFinder.findActiveMemberById(1L))
                .isInstanceOf(NotFoundException.class)
                .satisfies(thrown -> assertThat(((NotFoundException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 활성_회원이_존재하면_존재_검증을_통과한다() {
        when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);

        memberFinder.ensureActiveMemberExists(1L);

        verify(memberRepository).existsByIdAndDeletedAtIsNull(1L);
    }

    @Test
    void 활성_회원이_존재하지_않으면_존재_검증에서_예외를_던진다() {
        when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(false);

        assertThatThrownBy(() -> memberFinder.ensureActiveMemberExists(1L))
                .isInstanceOf(NotFoundException.class)
                .satisfies(thrown -> assertThat(((NotFoundException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }
}
