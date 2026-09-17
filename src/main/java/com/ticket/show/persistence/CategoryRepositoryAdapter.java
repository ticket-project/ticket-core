package com.ticket.show.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.Category;
import com.ticket.show.domain.CategoryRepository;

import lombok.RequiredArgsConstructor;

/** {@link CategoryRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {
    private final SpringDataCategoryJpaRepository jpaRepository;

    @Override
    public List<Category> findAllOrderById() {
        return jpaRepository.findAllByOrderByIdAsc();
    }
}
