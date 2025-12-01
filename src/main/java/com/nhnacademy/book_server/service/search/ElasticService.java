package com.nhnacademy.book_server.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.SearchFieldType;
import com.nhnacademy.book_server.repository.ElasticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ElasticService implements ElasticRepository {

    private static final String index = "book_index";
    private final ElasticsearchClient client;

    @Override
    public List<BookResponse> search(String keyword, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        // 가중치 계산
        int titleBoost = SearchFieldType.TITLE.getWeight();
        int authorBoost = SearchFieldType.AUTHOR.getWeight();
        int tagBoost = SearchFieldType.TAG.getWeight();
        int isbnBoost = SearchFieldType.ISBN.getWeight();
        int publisherBoost = SearchFieldType.PUBLISHER.getWeight();
        int contentBoost = SearchFieldType.CONTENT.getWeight();

        try {
            SearchResponse<Map> response = client.search(s -> s
                            .index(index)
                            .size(size)
                            .query(q -> q.multiMatch(m -> m
                                    .query(keyword)
                                    .fields(
                                            "title^" + titleBoost,
                                            "author^" + authorBoost,
                                            "tags^" + tagBoost,
                                            "isbn^" + isbnBoost,
                                            "publisher^" + publisherBoost,
                                            "content^" + contentBoost
                                    )
                            )),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .map(this::toBookResponse)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // book_index.json의 필드에 맞춰 BookResponse 변환
    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) return null;

        Long id = null;
        if (source.get("id") != null) {
            id = ((Number) source.get("id")).longValue();
        } else if (source.get("bookId") != null) {
            id = ((Number) source.get("bookId")).longValue();
        }

        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.get("isbn");

        Integer price = null;
        if (source.get("price") != null) {
            price = ((Number) source.get("price")).intValue();
        }

        String image = (String) source.get("image");

        Integer categoryId = null;
        if (source.get("categoryId") != null) {
            categoryId = ((Number) source.get("categoryId")).intValue();
        }

        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");

        String publishedDate = null;
        if (source.get("publishedDate") != null) {
            publishedDate = source.get("publishedDate").toString();
        }

        Double avgRating = null;
        if (source.get("avgRating") != null) {
            avgRating = ((Number) source.get("avgRating")).doubleValue();
        }

        Long reviewCount = 0L;
        if (source.get("reviewCount") != null) {
            reviewCount = ((Number) source.get("reviewCount")).longValue();
        }

        return new BookResponse(
                id,
                title,
                author,
                isbn,
                price,
                image,
                categoryId,
                content,
                publisher,
                publishedDate,
                avgRating,
                reviewCount
        );
    }

    //elasticservice에 인덱스 저장 기능
    public void saveToIndex(BookResponse book) {
        try {
            client.index(i -> i
                    .index(index)
                    .id(book.id().toString())
                    .document(book)
            );
        } catch (Exception e) {
            throw new RuntimeException("ES 저장 실패: " + e.getMessage());
        }
    }

    public void saveAll(List<BookResponse> books) {
        books.forEach(this::saveToIndex);
    }

}
