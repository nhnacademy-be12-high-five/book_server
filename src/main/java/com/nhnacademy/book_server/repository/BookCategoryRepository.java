package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCategoryRepository extends JpaRepository<BookCategory, BookCategory.Pk> {

    boolean existsById(BookCategory.Pk categoryId);
}
