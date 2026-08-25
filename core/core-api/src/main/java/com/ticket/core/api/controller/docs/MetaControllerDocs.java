package com.ticket.core.api.controller.docs;

import com.ticket.core.app.commoncode.query.GetMetaCodesUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "메타(Meta)", description = "프론트 공통 코드/메타 정보 API")
public interface MetaControllerDocs {

    @Operation(
            summary = "공통 코드 조회",
            description = "프론트에서 필요한 카테고리/장르/Enum 값들을 한 번에 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ApiResponse<GetMetaCodesUseCase.Output> getMetaCodes();
}
