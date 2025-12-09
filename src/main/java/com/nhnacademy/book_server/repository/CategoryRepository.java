package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    //대분류(depth=1)
    List<Category> findByDepth(int depth);
    //하위 카테고리
    List<Category> findByParentId(int parentId);

}
