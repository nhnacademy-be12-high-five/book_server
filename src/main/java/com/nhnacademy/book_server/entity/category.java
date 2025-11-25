package com.nhnacademy.book_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@Entity
@RequiredArgsConstructor
public class category {

    @NotNull
    @Id
    private int categoryId;

    private String categoryName;

    @NotNull
    private int parentId;
    private int depth;
}
