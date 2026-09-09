package com.ticket.show.infrastructure;

import com.ticket.show.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCategoryJpaRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByIdAsc();
}
