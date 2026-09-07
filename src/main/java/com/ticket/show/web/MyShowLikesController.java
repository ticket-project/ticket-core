package com.ticket.show.web;

import com.ticket.show.application.showlike.query.GetMyShowLikesUseCase;
import com.ticket.show.application.showlike.query.model.ShowLikeSummaryView;
import com.ticket.show.web.docs.MyShowLikesControllerDocs;
import com.ticket.show.web.support.cursor.ShowLikeCursorCodec;
import com.ticket.member.AuthenticatedMember;
import com.ticket.web.ApiResponse;
import com.ticket.web.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/members/me/likes}는 member가 아니라 show가 구현한다 — 찜 데이터
 * ({@code ShowLike})를 show가 소유하므로, member가 이 엔드포인트를 구현하면
 * {@code show -> member}(회원 확인)와 {@code member -> show}(찜 목록 조회)가 만나
 * 순환이 생긴다. URL은 기존 계약을 그대로 유지한다.
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
