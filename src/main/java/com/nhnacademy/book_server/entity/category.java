package com.nhnacademy.book_server.entity;

import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class category {

    @NotNull
    @Id
    private int categoryId;

    private String categoryName;
}
