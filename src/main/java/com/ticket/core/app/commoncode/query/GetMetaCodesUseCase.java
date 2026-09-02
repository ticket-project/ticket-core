package com.ticket.core.app.commoncode.query;

import com.ticket.catalog.internal.domain.show.Category;
import com.ticket.catalog.internal.domain.show.repository.CategoryRepository;
import com.ticket.catalog.internal.domain.show.Genre;
import com.ticket.catalog.internal.domain.show.repository.GenreRepository;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.SaleType;
import com.ticket.catalog.internal.application.show.query.ShowSort;
import com.ticket.catalog.internal.domain.show.BookingStatus;
import com.ticket.booking.BookingCatalog;
import com.ticket.identity.internal.domain.member.model.Role;
import com.ticket.identity.internal.domain.member.model.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMetaCodesUseCase {

    private final CategoryRepository categoryRepository;
    private final GenreRepository genreRepository;
    private final BookingCatalog bookingCatalog;

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
        final List<CategoryCodeItem> categories = categoryRepository.findAllOrderById().stream()
                .map(this::toCategoryCodeItem)
                .toList();

        final List<GenreCodeItem> genres = genreRepository.findAllOrderByCategoryAndName().stream()
                .map(this::toGenreCodeItem)
                .toList();

        final EnumCodes enums = new EnumCodes(
                mapEnumValues(BookingStatus.values(), BookingStatus::getCode, BookingStatus::getDescription),
                toEnumCodeItems(bookingCatalog.performanceSeatStates()),
                toEnumCodeItems(bookingCatalog.holdStates()),
                toEnumCodeItems(bookingCatalog.orderStates()),
                mapEnumValues(SocialProvider.values(), SocialProvider::getCode, SocialProvider::getDescription),
                mapEnumValues(Role.values(), Role::getCode, Role::getDescription),
                mapEnumValues(SaleType.values(), SaleType::getCode, SaleType::getDescription),
                mapEnumValues(Region.values(), Region::getCode, Region::getDescription),
                mapEnumValues(ShowSort.values(), ShowSort::apiValue, ShowSort::getDescription)
        );

        return new Output(categories, genres, enums);
    }

    private CategoryCodeItem toCategoryCodeItem(final Category category) {
        return new CategoryCodeItem(category.getId(), category.getCode(), category.getName());
    }

    private GenreCodeItem toGenreCodeItem(final Genre genre) {
        return new GenreCodeItem(
                genre.getId(),
                genre.getCategory().getCode(),
                genre.getCode(),
                genre.getName()
        );
    }

    private <E> List<EnumCodeItem> mapEnumValues(
            final E[] values,
            final Function<E, String> codeMapper,
            final Function<E, String> descriptionMapper
    ) {
        return Arrays.stream(values)
                .map(value -> new EnumCodeItem(codeMapper.apply(value), descriptionMapper.apply(value)))
                .toList();
    }

    private List<EnumCodeItem> toEnumCodeItems(final List<BookingCatalog.CodeLabel> codeLabels) {
        return codeLabels.stream()
                .map(codeLabel -> new EnumCodeItem(codeLabel.code(), codeLabel.label()))
                .toList();
    }
}
