package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.response.SearchLogResponse;

import java.util.List;

public interface SearchLogService {


    void setSearchLog(String keyword);

    //  인기 검색어 조회
    List<SearchLogResponse> getPopularKeywords(int limit);
}
