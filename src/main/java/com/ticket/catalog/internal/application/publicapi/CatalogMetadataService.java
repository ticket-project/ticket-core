package com.ticket.catalog.internal.application.publicapi;

import com.ticket.catalog.CatalogMetadata;
import com.ticket.catalog.internal.domain.show.BookingStatus;
import com.ticket.catalog.internal.domain.show.Category;
import com.ticket.catalog.internal.domain.show.Genre;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.SaleType;
import com.ticket.catalog.internal.domain.show.repository.CategoryRepository;
import com.ticket.catalog.internal.domain.show.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * {@link CatalogMetadata}의 catalog 소유 구현이다. metadata module은 이 계약을 통해서만
 * catalog의 code/label 값을 조합하고, internal entity·enum을 직접 import하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogMetadataService implements CatalogMetadata {

    private final CategoryRepository categoryRepository;
    private final GenreRepository genreRepository;

    @Override
    public List<CategoryCode> categories() {
        return categoryRepository.findAllOrderById().stream()
                .map(this::toCategoryCode)
                .toList();
    }

    @Override
    public List<GenreCode> genres() {
        return genreRepository.findAllOrderByCategoryAndName().stream()
                .map(this::toGenreCode)
                .toList();
    }

    @Override
    public List<CodeLabel> bookingStatuses() {
        return Arrays.stream(BookingStatus.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }

    @Override
    public List<CodeLabel> saleTypes() {
        return Arrays.stream(SaleType.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }

    @Override
    public List<CodeLabel> regions() {
        return Arrays.stream(Region.values())
                .map(value -> new CodeLabel(value.getCode(), value.getDescription()))
                .toList();
    }

    private CategoryCode toCategoryCode(final Category category) {
        return new CategoryCode(category.getId(), category.getCode(), category.getName());
    }

    private GenreCode toGenreCode(final Genre genre) {
        return new GenreCode(genre.getId(), genre.getCategory().getCode(), genre.getCode(), genre.getName());
    }
}
