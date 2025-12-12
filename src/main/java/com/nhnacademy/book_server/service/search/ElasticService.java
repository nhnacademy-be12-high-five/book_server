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
public class ElasticService implements ElasticRepository {

    private static final String INDEX = "book_index";

    private final ElasticsearchClient client;

    /**
     * ======================
     * 일반 검색 서비스
     * ======================
     */
    @Override
    public SearchResult<BookResponse> search(String keyword, BookSortType sort, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult<>(List.of(), 0L);
        }

        int from = page * size;

        // 가중치 로딩
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

                                // --- 필수 multiMatch 검색 ---
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
                                        .operator(Operator.And)
                                ));

                        // --- 정렬 조건 ---
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

                                case POPULAR -> { /* score 기본 */ }
                                default -> { /* score 기본 */ }
                            }
                        }

                        return s;
                    },
                    Map.class
            );

            // 검색 결과 수
            long totalHits =
                    response.hits().total() != null
                            ? response.hits().total().value()
                            : response.hits().hits().size();

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


    /**
     * ============================================
     * ES _source → BookResponse 변환
     *  (프론트 카드 UI에서 사용하는 필드만 정확히 매핑)
     * ============================================
     */
    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) return null;

        // ID
        Long id = null;
        Object idObj = source.get("id") != null ? source.get("id") : source.get("bookId");
        if (idObj instanceof Number nId) {
            id = nId.longValue();
        }

        // 문자열 필드
        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.get("isbn");
        String image = (String) source.get("image");
        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");

        String publishedDate = null;
        if (source.get("publishedDate") != null) {
            publishedDate = source.get("publishedDate").toString();
        }

        // 숫자 필드
        Integer price = null;
        if (source.get("price") instanceof Number nPrice) {
            price = nPrice.intValue();
        }

        Integer categoryId = null;
        if (source.get("categoryId") instanceof Number nCat) {
            categoryId = nCat.intValue();
        }

        Double avgRating = null;
        if (source.get("avgRating") instanceof Number nAvg) {
            avgRating = nAvg.doubleValue();
        }

        Long reviewCount = 0L;
        if (source.get("reviewCount") instanceof Number nRev) {
            reviewCount = nRev.longValue();
        }

        // ⭐ 일반 검색이므로 aiSummary = null
        String aiSummary = null;

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
                reviewCount,
                aiSummary
        );
    }


    /**
     * ===================================
     * saveAll → book_index 초기 인덱싱
     * ===================================
     */
    @Override
    public void saveAll(List<BookResponse> books) {
        if (books == null || books.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (BookResponse book : books) {
                if (book == null || book.bookId() == null) continue;

                // ES 문서로 그대로 BookResponse를 저장
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
