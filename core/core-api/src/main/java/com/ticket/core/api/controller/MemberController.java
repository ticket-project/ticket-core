package com.ticket.core.api.controller;
`r`nimport com.ticket.core.app.showlike.query.model.ShowLikeSummaryView;`r`n
import com.ticket.core.api.controller.docs.MemberControllerDocs;
import com.ticket.core.app.auth.token.AuthenticatedMember;
import com.ticket.core.app.member.query.GetCurrentMemberUseCase;
import com.ticket.core.app.member.command.WithdrawCurrentMemberUseCase;
import com.ticket.core.app.showlike.query.GetMyShowLikesUseCase;
import com.ticket.core.support.response.ApiResponse;
import com.ticket.core.api.support.cursor.ShowLikeCursorCodec;
import com.ticket.core.support.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private final GetCurrentMemberUseCase getCurrentMemberUseCase;
    private final WithdrawCurrentMemberUseCase withdrawCurrentMemberUseCase;
    private final GetMyShowLikesUseCase getMyShowLikesUseCase;
    private final ShowLikeCursorCodec showLikeCursorCodec;

    @Override
    @GetMapping
    public ApiResponse<GetCurrentMemberUseCase.Output> getCurrentMember(final AuthenticatedMember member) {
        final GetCurrentMemberUseCase.Input input = new GetCurrentMemberUseCase.Input(member.memberId());
        return ApiResponse.success(getCurrentMemberUseCase.execute(input));
    }

    @Override
    @DeleteMapping
    public ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(final AuthenticatedMember member) {
        final WithdrawCurrentMemberUseCase.Input input = new WithdrawCurrentMemberUseCase.Input(member.memberId());
        final WithdrawCurrentMemberUseCase.Output output = withdrawCurrentMemberUseCase.execute(input);
        SecurityContextHolder.clearContext();
        return ApiResponse.success(output);
    }

    @Override
    @GetMapping("/me/likes")
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
