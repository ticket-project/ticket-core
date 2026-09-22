package com.ticket.like.usecase;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.like.exception.LikeAlreadyExistsException;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.api.InputChecks;

import lombok.RequiredArgsConstructor;

/**
 * 대상 존재 확인은 이 use case의 책임이 아니다 — like는 다른 BC의 entity 존재 여부를 자기 invariant로 잡지 않는다(memberId·likeType·targetId 조합의 유일성만
 * 보장한다). 존재하지 않는 targetId를 찜해도 조용히 저장된다. 회원 활성 확인은 다르다 — JWT 인증 필터는 서명·만료만 검사하고 탈퇴 여부를 재확인하지 않으므로(탈퇴해도 access token은 만료
 * 전까지 유효하다), 탈퇴 회원이 자기 데이터를 건드리지 못하게 하려면 여기서 직접 {@link MemberLookupApi#requireActive}로 다시 확인해야 한다.
 *
 * <p>이미 찜한 상태면 다시 저장하지 않고 현재 상태만 돌려준다(멱등). 처음 찜하는 사이 동시 요청이 먼저 저장을 끝냈다면(unique 제약 위반)
 * {@link LikeAlreadyExistsException}(409, E7001)을 던진다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AddLikeUseCase {
    private final MemberLookupApi memberLookupApi;
    private final LikeRepository likeRepository;

    public record Input(Long memberId, LikeType likeType, Long targetId) {
        public Input {
            InputChecks.requirePositiveId(memberId, "memberId");
            InputChecks.requireProvided(likeType, "likeType");
            InputChecks.requirePositiveId(targetId, "targetId");
        }
    }

    /** 공개 API JSON 이름 {@code showId}는 대상이 공연뿐이던 시절의 계약이라 그대로 고정한다. */
    public record Output(@JsonProperty("showId") Long targetId, boolean liked, long likeCount) {}

    public Output execute(final Input input) {
        final Long memberId = input.memberId();
        final LikeType likeType = input.likeType();
        final Long targetId = input.targetId();
        memberLookupApi.requireActive(memberId);

        if (!likeRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId)) {
            try {
                likeRepository.like(memberId, likeType, targetId);
            } catch (final DataIntegrityViolationException e) {
                // 이 catch가 중복 찜만 잡는 근거는 LIKES에 다른 무결성 제약이 없다는 것이다 -- FK는
                // like V1·V2가 제거했고(모듈 간 FK 금지), NOT NULL 세 컬럼은 Like 생성자의
                // requireNonNull이 DB에 닿기 전에 막는다. 남는 것은 UK_LIKES_MEMBER_TARGET뿐이다.
                // 제약 이름으로 좁히려면 벤더마다 다르게 장식된 문자열을 파싱해야 해서 오히려 약해진다
                // (H2 "...UK_LIKES_MEMBER_TARGET_INDEX_n", Oracle "SCHEMA.UK_...").
                // 이 전제는 LikeRepositoryPagingTest가 실제 DB로 고정한다.
                throw new LikeAlreadyExistsException(memberId, likeType, targetId);
            }
        }

        return new Output(targetId, true, likeRepository.countByLikeTypeAndTargetId(likeType, targetId));
    }
}
