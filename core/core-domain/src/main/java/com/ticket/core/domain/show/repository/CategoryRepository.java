package com.ticket.core.domain.show.repository;

import com.ticket.core.domain.show.model.Category;

import java.util.List;

/**
 * 카테고리 메타 코드의 복원을 담당하는 도메인 Repository다.
 */
public interface CategoryRepository {

    List<Category> findAllOrderById();
}
