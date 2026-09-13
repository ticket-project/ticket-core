package com.ticket.show.classification.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.classification.domain.Category;

interface SpringDataCategoryJpaRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByIdAsc();
}
