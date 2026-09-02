/**
 * ShowLike module: 회원의 Show 좋아요 추가·삭제·조회, {@code (memberId, showId)} 중복 방지를 소유한다.
 *
 * <p>구현은 {@code internal} 아래에 있다: {@code AddShowLikeUseCase}/{@code RemoveShowLikeUseCase}/
 * {@code GetShowLikeStatusUseCase}와 이들을 노출하는 {@code ShowLikeController}가 {@link
 * com.ticket.identity.MemberLookup}/{@link com.ticket.catalog.ShowLookup}만 참조하고 identity·catalog
 * internal entity를 참조하지 않는다. 이 module은 아직 다른 module이 쓰는 공개 계약을 두지 않는다 —
 * booking/metadata 등 어떤 module도 showlike 공개 API를 소비하지 않는다.
 *
 * <p><b>이번 Task 9에서 옮기지 못하고 legacy에 남긴 부분</b> — catalog·identity의 아직 이동하지 않은
 * legacy 코드가 showlike 내부를 직접 참조하고 있어서, 그 참조를 끊지 않고는 옮길 수 없었다. 아래
 * 클래스는 {@code com.ticket.core.*} 아래 원래 위치에 그대로 남아 {@code com.ticket.ModularityTests}의
 * legacy package 제외 predicate로 계속 검증에서 빠진다:
 *
 * <ul>
 *   <li>{@code com.ticket.core.domain.showlike.model.ShowLike}(entity, 여전히 {@code Member}/
 *       {@code Show}에 대한 {@code @ManyToOne})와 {@code com.ticket.core.domain.showlike.repository.
 *       ShowLikeRepository} — {@code catalog.internal.infrastructure.show.query.
 *       QuerydslShowDetailReadRepository}가 이 entity로부터 Querydsl이 생성하는 Q-type을 직접 import해
 *       {@code showLike.show.id}로 live join하여 공연 상세의 {@code likeCount}를 센다. {@code show}를 scalar
 *       {@code showId} column으로 바꾸면 그 join이 쓰는 {@code .show} navigable property가 사라져
 *       catalog가 컴파일되지 않는다.</li>
 *   <li>{@code com.ticket.core.app.showlike.query.GetMyShowLikesUseCase}/{@code .ShowLikeReadRepository}/
 *       {@code .model.ShowLikeSummaryView}, {@code com.ticket.core.infra.showlike.query.
 *       QuerydslShowLikeReadRepository}, {@code com.ticket.core.api.support.cursor.ShowLikeCursorCodec}
 *       — identity의 {@code MemberController}가 {@code GET /api/v1/members/me/likes}를 구현하며 이
 *       클래스들을 직접 호출한다. 이 module로 옮기면 identity → showlike, showlike → identity
 *       ({@code MemberLookup}) 양방향 참조가 생겨 module cycle이 된다.</li>
 *   <li>{@code com.ticket.core.infra.showlike.ShowLikeRepositoryAdapter}는 위 entity와 함께 legacy에
 *       남지만, scalar id만 받는 {@code like(Long memberId, Long showId)}를 새로 추가했다 — {@code
 *       EntityManager#getReference}로 FK 전용 참조를 만들어 저장하므로, 이 module의 {@code
 *       AddShowLikeUseCase}는 identity/catalog internal entity를 전혀 참조하지 않고 memberId/showId
 *       scalar 값만 이 adapter에 넘긴다. Member/Show를 향한 실제 타입 참조는 이 adapter(legacy, module
 *       검증 대상 아님) 안에만 남는다.</li>
 * </ul>
 *
 * <p>이 항목들은 identity의 {@code /me/likes} 엔드포인트를 showlike로 옮기거나 catalog의 likeCount
 * 조회 방식을 바꾸는 별도 후속 Task가 정리해야 한다. 그 전까지 위 legacy 클래스는 이 module의 일부가
 * 아니다.
 */
@ApplicationModule(displayName = "ShowLike", allowedDependencies = {"catalog", "identity"})
package com.ticket.showlike;

import org.springframework.modulith.ApplicationModule;
