package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RerankerService {

    private final RestTemplate restTemplate;
    private static final String RERANKER_URL = "http://reranker.java21.net/rerank";

    // [수정] 5분짜리 "ollamaRestTemplate" 빈을 주입받도록 명시
    public RerankerService(@Qualifier("ollamaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 검색된 도서 리스트를 Reranker API를 통해 재순위화합니다.
     */
    public List<BookResponse> rerank(List<BookResponse> books, String query) {
        if (books == null || books.isEmpty()) {
            return List.of();
        }

        try {
            // 1. Reranker에 보낼 텍스트 추출
            List<String> texts = books.stream()
                    .map(this::toRerankText)
                    .toList();

            // 2. 요청 바디 구성
            Map<String, Object> requestBody = Map.of(
                    "query", query,
                    "texts", texts
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 3. API 호출 (이제 5분까지 기다립니다)
            List<Map<String, Object>> results = restTemplate.postForObject(
                    RERANKER_URL,
                    entity,
                    List.class
            );

            if (results == null || results.isEmpty()) {
                log.warn("Reranker 응답이 비어있어 원본 순서를 유지합니다.");
                return books;
            }

            // 4. 점수 매핑 및 재정렬
            List<BookWithScore> scoredBooks = new ArrayList<>();
            for (Map<String, Object> res : results) {
                int index = res.get("index") instanceof Number number ? number.intValue() : -1;
                double score = res.get("score") instanceof Number number? number.doubleValue() : 0.0;

                if (index >= 0 && index < books.size()) {
                    scoredBooks.add(new BookWithScore(books.get(index), score));
                }
            }

            // 점수 높은 순(내림차순) 정렬
            scoredBooks.sort(Comparator.comparingDouble(BookWithScore::score).reversed());

            return scoredBooks.stream()
                    .map(BookWithScore::book)
                    .toList();

        } catch (Exception e) {
            log.error("Reranker API 호출 실패. 원본 순서를 반환합니다. query={}", query, e);
            return books; // 실패 시 원본 유지
        }
    }

    private String toRerankText(BookResponse book) {
        String content = book.content() != null ? book.content() : "";
        String shortContent = content.length() > 300 ? content.substring(0, 300) : content;

        return String.format("%s %s %s %s",
                book.title(),
                book.author(),
                book.publisher(),
                shortContent
        );
    }

    private record BookWithScore(BookResponse book, double score) {}
}