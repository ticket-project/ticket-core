package com.ticket.metadata.internal.web;

import com.ticket.core.support.response.ApiResponse;
import com.ticket.metadata.internal.application.query.GetMetaCodesUseCase;
import com.ticket.metadata.internal.web.docs.MetaControllerDocs;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController implements MetaControllerDocs {

    private final GetMetaCodesUseCase getMetaCodesUseCase;

    @Override
    @GetMapping("/codes")
    public ApiResponse<GetMetaCodesUseCase.Output> getMetaCodes() {
        return ApiResponse.success(getMetaCodesUseCase.execute());
    }
}
