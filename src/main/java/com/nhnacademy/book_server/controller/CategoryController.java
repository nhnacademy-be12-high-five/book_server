package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.CategorySwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController implements CategorySwagger {

    private final CategoryService categoryService;


    @Override
    @GetMapping("/parent")
    public ResponseEntity<List<CategoryResponse>> getParents() {
        return ResponseEntity.ok(categoryService.getParents());
    }

    @Override
    @GetMapping("/{parentId}/child")
    public ResponseEntity<List<CategoryResponse>> getChilds(@PathVariable int parentId) {
        return ResponseEntity.ok(categoryService.getChilds(parentId));
    }

    @Override
    @GetMapping("/{categoryId}/books")
    public ResponseEntity<List<BookResponse>> getBooksByCategory(@PathVariable int categoryId) {
        return ResponseEntity.ok(categoryService.getBooksByCategory(categoryId));
    }


}
