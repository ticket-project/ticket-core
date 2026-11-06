package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.MemberSnapshot;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.shared.exception.NotFoundException;

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

        MemberSnapshot profile = service.getProfile(1L);

        assertThat(profile).isEqualTo(new MemberSnapshot(1L, "홍길동", "user@example.com"));
    }

    @Test
    void 탈퇴하거나_존재하지_않는_회원이면_예외를_던진다() {
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 활성_확인은_회원_엔티티를_읽지_않고_존재_여부만_묻는다() {
        when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);

        service.requireActive(1L);

        verify(memberRepository, never()).findActiveById(1L);
    }

    @Test
    void 탈퇴하거나_존재하지_않는_회원의_활성_확인은_예외를_던진다() {
        when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireActive(1L)).isInstanceOf(NotFoundException.class);
    }
}
