package com.ticket.show.endpoint;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.web.ApiResponse;
import com.ticket.shared.web.SliceResponse;
import com.ticket.show.endpoint.docs.MyShowLikesControllerDocs;
import com.ticket.show.usecase.GetMyShowLikesUseCase;

import lombok.RequiredArgsConstructor;

/**
 * {@code /api/v1/members/me/likes}는 member가 아니라 show가 구현한다. 찜하기·찜 해제·찜 상태 조회(add/remove/status)는
 * like module로 옮겼지만, 이 "내 찜 목록"만은 show에 남는다 — 각 항목에 공연 제목·이미지·공연장 이름을 채워야 해서 show 자기 데이터를 다시 조회해야
 * 하고, 그건 like가 아니라 show만 할 수 있는 조합이다(ADR 0006 §2, ADR 0008). member가 이 엔드포인트를 구현하면 {@code show ->
 * member}(회원 확인)와 {@code member -> show}(찜 목록 조회)가 만나 순환이 생긴다. URL은 기존 계약을 그대로 유지한다. 여러 BC의 "내 것"을
 * 모으는 module(가칭 mypage)이 생기면 이 controller와 {@code GetMyShowLikesUseCase}는 그쪽으로 옮길 후보다.
 */
@RestController
@RequestMapping("/api/v1/members/me/likes")
@RequiredArgsConstructor
public class MyShowLikesController implements MyShowLikesControllerDocs {
    private final GetMyShowLikesUseCase getMyShowLikesUseCase;

    @Override
    @GetMapping
    public ApiResponse<SliceResponse<GetMyShowLikesUseCase.Item>> getMyLikes(
            final AuthenticatedMember member,
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20") final int size) {
        final GetMyShowLikesUseCase.Input input =
                new GetMyShowLikesUseCase.Input(member.memberId(), decodeCursor(cursor), size);
        final GetMyShowLikesUseCase.Output output = getMyShowLikesUseCase.execute(input);
        return ApiResponse.success(
                SliceResponse.of(
                        output.items(),
                        output.hasNext(),
                        size,
                        encodeCursor(output.nextPosition())));
    }

    /**
     * 찜 목록 커서는 마지막 찜 id를 그대로 십진수 문자열로 쓴다 -- 기존 wire 계약이다. 공연 목록 커서와 달리 정렬 키가 하나뿐이라
     * Base64+JSON({@code ShowCursorCodec})이 필요 없다.
     */
    private static @Nullable Long decodeCursor(final @Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (final NumberFormatException exception) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }

    private static @Nullable String encodeCursor(final @Nullable Long lastLikeId) {
        return lastLikeId == null ? null : String.valueOf(lastLikeId);
    }
}
