package com.ticket.show.domain.show.repository;

import com.ticket.show.domain.show.Genre;

import java.util.List;

/**
 * 장르 메타 코드의 복원을 담당하는 도메인 Repository다.
 */
public interface GenreRepository {

    List<Genre> findAllOrderByCategoryAndName();

    List<Genre> findAllByCategoryCodeOrderByName(String categoryCode);
}
