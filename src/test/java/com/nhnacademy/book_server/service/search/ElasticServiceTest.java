package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.ObjectBuilder;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringJUnitConfig
@Import({ElasticService.class, ElasticServiceTest.Config.class})
class ElasticServiceTest {

    @TestConfiguration
    static class Config {
        // 테스트 전용 Bean이 필요하면 여기에 추가
    }

    @MockitoBean
    ElasticsearchClient client;

    @Autowired
    ElasticService elasticService;

    // -------------------------------
    // Helpers
    // -------------------------------
    private static SearchResponse<Map> stubSearchResponse(List<Map<String, Object>> sources, long total) {
        List<Hit<Map>> hits = sources.stream()
                .map(src -> new Hit.Builder<Map>().source(src).build())
                .toList();

        HitsMetadata<Map> hitsMetadata = new HitsMetadata.Builder<Map>()
                .hits(hits)
                .total(new TotalHits.Builder().value(total).relation(TotalHitsRelation.Eq).build())
                .build();

        return new SearchResponse.Builder<Map>()
                .hits(hitsMetadata)
                .build();
    }

    private static Map<String, Object> sourceWithId(long id, Integer price, Double avgRating, Long reviewCount, String publishedDate) {
        Map<String, Object> src = new HashMap<>();
        src.put("id", id);
        src.put("title", "t" + id);
        src.put("author", "a" + id);
        src.put("isbn", "i" + id);
        if (price != null) src.put("price", price);
        src.put("image", "img");
        src.put("content", "c");
        src.put("publisher", "p");
        if (publishedDate != null) src.put("publishedDate", publishedDate);
        if (avgRating != null) src.put("avgRating", avgRating);
        if (reviewCount != null) src.put("reviewCount", reviewCount);
        src.put("aiSummary", "s");
        return src;
    }

    private SearchRequest captureBuiltSearchRequest(BookSortType sort, int page, int size) throws IOException {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> fnCaptor =
                ArgumentCaptor.forClass(Function.class);

        when(client.search(fnCaptor.capture(), eq(Map.class)))
                .thenReturn(stubSearchResponse(List.of(sourceWithId(1L, 1000, 4.5, 120L, "2024-01-01")), 1L));

        elasticService.search("k", sort, page, size);

        Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn = fnCaptor.getValue();
        SearchRequest.Builder b = new SearchRequest.Builder();
        ObjectBuilder<SearchRequest> ob = fn.apply(b);
        return ob.build();
    }

    // ===============================
    // search() tests
    // ===============================
    @Nested
    @DisplayName("search()")
    class SearchTests {

        @Test
        @DisplayName("keyword가 null/blank이면 empty 결과 + client.search 호출 없음")
        void search_blankKeyword_returnsEmpty_noClientCall() {
            SearchResult<BookResponse> r1 = elasticService.search(null, BookSortType.POPULAR, 0, 10);
            SearchResult<BookResponse> r2 = elasticService.search("   ", BookSortType.LOW_PRICE, 0, 10);

            assertThat(r1.totalHits()).isZero();
            assertThat(r1.content()).isEmpty();
            assertThat(r2.totalHits()).isZero();
            assertThat(r2.content()).isEmpty();

            verify(client, never()).search(any(Function.class), eq(Map.class));
        }

        @Test
        @DisplayName("POPULAR/null 정렬: function_score 쿼리 분기 + from/size 설정 검증")
        void search_popularOrNull_buildsFunctionScoreQuery() throws Exception {
            SearchRequest req1 = captureBuiltSearchRequest(BookSortType.POPULAR, 2, 20);
            assertThat(req1.index()).containsExactly("high-five");
            assertThat(req1.from()).isEqualTo(40);
            assertThat(req1.size()).isEqualTo(20);

            Query q1 = req1.query();
            assertThat(q1).isNotNull();
            assertThat(q1.isFunctionScore()).isTrue();

            SearchRequest req2 = captureBuiltSearchRequest(null, 1, 10);
            assertThat(req2.query()).isNotNull();
            assertThat(req2.query().isFunctionScore()).isTrue();
        }

        @Test
        @DisplayName("LOW_PRICE 정렬: price ASC sort 필드 설정 검증")
        void search_lowPrice_buildsSortPriceAsc() throws Exception {
            SearchRequest req = captureBuiltSearchRequest(BookSortType.LOW_PRICE, 0, 10);

            assertThat(req.sort()).hasSize(1);
            assertThat(req.sort().get(0).isField()).isTrue();
            assertThat(req.sort().get(0).field().field()).isEqualTo("price");
            assertThat(req.sort().get(0).field().order()).isEqualTo(SortOrder.Asc);
        }

        @Test
        @DisplayName("HIGH_PRICE 정렬: price DESC sort 필드 설정 검증")
        void search_highPrice_buildsSortPriceDesc() throws Exception {
            SearchRequest req = captureBuiltSearchRequest(BookSortType.HIGH_PRICE, 0, 10);

            assertThat(req.sort()).hasSize(1);
            assertThat(req.sort().get(0).field().field()).isEqualTo("price");
            assertThat(req.sort().get(0).field().order()).isEqualTo(SortOrder.Desc);
        }

        @Test
        @DisplayName("REVIEW 정렬: reviewCount DESC sort 필드 설정 검증")
        void search_review_buildsSortReviewCountDesc() throws Exception {
            SearchRequest req = captureBuiltSearchRequest(BookSortType.REVIEW, 0, 10);

            assertThat(req.sort()).hasSize(1);
            assertThat(req.sort().get(0).field().field()).isEqualTo("reviewCount");
            assertThat(req.sort().get(0).field().order()).isEqualTo(SortOrder.Desc);
        }

        @Test
        @DisplayName("NEW 정렬: publishedDate DESC sort 필드 설정 검증")
        void search_new_buildsSortPublishedDateDesc() throws Exception {
            SearchRequest req = captureBuiltSearchRequest(BookSortType.NEW, 0, 10);

            assertThat(req.sort()).hasSize(1);
            assertThat(req.sort().get(0).field().field()).isEqualTo("publishedDate");
            assertThat(req.sort().get(0).field().order()).isEqualTo(SortOrder.Desc);
        }

        @Test
        @DisplayName("RATING 정렬: bool + filter(reviewCount>=100) + avgRating DESC sort 검증")
        void search_rating_buildsFilterAndSort() throws Exception {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> fnCaptor =
                    ArgumentCaptor.forClass(Function.class);

            when(client.search(fnCaptor.capture(), eq(Map.class)))
                    .thenReturn(stubSearchResponse(List.of(sourceWithId(1L, 1000, 4.9, 150L, "2024-01-01")), 1L));

            elasticService.search("k", BookSortType.RATING, 0, 10);

            SearchRequest.Builder b = new SearchRequest.Builder();
            SearchRequest req = fnCaptor.getValue().apply(b).build();

            assertThat(req.query()).isNotNull();
            assertThat(req.query().isBool()).isTrue();
            assertThat(req.sort()).hasSize(1);
            assertThat(req.sort().get(0).field().field()).isEqualTo("avgRating");
            assertThat(req.sort().get(0).field().order()).isEqualTo(SortOrder.Desc);
        }

        @Test
        @DisplayName("search 결과 매핑: 숫자형 변환/기본값 처리 검증")
        void search_mapsSourceToBookResponse() throws Exception {
            Map<String, Object> src = new HashMap<>();
            src.put("id", 7);                   // Integer -> Long
            src.put("title", "title");
            src.put("author", "author");
            src.put("isbn", "isbn");
            src.put("price", 1234L);            // Long -> Integer
            src.put("publishedDate", 20240101); // toString()
            // avgRating 누락 -> null
            // reviewCount 누락 -> 0L

            when(client.search(any(Function.class), eq(Map.class)))
                    .thenReturn(stubSearchResponse(List.of(src), 1L));

            SearchResult<BookResponse> result = elasticService.search("k", BookSortType.NEW, 0, 10);

            assertThat(result.totalHits()).isEqualTo(1L);
            BookResponse br = result.content().get(0);

            assertThat(br.bookId()).isEqualTo(7L);
            assertThat(br.price()).isEqualTo(1234);
            assertThat(br.publishedDate()).isEqualTo("20240101");
            assertThat(br.avgRating()).isNull();
            assertThat(br.reviewCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("client.search IOException 발생 시 RuntimeException(ES 검색 실패)로 래핑")
        void search_wrapsIOException() throws Exception {
            when(client.search(any(Function.class), eq(Map.class)))
                    .thenThrow(new IOException("boom"));

            assertThatThrownBy(() -> elasticService.search("k", BookSortType.NEW, 0, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES 검색 실패");
        }
    }

    // ===============================
    // saveAll() tests
    // ===============================
    @Nested
    @DisplayName("saveAll()")
    class SaveAllTests {

        @Test
        @DisplayName("null/empty 입력은 bulk 호출하지 않음")
        void saveAll_nullOrEmpty_noBulkCall() throws Exception {
            elasticService.saveAll(null);
            elasticService.saveAll(List.of());

            verify(client, never()).bulk(any(BulkRequest.class));
        }

        @Test
        @DisplayName("bulk errors=false면 예외 없음 + bulk 1회 호출")
        void saveAll_success_callsBulkOnce() throws Exception {
            BulkResponse ok = new BulkResponse.Builder()
                    .errors(false)
                    .items(List.of())
                    .took(1)
                    .build();

            when(client.bulk(any(BulkRequest.class))).thenReturn(ok);

            BookResponse b1 = new BookResponse(
                    1L, "t1", "a1", "i1", 1000, "img",
                    List.of(), List.of(), "c", "p", "2024-01-01",
                    4.5, 10L, "s", null
            );

            elasticService.saveAll(List.of(b1));
            verify(client, times(1)).bulk(any(BulkRequest.class));
        }

        @Test
        @DisplayName("bulk errors=true면 RuntimeException 발생")
        void saveAll_errorsTrue_throws() throws Exception {
            BulkResponse bad = new BulkResponse.Builder()
                    .errors(true)
                    .items(List.of())
                    .took(1)
                    .build();

            when(client.bulk(any(BulkRequest.class))).thenReturn(bad);

            BookResponse b1 = new BookResponse(
                    1L, "t1", "a1", "i1", 1000, "img",
                    List.of(), List.of(), "c", "p", "2024-01-01",
                    4.5, 10L, "s", null
            );

            assertThatThrownBy(() -> elasticService.saveAll(List.of(b1)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES bulk 인덱싱 중 일부 문서 실패");
        }

        @Test
        @DisplayName("bulk IOException이면 RuntimeException(ES bulk 인덱싱 실패)로 래핑")
        void saveAll_ioException_wraps() throws Exception {
            when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("io"));

            BookResponse b1 = new BookResponse(
                    1L, "t1", "a1", "i1", 1000, "img",
                    List.of(), List.of(), "c", "p", "2024-01-01",
                    4.5, 10L, "s", null
            );

            assertThatThrownBy(() -> elasticService.saveAll(List.of(b1)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES bulk 인덱싱 실패");
        }
    }

    // ===============================
    // update tests
    // ===============================
    @Nested
    @DisplayName("increase/decreaseReviewCount()")
    class ReviewCountTests {

        @Test
        @DisplayName("increaseReviewCount: update 호출 + index/id 설정 검증")
        void increaseReviewCount_callsUpdate() throws Exception {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Function<UpdateRequest.Builder<Object, Object>, ObjectBuilder<UpdateRequest<Object, Object>>>> fnCaptor =
                    ArgumentCaptor.forClass(Function.class);

            // update는 UpdateResponse<TDocument>를 반환하지만, 여기서는 반환값을 쓰지 않으므로 mock은 null로 둬도 됩니다.
            when(client.update(fnCaptor.capture(), eq(Object.class))).thenReturn(null);

            elasticService.increaseReviewCount(55L);

            UpdateRequest.Builder<Object, Object> b = new UpdateRequest.Builder<>();
            UpdateRequest<Object, Object> req = fnCaptor.getValue().apply(b).build();

            assertThat(req.index()).isEqualTo("high-five");
            assertThat(req.id()).isEqualTo("55");

            verify(client, times(1)).update(any(Function.class), eq(Object.class));
        }

        @Test
        @DisplayName("decreaseReviewCount: update 호출 + index/id 설정 검증")
        void decreaseReviewCount_callsUpdate() throws Exception {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Function<UpdateRequest.Builder<Object, Object>, ObjectBuilder<UpdateRequest<Object, Object>>>> fnCaptor =
                    ArgumentCaptor.forClass(Function.class);

            when(client.update(fnCaptor.capture(), eq(Object.class))).thenReturn(null);

            elasticService.decreaseReviewCount(77L);

            UpdateRequest.Builder<Object, Object> b = new UpdateRequest.Builder<>();
            UpdateRequest<Object, Object> req = fnCaptor.getValue().apply(b).build();

            assertThat(req.index()).isEqualTo("high-five");
            assertThat(req.id()).isEqualTo("77");

            verify(client, times(1)).update(any(Function.class), eq(Object.class));
        }

        @Test
        @DisplayName("update 중 예외 발생 시 RuntimeException으로 래핑")
        void update_wrapsException() throws Exception {
            when(client.update(any(Function.class), eq(Object.class)))
                    .thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> elasticService.increaseReviewCount(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES reviewCount 증가 실패");

            assertThatThrownBy(() -> elasticService.decreaseReviewCount(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES reviewCount 감소 실패");
        }
    }

    @Test
        @DisplayName("decreaseReviewCount: update 호출 + index/id 설정 검증")
        void decreaseReviewCount_callsUpdate() throws Exception {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Function<UpdateRequest.Builder<Void>, ObjectBuilder<UpdateRequest<Void>>>> fnCaptor =
                    ArgumentCaptor.forClass(Function.class);

            when(client.update(fnCaptor.capture(), eq(Void.class))).thenReturn(null);

            elasticService.decreaseReviewCount(77L);

            UpdateRequest.Builder<Void> b = new UpdateRequest.Builder<>();
            UpdateRequest<Void> req = fnCaptor.getValue().apply(b).build();

            assertThat(req.index()).isEqualTo("high-five");
            assertThat(req.id()).isEqualTo("77");
            verify(client, times(1)).update(any(Function.class), eq(Void.class));
        }

        @Test
        @DisplayName("update 중 예외 발생 시 RuntimeException으로 래핑")
        void update_wrapsException() throws Exception {
            when(client.update(any(Function.class), eq(Void.class)))
                    .thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> elasticService.increaseReviewCount(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES reviewCount 증가 실패");

            assertThatThrownBy(() -> elasticService.decreaseReviewCount(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES reviewCount 감소 실패");
        }
    }

