package com.ticket.show.classification.infrastructure;

import com.ticket.show.classification.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCategoryJpaRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByIdAsc();
}
