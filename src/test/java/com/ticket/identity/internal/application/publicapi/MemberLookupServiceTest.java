package com.ticket.identity.internal.application.publicapi;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.MemberProfile;
import com.ticket.identity.internal.domain.member.model.Email;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.model.Role;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class MemberLookupServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberLookupService service;

    @Test
    void 활성_회원의_이름과_이메일을_반환한다() {
        Member member = Member.createSocialMember(Email.create("user@example.com"), "홍길동", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));

        MemberProfile profile = service.getProfile(1L);

        assertThat(profile).isEqualTo(new MemberProfile(1L, "홍길동", "user@example.com"));
    }

    @Test
    void 탈퇴하거나_존재하지_않는_회원이면_예외를_던진다() {
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(1L))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.NOT_FOUND_DATA));
    }
}
