package com.ticket.metadata.internal.web;

import com.ticket.metadata.internal.application.query.GetMetaCodesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/meta/codes}의 기존 응답 키(카테고리/장르/enum code·label)를 그대로
 * 유지하는지 검증한다. catalog/booking/member 공개 계약을 조합한 이후에도 wire format은
 * 이동 전과 같아야 한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class MetaControllerContractTest {

    @Test
    void 메타_코드_api는_기존_응답_키와_code_label_계약을_유지한다() throws Exception {
        GetMetaCodesUseCase getMetaCodesUseCase = mock(GetMetaCodesUseCase.class);
        MetaController controller = new MetaController(getMetaCodesUseCase);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        GetMetaCodesUseCase.Output output = new GetMetaCodesUseCase.Output(
                List.of(new GetMetaCodesUseCase.CategoryCodeItem(1L, "CONCERT", "콘서트")),
                List.of(new GetMetaCodesUseCase.GenreCodeItem(2L, "CONCERT", "KPOP", "케이팝")),
                new GetMetaCodesUseCase.EnumCodes(
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("ON_SALE", "판매중")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("AVAILABLE", "예매가능")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("ACTIVE", "선점 중")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("PENDING", "결제 대기")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("KAKAO", "카카오")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("USER", "일반 회원")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("GENERAL", "일반")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("SEOUL", "서울")),
                        List.of(new GetMetaCodesUseCase.EnumCodeItem("popular", "인기순"))
                )
        );
        when(getMetaCodesUseCase.execute()).thenReturn(output);

        mockMvc.perform(get("/api/v1/meta/codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.categories[0].id").value(1))
                .andExpect(jsonPath("$.data.categories[0].code").value("CONCERT"))
                .andExpect(jsonPath("$.data.categories[0].name").value("콘서트"))
                .andExpect(jsonPath("$.data.genres[0].categoryCode").value("CONCERT"))
                .andExpect(jsonPath("$.data.genres[0].code").value("KPOP"))
                .andExpect(jsonPath("$.data.enums.bookingStatus[0].code").value("ON_SALE"))
                .andExpect(jsonPath("$.data.enums.bookingStatus[0].description").value("판매중"))
                .andExpect(jsonPath("$.data.enums.performanceSeatState[0].code").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.enums.holdState[0].code").value("ACTIVE"))
                .andExpect(jsonPath("$.data.enums.orderState[0].code").value("PENDING"))
                .andExpect(jsonPath("$.data.enums.socialProvider[0].code").value("KAKAO"))
                .andExpect(jsonPath("$.data.enums.role[0].code").value("USER"))
                .andExpect(jsonPath("$.data.enums.saleType[0].code").value("GENERAL"))
                .andExpect(jsonPath("$.data.enums.region[0].code").value("SEOUL"))
                .andExpect(jsonPath("$.data.enums.showSortKey[0].code").value("popular"))
                .andExpect(jsonPath("$.data.enums.showSortKey[0].description").value("인기순"))
                .andExpect(jsonPath("$.error").isEmpty());
    }
}
