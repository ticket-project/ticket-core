package com.ticket.metadata.internal.application.query;

import com.ticket.booking.BookingMetadata;
import com.ticket.catalog.CatalogMetadata;
import com.ticket.member.MemberMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * 프론트가 한 번에 조회하는 공통 코드/Enum을 {@link CatalogMetadata}, {@link BookingMetadata},
 * {@link MemberMetadata}에서 각각 조합한다. 어떤 module의 internal enum·entity·repository도
 * 직접 import하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMetaCodesUseCase {

    private final CatalogMetadata catalogMetadata;
    private final BookingMetadata bookingMetadata;
    private final MemberMetadata memberMetadata;

    public record CategoryCodeItem(Long id, String code, String name) {
    }

    public record GenreCodeItem(Long id, String categoryCode, String code, String name) {
    }

    public record EnumCodeItem(String code, String description) {
    }

    public record EnumCodes(
            List<EnumCodeItem> bookingStatus,
            List<EnumCodeItem> performanceSeatState,
            List<EnumCodeItem> holdState,
            List<EnumCodeItem> orderState,
            List<EnumCodeItem> socialProvider,
            List<EnumCodeItem> role,
            List<EnumCodeItem> saleType,
            List<EnumCodeItem> region,
            List<EnumCodeItem> showSortKey
    ) {
    }

    public record Output(
            List<CategoryCodeItem> categories,
            List<GenreCodeItem> genres,
            EnumCodes enums
    ) {
    }

    public Output execute() {
        final List<CategoryCodeItem> categories = catalogMetadata.categories().stream()
                .map(this::toCategoryCodeItem)
                .toList();

        final List<GenreCodeItem> genres = catalogMetadata.genres().stream()
                .map(this::toGenreCodeItem)
                .toList();

        final EnumCodes enums = new EnumCodes(
                toEnumCodeItems(catalogMetadata.bookingStatuses(), CatalogMetadata.CodeLabel::code, CatalogMetadata.CodeLabel::label),
                toEnumCodeItems(bookingMetadata.performanceSeatStates(), BookingMetadata.CodeLabel::code, BookingMetadata.CodeLabel::label),
                toEnumCodeItems(bookingMetadata.holdStates(), BookingMetadata.CodeLabel::code, BookingMetadata.CodeLabel::label),
                toEnumCodeItems(bookingMetadata.orderStates(), BookingMetadata.CodeLabel::code, BookingMetadata.CodeLabel::label),
                toEnumCodeItems(memberMetadata.socialProviders(), MemberMetadata.CodeLabel::code, MemberMetadata.CodeLabel::label),
                toEnumCodeItems(memberMetadata.roles(), MemberMetadata.CodeLabel::code, MemberMetadata.CodeLabel::label),
                toEnumCodeItems(catalogMetadata.saleTypes(), CatalogMetadata.CodeLabel::code, CatalogMetadata.CodeLabel::label),
                toEnumCodeItems(catalogMetadata.regions(), CatalogMetadata.CodeLabel::code, CatalogMetadata.CodeLabel::label),
                toEnumCodeItems(catalogMetadata.showSortKeys(), CatalogMetadata.CodeLabel::code, CatalogMetadata.CodeLabel::label)
        );

        return new Output(categories, genres, enums);
    }

    private CategoryCodeItem toCategoryCodeItem(final CatalogMetadata.CategoryCode category) {
        return new CategoryCodeItem(category.id(), category.code(), category.name());
    }

    private GenreCodeItem toGenreCodeItem(final CatalogMetadata.GenreCode genre) {
        return new GenreCodeItem(genre.id(), genre.categoryCode(), genre.code(), genre.name());
    }

    private <T> List<EnumCodeItem> toEnumCodeItems(
            final List<T> codeLabels,
            final Function<T, String> codeMapper,
            final Function<T, String> labelMapper
    ) {
        return codeLabels.stream()
                .map(codeLabel -> new EnumCodeItem(codeMapper.apply(codeLabel), labelMapper.apply(codeLabel)))
                .toList();
    }
}
