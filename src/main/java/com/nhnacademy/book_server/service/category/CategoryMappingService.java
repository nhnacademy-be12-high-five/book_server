
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

    @Transactional
    public void categoryAndMap(Book book, Integer categoryId, String categoryName) {
        // 1. 요청된 카테고리(주로 소분류) 가져오기 또는 생성
        Category mainCategory = getOrCreateCategory(categoryId, categoryName);

        // 2. 책 <-> 메인 카테고리 매핑 저장
        saveBookCategory(book, mainCategory);

        if (mainCategory.getParentId() != 0) {
            // 부모 카테고리 조회 (없으면 안전하게 무시하거나, 필요시 생성 로직 추가 가능)
            categoryRepository.findByCategoryId(mainCategory.getParentId())
                    .ifPresent(parentCategory -> saveBookCategory(book, parentCategory));
        }
    }

    private Category getOrCreateCategory(Integer categoryId, String categoryName) {
        return categoryRepository.findByCategoryId(categoryId)
                .orElseGet(() -> {
                    // CategoryMapper의 개선된 getParentId 사용
                    int parentId = CategoryMapper.getParentId(categoryId);
                    int depth = (parentId == 0) ? 1 : 2;

                    Category newCategory = new Category(categoryId, categoryName, parentId, depth);
                    return categoryRepository.save(newCategory);
                });
    }

    private void saveBookCategory(Book book, Category category) {
        BookCategory.Pk pk = new BookCategory.Pk(book.getId(), category.getCategoryId());

        // 이미 매핑되어 있으면 패스
        if (bookCategoryRepository.existsById(pk)) {
            return;
        }

        BookCategory bookCategory = new BookCategory(pk, book, category);
        bookCategoryRepository.save(bookCategory);
    }
}