package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.model.Genre;
import com.ticket.core.domain.show.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetGenresByCategoryUseCase {

    private final GenreRepository genreRepository;

    public record Input(String categoryCode) {
    }

    public record GenreItem(Long id, String code, String name) {
    }

    public record Output(List<GenreItem> genres) {
    }

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
