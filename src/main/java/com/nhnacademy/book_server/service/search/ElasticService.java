package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.entity.SearchFieldType;
import com.nhnacademy.book_server.repository.ElasticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ElasticService {

    private static final String INDEX = "high-five";

    private final ElasticRepository elasticRepository;

    private final ElasticsearchClient client;
    private final GeminiTextClientService geminiTextClientService;

    public SearchResult<BookResponse> search(String keyword, BookSortType sort, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult<>(List.of(), 0L);
        }

        int from = page * size;

        // 필드별 가중치
        int titleBoost = SearchFieldType.TITLE.getWeight();
        int authorBoost = SearchFieldType.AUTHOR.getWeight();
        int tagBoost = SearchFieldType.TAG.getWeight();
        int isbnBoost = SearchFieldType.ISBN.getWeight();
        int publisherBoost = SearchFieldType.PUBLISHER.getWeight();
        int contentBoost = SearchFieldType.CONTENT.getWeight();

        try {
            SearchResponse<Map> response = client.search(s -> {
                        s.index(INDEX)
                                .from(from)
                                .size(size)
                                // ★ 키워드 기반 필수 검색 조건 (AND로 강하게 매칭)
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
                                        // "만화" AND "스펀지" 처럼 모두 포함해야 매칭되도록
                                        .operator(Operator.And)
                                ));

                // ★ 정렬 기준
                if (sort != null) {
                    switch (sort) {
                        case LOW_PRICE -> s.sort(so -> so
                                .field(f -> f.field("price").order(SortOrder.Asc)));

                        case HIGH_PRICE -> s.sort(so -> so
                                .field(f -> f.field("price").order(SortOrder.Desc)));

                        case RATING -> s.sort(so -> so
                                .field(f -> f.field("avgRating").order(SortOrder.Desc)));

                        case REVIEW -> s.sort(so -> so
                                .field(f -> f.field("reviewCount").order(SortOrder.Desc)));

                        case NEW -> s.sort(so -> so
                                .field(f -> f.field("publishedDate").order(SortOrder.Desc)));

                        case POPULAR -> {
                            // POPULAR / 기본: score(관련도) 순으로만 정렬
                            // → 추가 sort 설정 안 함
                        }

                        default -> {
                            // 혹시 null 등 예외값이 들어오면 score 순
                        }
                    }
                }

                        return s;
                    },
                    Map.class
            );

            // totalHits 계산
            long totalHits;
            if (response.hits().total() != null) {
                totalHits = response.hits().total().value();
            } else {
                totalHits = response.hits().hits().size();
            }

            // Map → BookResponse 변환
            List<BookResponse> books = response.hits().hits().stream()
                    .map(Hit::source)
                    .map(this::toBookResponse)
                    .toList();

            return new SearchResult<>(books, totalHits);

        } catch (Exception e) {
            throw new RuntimeException("ES 검색 실패: " + e.getMessage(), e);
        }
    }

    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) {
            return null;
        }

        Long bookId = null;
        if (source.get("id") instanceof Number nId) {
            bookId = nId.longValue();
        } else if (source.get("bookId") instanceof Number nBookId) {
            bookId = nBookId.longValue();
        }

        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.get("isbn");

        Integer price = null;
        Object priceObj = source.get("price");
        if (priceObj instanceof Number nPrice) {
            price = nPrice.intValue();
        }

        String image = (String) source.get("image");

        Integer categoryId = null;
        Object catObj = source.get("categoryId");
        if (catObj instanceof Number nCat) {
            categoryId = nCat.intValue();
        }

        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");

        String publishedDate = null;
        if (source.get("publishedDate") != null) {
            publishedDate = source.get("publishedDate").toString();
        }

        Double avgRating = null;
        Object avgObj = source.get("avgRating");
        if (avgObj instanceof Number nAvg) {
            avgRating = nAvg.doubleValue();
        }

        Long reviewCount = 0L;
        Object revObj = source.get("reviewCount");
        if (revObj instanceof Number nRev) {
            reviewCount = nRev.longValue();
        }

        String aiSummary = null;
        if (content != null && !content.isBlank()) {
            aiSummary = geminiTextClientService.generateAnswer(content);
        }

        return new BookResponse(
                bookId,
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
                reviewCount,
                aiSummary,
                null
        );
    }


    public void saveAll(List<BookResponse> books) {
        if (books == null || books.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (BookResponse book : books) {
                if (book == null || book.bookId() == null) {
                    continue;
                }

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX)
                                .id(book.bookId().toString())
                                .document(book)
                        )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());

            if (response.errors()) {
                // 개별 실패 건 로깅
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        System.err.println("ES bulk 인덱싱 실패 - id=" +
                                item.id() + " reason=" + item.error().reason());
                    }
                });
                throw new RuntimeException("ES bulk 인덱싱 중 일부 문서 실패 발생");
            }

        } catch (IOException e) {
            throw new RuntimeException("ES bulk 인덱싱 실패", e);
        }
    }
}
