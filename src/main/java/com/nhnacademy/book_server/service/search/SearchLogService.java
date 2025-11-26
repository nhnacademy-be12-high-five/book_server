package com.nhnacademy.book_server.service;

public interface SearchLogService {

    void setSearchLog(String keyword);
    long getSearchCount(String keyword);
}
