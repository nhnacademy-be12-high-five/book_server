package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.entity.SearchFieldType;
import com.nhnacademy.book_server.repository.ElasticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 연동 구현체
 */
@Service
@RequiredArgsConstructor
public class ElasticService implements ElasticRepository {

    private static final String INDEX = "book_index";

    private final ElasticsearchClient client;

    /**
     * ES book_index 검색
     */
    @Override
    public SearchResult<BookResponse> search(String keyword, BookSortType sort, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult<>(List.of(), 0L);
        }

        int from = page * size;

        // 필드별 가중치 (SearchFieldType enum 사용)
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
                                ));

                        // 정렬 기준에 따라 sort 추가
                        if (sort != null) {
                            switch (sort) {
                                case LOW_PRICE:
                                    s.sort(so -> so
                                            .field(f -> f.field("price").order(SortOrder.Asc)));
                                    break;

                                case HIGH_PRICE:
                                    s.sort(so -> so
                                            .field(f -> f.field("price").order(SortOrder.Desc)));
                                    break;

                                case RATING:
                                    s.sort(so -> so
                                            .field(f -> f.field("avgRating").order(SortOrder.Desc)));
                                    break;

                                case REVIEW:
                                    s.sort(so -> so
                                            .field(f -> f.field("reviewCount").order(SortOrder.Desc)));
                                    break;

                                case NEW:
                                    s.sort(so -> so
                                            .field(f -> f.field("publishedDate").order(SortOrder.Desc)));
                                    break;

                                case POPULAR:
                                default:
                                    // POPULAR는 기본 score(relevance) 기준 → 별도 sort 없음
                                    break;
                            }
                        }


                        return s;
                    },
                    Map.class
            );

            // 전체 건수(totalHits)
            long totalHits = 0L;
            if (response.hits().total() != null) {
                totalHits = response.hits().total().value();
            } else {
                // total 정보가 없는 경우, 일단 현재 반환된 개수로 대체
                totalHits = response.hits().hits().size();
            }

            // 실제 도서 목록 변환
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
     * ES 검색 결과 Map → BookResponse 변환
     */
    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) {
            return null;
        }

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

    /**
     * 여러 도서를 ES 인덱스에 저장 (reindex)
     */
    @Override
    public void saveAll(List<BookResponse> books) {
        for (BookResponse book : books) {
            try {
                client.index(i -> i
                        .index(INDEX)
                        .id(book.id().toString())
                        .document(book)
                );
            } catch (IOException e) {
                throw new RuntimeException("ES 인덱싱 실패: " + book.id(), e);
            }
        }
    }
}
