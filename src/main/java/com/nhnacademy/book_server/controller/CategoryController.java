package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.CategorySwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.service.category.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController implements CategorySwagger {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<Void> createCategory(@RequestBody Category category) {
        categoryService.createCategory(category.getCategoryId(), category.getCategoryName(), category.getParentId(), category.getDepth());
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/parent")
    public ResponseEntity<List<CategoryResponse>> getParents() {
        return ResponseEntity.ok(categoryService.getParents());
    }

    @Override
    @GetMapping("/{parentId}/child")
    public ResponseEntity<List<CategoryResponse>> getChilds(@PathVariable("parentId") int parentId) {
        return ResponseEntity.ok(categoryService.getChilds(parentId));
    }

    @Override
    @GetMapping("/{categoryId}/books")
    public ResponseEntity<Page<BookResponse>> getBooksByCategory(@PathVariable("categoryId") int categoryId,
                                                                 @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(categoryService.getBooksByCategory(categoryId, pageable));
    }
}
