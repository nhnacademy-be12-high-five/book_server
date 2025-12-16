package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookCategoryRepository extends JpaRepository<BookCategory, BookCategory.Pk> {
    List<BookCategory> findByCategory(Category category);

    @Query("select bc.book.id from BookCategory bc where bc.category.categoryId = :categoryId")
    List<Long> findBookIdsByCategoryId(@Param("categoryId") int categoryId);


}
