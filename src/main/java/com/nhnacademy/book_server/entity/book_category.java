package com.nhnacademy.book_server.entity;

import jakarta.persistence.ManyToOne;

public class book_category {

    @ManyToOne
    private int id;  // 카테고리 아이디

    @ManyToOne
    private int book_id;
}
