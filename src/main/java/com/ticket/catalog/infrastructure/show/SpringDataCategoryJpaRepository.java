package com.ticket.catalog.infrastructure.show;

import com.ticket.catalog.domain.show.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCategoryJpaRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByIdAsc();
}
