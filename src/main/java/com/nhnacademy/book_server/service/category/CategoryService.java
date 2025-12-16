package com.nhnacademy.book_server.service.category;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.repository.BookCategoryRepository;
import com.nhnacademy.book_server.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;

    // 대분류
    public List<CategoryResponse> getParents() {
        return categoryRepository.findByDepth(1)
                .stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getCategoryName()))
                .toList();
    }

    // 하위 카테고리 조회
    public List<CategoryResponse> getChilds(int parentId) {
        return categoryRepository.findByParentId(parentId)
                .stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getCategoryName()))
                .toList();
    }

    // 카테고리별 도서 조회
    public List<BookResponse> getBooksByCategory(int categoryID) {
        return categoryRepository.findById(categoryID)
                .map(category ->
                        bookCategoryRepository.findByCategory(category)
                                .stream()
                                .map(BookCategory::getBook)
                                .map(book -> BookResponse.from(book, category))
                                .toList()
                )
                .orElse(List.of());  // 카테고리가 없으면 빈 리스트
    }

}
