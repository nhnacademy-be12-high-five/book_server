package com.nhnacademy.book_server.entity;

import com.mysql.cj.log.Log;

public record bookResponse(Long id,String title,String author,Long price,String image) {
}
