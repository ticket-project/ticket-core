package com.ticket.show.infrastructure;

import com.ticket.show.domain.Category;
import com.ticket.show.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link CategoryRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final SpringDataCategoryJpaRepository jpaRepository;

    @Override
    public List<Category> findAllOrderById() {
        return jpaRepository.findAllByOrderByIdAsc();
    }
}
