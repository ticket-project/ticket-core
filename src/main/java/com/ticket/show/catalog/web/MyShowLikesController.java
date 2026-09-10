package com.ticket.show.catalog.web;

import com.ticket.show.catalog.application.usecase.GetMyShowLikesUseCase;
import com.ticket.show.catalog.application.ShowLikeSummaryView;
import com.ticket.show.catalog.web.docs.MyShowLikesControllerDocs;
import com.ticket.show.catalog.web.support.cursor.ShowLikeCursorCodec;
import com.ticket.member.AuthenticatedMember;
import com.ticket.web.ApiResponse;
import com.ticket.web.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/members/me/likes}는 member가 아니라 show가 구현한다. 찜하기·찜 해제·찜 상태
 * 조회(add/remove/status)는 like module로 옮겼지만, 이 "내 찜 목록"만은 show에 남는다 —
 * 각 항목에 공연 제목·이미지·공연장 이름을 채워야 해서 show 자기 데이터를 다시 조회해야
 * 하고, 그건 like가 아니라 show만 할 수 있는 조합이다(ADR 0006 §2, ADR 0008).
 * member가 이 엔드포인트를 구현하면 {@code show -> member}(회원 확인)와
 * {@code member -> show}(찜 목록 조회)가 만나 순환이 생긴다. URL은 기존 계약을 그대로
 * 유지한다. 여러 BC의 "내 것"을 모으는 module(가칭 mypage)이 생기면 이 controller와
 * {@code GetMyShowLikesUseCase}는 그쪽으로 옮길 후보다.
 */
@RestController
@RequestMapping("/api/v1/members/me/likes")
@RequiredArgsConstructor
public class MyShowLikesController implements MyShowLikesControllerDocs {

    private final GetMyShowLikesUseCase getMyShowLikesUseCase;
    private final ShowLikeCursorCodec showLikeCursorCodec;

    @Override
    @GetMapping
    public ApiResponse<SliceResponse<ShowLikeSummaryView>> getMyLikes(
            final AuthenticatedMember member,
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20") final int size
    ) {
        final GetMyShowLikesUseCase.Input input = new GetMyShowLikesUseCase.Input(
                member.memberId(),
                showLikeCursorCodec.decode(cursor),
                size
        );
        final GetMyShowLikesUseCase.Output output = getMyShowLikesUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(),
                output.hasNext(),
                size,
                showLikeCursorCodec.encode(output.nextPosition())
        ));
    }
}
