package com.nhnacademy.book_server.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserTagRequest {
    private String tagCode; // CART_CANDIDATE, TO_READ, ...
}
