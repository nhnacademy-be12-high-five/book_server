package com.nhnacademy.book_server.service.search;

public interface SearchLogService {

    void setSearchLog(String keyword);
    long getSearchCount(String keyword);
}
