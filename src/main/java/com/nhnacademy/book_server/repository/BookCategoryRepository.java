package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookCategoryRepository extends JpaRepository<BookCategory, Integer> {
    List<BookCategory> findByCategory(Category category);
}
