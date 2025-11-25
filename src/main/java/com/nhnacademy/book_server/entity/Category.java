package com.nhnacademy.book_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Entity
@RequiredArgsConstructor
public class Category {

    @NotNull
    @Id
    private int categoryId;

    private String categoryName;

    @NotNull
    private int parentId;
    private int depth;
}
