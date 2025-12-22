package com.nhnacademy.book_server.service.category;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.mapper.CategoryMapper;
import com.nhnacademy.book_server.repository.BookCategoryRepository;
import com.nhnacademy.book_server.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryMappingService {

    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;

        @Transactional(readOnly = false)
        public void CategoryAndMap(Book book, Integer categoryId, String categoryName) {

            Category category = categoryRepository.findByCategoryId(categoryId).orElseGet(()->{
                int parentId=CategoryMapper.getParentId(categoryId);
                int depth=(parentId == 0) ? 1:2;

                Category newCategory = new Category(categoryId, categoryName, parentId, depth);
                return categoryRepository.save(newCategory);
            });

            BookCategory.Pk category1= new BookCategory.Pk(book.getId(),category.getCategoryId());

            if (bookCategoryRepository.existsById(category1)){
                return;
            }

            BookCategory bookCategory = new BookCategory(category1,book,category);
            bookCategoryRepository.save(bookCategory);
        }
}