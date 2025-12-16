package com.nhnacademy.book_server.service.category;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.repository.BookCategoryRepository;
import com.nhnacademy.book_server.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryMappingService {

    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;

    public void upsertCategoryAndMap(Book book, Integer categoryId, String categoryName) {
        if (book == null || categoryId == null) return;

        // 1) Category upsert (없으면 생성)
        Category category = categoryRepository.findById(categoryId)
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setCategoryId(categoryId);
                    c.setCategoryName(StringUtils.hasText(categoryName) ? categoryName : "");
                    // NOT NULL 컬럼 안전값 (데이터 파이프라인에서 나중에 정교화 가능)
                    c.setParentId(0);
                    c.setDepth(0);
                    return categoryRepository.save(c);
                });

        // 2) 이름이 들어왔고 기존과 다르면 업데이트(선택)
        if (StringUtils.hasText(categoryName) && !categoryName.equals(category.getCategoryName())) {
            category.setCategoryName(categoryName);
            categoryRepository.save(category);
        }

        // 3) 매핑 존재하면 종료
        BookCategory.Pk pk = new BookCategory.Pk(book.getId(), categoryId);
        if (bookCategoryRepository.existsById(pk)) return;

        // 4) 매핑 저장 (setter 불필요)
        bookCategoryRepository.save(new BookCategory(book, category));
    }
}
