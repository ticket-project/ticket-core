package com.ticket.show.usecase;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.show.domain.Genre;
import com.ticket.show.domain.GenreRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetGenresByCategoryUseCase {
    private final GenreRepository genreRepository;

    public record Input(String categoryCode) {}

    public record GenreItem(Long id, String code, @Nullable String name) {}

    public record Output(List<GenreItem> genres) {}

    public Output execute(final Input input) {
        final List<Genre> genres;

        if (input.categoryCode == null || input.categoryCode.isBlank()) {
            genres = genreRepository.findAllOrderByCategoryAndName();
        } else {
            genres = genreRepository.findAllByCategoryCodeOrderByName(input.categoryCode);
        }

        final List<GenreItem> items = genres.stream()
                .map(genre -> new GenreItem(genre.getId(), genre.getCode(), genre.getName()))
                .toList();

        return new Output(items);
    }
}
