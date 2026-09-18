/**
 * use case가 응답으로 내보내는 조합 결과({@code *View})만 모은 package다.
 *
 * <p>여기 있는 타입은 {@code Output}의 필드나 목록 항목으로 그대로 직렬화되는 <b>최종 응답 모양</b>이다. {@code @JsonProperty} 별칭까지
 * 포함해 공개 API 계약이므로 바꿀 때 클라이언트를 함께 본다.
 *
 * <p>조회가 돌려주는 projection({@code *Row})과 조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code
 * ShowCursor}·{@code ShowSort})는 {@code show.query}에 남는다 — 둘은 모양이 다르다. 예를 들어 {@code
 * ShowListItemRow}는 {@code venueId} scalar만 담고, 공연장 이름·지역을 채워 {@code ShowListItemView}를 만드는 것은 use
 * case의 일이다.
 */
@NullMarked
package com.ticket.show.usecase.view;

import org.jspecify.annotations.NullMarked;
