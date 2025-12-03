package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.response.SearchLogResponse;
import com.nhnacademy.book_server.entity.SearchLog;
import com.nhnacademy.book_server.repository.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchLogServiceImpl implements SearchLogService {

    private final SearchLogRepository searchLogRepository;

    @Override
    public void setSearchLog(String keyword) {
        SearchLog log = searchLogRepository.findByKeyword(keyword)
                .orElseGet(() -> SearchLog.builder()
                        .keyword(keyword)
                        .searchCount(0L)
                        .build());

        log.increaseCount(); // 엔티티에 increaseCount()가 있다고 가정

        searchLogRepository.save(log);
    }

    @Override
    public List<SearchLogResponse> getPopularKeywords(int limit) {
        if (limit <= 0) {
            limit = 10; // 기본값
        }

        List<SearchLog> logs = searchLogRepository.findAllByOrderBySearchCountDesc();

        return logs.stream()
                .limit(limit)
                .map(log -> new SearchLogResponse(
                        log.getKeyword(),
                        log.getSearchCount()
                ))
                .toList();
    }
}
