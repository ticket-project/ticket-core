package com.ticket.metadata.internal.application.query;

import com.ticket.booking.BookingMetadata;
import com.ticket.catalog.CatalogMetadata;
import com.ticket.identity.IdentityMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * catalog·booking·identity의 공개 metadata 계약만으로 코드/라벨을 조합하는지 검증한다.
 * 어떤 module의 internal enum·entity·repository도 직접 참조하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetMetaCodesUseCaseTest {

    @Mock
    private CatalogMetadata catalogMetadata;

    @Mock
    private BookingMetadata bookingCatalog;

    @Mock
    private IdentityMetadata identityMetadata;

    @Test
    void catalog_booking_identity_공개_계약을_각각_한_번씩_호출해_코드를_조합한다() {
        when(catalogMetadata.categories())
                .thenReturn(List.of(new CatalogMetadata.CategoryCode(1L, "CONCERT", "콘서트")));
        when(catalogMetadata.genres())
                .thenReturn(List.of(new CatalogMetadata.GenreCode(2L, "CONCERT", "KPOP", "케이팝")));
        when(catalogMetadata.bookingStatuses())
                .thenReturn(List.of(new CatalogMetadata.CodeLabel("ON_SALE", "판매중")));
        when(catalogMetadata.saleTypes())
                .thenReturn(List.of(new CatalogMetadata.CodeLabel("GENERAL", "일반")));
        when(catalogMetadata.regions())
                .thenReturn(List.of(new CatalogMetadata.CodeLabel("SEOUL", "서울")));
        when(catalogMetadata.showSortKeys())
                .thenReturn(List.of(new CatalogMetadata.CodeLabel("popular", "인기순")));
        when(bookingCatalog.performanceSeatStates())
                .thenReturn(List.of(new BookingMetadata.CodeLabel("AVAILABLE", "예매가능")));
        when(bookingCatalog.holdStates())
                .thenReturn(List.of(new BookingMetadata.CodeLabel("ACTIVE", "선점 중")));
        when(bookingCatalog.orderStates())
                .thenReturn(List.of(new BookingMetadata.CodeLabel("PENDING", "결제 대기")));
        when(identityMetadata.roles())
                .thenReturn(List.of(new IdentityMetadata.CodeLabel("USER", "일반 회원")));
        when(identityMetadata.socialProviders())
                .thenReturn(List.of(new IdentityMetadata.CodeLabel("KAKAO", "카카오")));

        GetMetaCodesUseCase useCase = new GetMetaCodesUseCase(catalogMetadata, bookingCatalog, identityMetadata);

        GetMetaCodesUseCase.Output output = useCase.execute();

        assertThat(output.categories())
                .containsExactly(new GetMetaCodesUseCase.CategoryCodeItem(1L, "CONCERT", "콘서트"));
        assertThat(output.genres())
                .containsExactly(new GetMetaCodesUseCase.GenreCodeItem(2L, "CONCERT", "KPOP", "케이팝"));
        assertThat(output.enums().bookingStatus())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("ON_SALE", "판매중"));
        assertThat(output.enums().saleType())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("GENERAL", "일반"));
        assertThat(output.enums().region())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("SEOUL", "서울"));
        assertThat(output.enums().showSortKey())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("popular", "인기순"));
        assertThat(output.enums().performanceSeatState())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("AVAILABLE", "예매가능"));
        assertThat(output.enums().holdState())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("ACTIVE", "선점 중"));
        assertThat(output.enums().orderState())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("PENDING", "결제 대기"));
        assertThat(output.enums().role())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("USER", "일반 회원"));
        assertThat(output.enums().socialProvider())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("KAKAO", "카카오"));

        verify(catalogMetadata, times(1)).categories();
        verify(catalogMetadata, times(1)).genres();
        verify(catalogMetadata, times(1)).bookingStatuses();
        verify(catalogMetadata, times(1)).saleTypes();
        verify(catalogMetadata, times(1)).regions();
        verify(catalogMetadata, times(1)).showSortKeys();
        verify(bookingCatalog, times(1)).performanceSeatStates();
        verify(bookingCatalog, times(1)).holdStates();
        verify(bookingCatalog, times(1)).orderStates();
        verify(identityMetadata, times(1)).roles();
        verify(identityMetadata, times(1)).socialProviders();
    }
}
