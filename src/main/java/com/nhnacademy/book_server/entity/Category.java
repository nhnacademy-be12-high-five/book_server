package com.nhnacademy.book_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Category {

    @NotNull
    @Id
    private int categoryId;

    private String categoryName;

    @NotNull
    private int parentId;
    private int depth;
}