package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.ObjectBuilder;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ElasticService.class)
class ElasticServiceTest {

    @MockitoBean
    ElasticsearchClient client;

    @Resource
    ElasticService service;

    private static final String INDEX = "high-five";

    // =======================
    // Helpers
    // =======================

    private SearchResponse<Map> buildSearchResponse(long total, List<Map<String, Object>> sources) {
        List<Hit<Map>> hits = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> src = sources.get(i);
            String id = String.valueOf(src.getOrDefault("bookId", i + 1));

            hits.add(new Hit.Builder<Map>()
                    .index(INDEX)
                    .id(id)
                    .source((Map) src)
                    .build());
        }

        ShardStatistics shards = new ShardStatistics.Builder()
                .total(1).successful(1).skipped(0).failed(0)
                .build();

        HitsMetadata<Map> hitsMetadata = new HitsMetadata.Builder<Map>()
                .total(new TotalHits.Builder()
                        .value(total)
                        .relation(TotalHitsRelation.Eq)
                        .build())
                .hits(hits)
                .build();

        return new SearchResponse.Builder<Map>()
                .took(1)
                .timedOut(false)
                .shards(shards)
                .hits(hitsMetadata)
                .build();
    }

    private SearchRequest captureAndBuildSearchRequest() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Function.class);

        verify(client).search(captor.capture(), eq(Map.class));

        Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn = captor.getValue();
        SearchRequest.Builder builder = new SearchRequest.Builder();
        return fn.apply(builder).build();
    }

    private UpdateRequest<Void, Void> captureAndBuildUpdateRequest() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<UpdateRequest.Builder<Void, Void>, ObjectBuilder<UpdateRequest<Void, Void>>>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Function.class);

        verify(client).update(captor.capture(), eq(Void.class));

        Function<UpdateRequest.Builder<Void, Void>, ObjectBuilder<UpdateRequest<Void, Void>>> fn = captor.getValue();
        UpdateRequest.Builder<Void, Void> b = new UpdateRequest.Builder<>();
        return fn.apply(b).build();
    }

    private Map<String, Object> bookSrc(long id,
                                        String title,
                                        Integer price,
                                        Double rating,
                                        Long reviewCount,
                                        String publishedDate) {
        Map<String, Object> m = new HashMap<>();
        m.put("bookId", id);
        m.put("title", title);
        m.put("author", "author");
        m.put("isbn13", "isbn");
        m.put("price", price);
        m.put("image", "img");
        m.put("content", "content");
        m.put("publisher", "pub");
        m.put("publishedDate", publishedDate);
        m.put("avgRating", rating);
        m.put("reviewCount", reviewCount);

        List<Map<String, Object>> cats = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("categoryId", 10);
        c1.put("categoryName", "카테고리");
        cats.add(c1);
        m.put("categories", cats);

        return m;
    }

    // =======================
    // search() input validation
    // =======================

    @Test
    @DisplayName("search: keyword null/blank면 빈 결과 + client 호출 없음")
    void search_blankKeyword_returnsEmpty_noClientCall() {
        SearchResult<BookResponse> r1 = service.search(null, BookSortType.POPULAR, 0, 10);
        SearchResult<BookResponse> r2 = service.search("   ", BookSortType.POPULAR, 0, 10);

        assertThat(r1.totalHits()).isZero();
        assertThat(r1.content()).isEmpty();
        assertThat(r2.totalHits()).isZero();
        assertThat(r2.content()).isEmpty();

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("search: IOException 발생 시 BusinessException 래핑")
    void search_ioException_wrapsRuntimeException() throws Exception {
        doThrow(new IOException("io"))
                .when(client)
                .search(
                        ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class)
                );

        assertThatThrownBy(() -> service.search("키워드", BookSortType.POPULAR, 0, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.EXTERNAL_SERVER_ERROR.getMessage());
    }

    // =======================
    // POPULAR
    // =======================

    @Test
    @DisplayName("search: POPULAR -> function_score + boostMode=REPLACE + sort=_score desc + trackTotalHits true")
    void search_popular_buildsFunctionScoreAndSort() throws Exception {
        SearchResponse<Map> response = buildSearchResponse(
                2,
                List.of(
                        bookSrc(1, "A", 1000, 4.5, 150L, "2024-01-01"),
                        bookSrc(2, "B", 2000, 4.2, 90L, "2023-12-01")
                )
        );

        doReturn(response)
                .when(client)
                .search(
                        ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class) // ✅ 여기 반드시 Map.class
                );

        SearchResult<BookResponse> result = service.search("지리산", BookSortType.POPULAR, 1, 20);

        assertThat(result.totalHits()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).bookId()).isEqualTo(1L);
        assertThat(result.content().get(0).title()).isEqualTo("A");
        assertThat(result.content().get(0).price()).isEqualTo(1000);

        SearchRequest req = captureAndBuildSearchRequest();

        assertThat(req.index()).contains(INDEX);
        assertThat(req.from()).isEqualTo(20);
        assertThat(req.size()).isEqualTo(20);

        assertThat(req.trackTotalHits()).isNotNull();
        assertThat(req.trackTotalHits().enabled()).isTrue();

        Query q = req.query();
        assertThat(q).isNotNull();
        assertThat(q.isFunctionScore()).isTrue();
        assertThat(q.functionScore().boostMode()).isEqualTo(FunctionBoostMode.Replace);

        assertThat(req.sort()).isNotEmpty();
        SortOptions so = req.sort().get(0);
        assertThat(so.isScore()).isTrue();
        assertThat(so.score().order()).isEqualTo(SortOrder.Desc);
    }

    // =======================
    // RATING
    // =======================

    @Test
    @DisplayName("search: RATING -> bool(must=baseQuery, filter=reviewCount>=100) + sort avgRating desc")
    void search_rating_buildsFilterAndSort() throws Exception {
        SearchResponse<Map> response = buildSearchResponse(
                1,
                List.of(bookSrc(10, "R", 1500, 4.9, 200L, "2022-01-01"))
        );

        doReturn(response)
                .when(client)
                .search(
                        ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class)
                );

        SearchResult<BookResponse> result = service.search("지리산", BookSortType.RATING, 0, 10);

        assertThat(result.totalHits()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).avgRating()).isEqualTo(4.9);
        assertThat(result.content().get(0).reviewCount()).isEqualTo(200L);

        SearchRequest req = captureAndBuildSearchRequest();

        Query q = req.query();
        assertThat(q.isBool()).isTrue();

        var bool = q.bool();
        assertThat(bool.must()).isNotEmpty();
        assertThat(bool.filter()).isNotEmpty();

        Query filterQ = bool.filter().get(0);
        assertThat(filterQ.isRange()).isTrue();

        assertThat(filterQ.range().number().field()).isEqualTo("reviewCount");
        assertThat(filterQ.range().number().gte()).isEqualTo(100.0);

        SortOptions so = req.sort().get(0);
        assertThat(so.isField()).isTrue();
        assertThat(so.field().field()).isEqualTo("avgRating");
        assertThat(so.field().order()).isEqualTo(SortOrder.Desc);

        assertThat(req.trackTotalHits().enabled()).isTrue();
    }

    // =======================
    // Other sorts
    // =======================

    @Test
    @DisplayName("search: LOW_PRICE -> sort price asc")
    void search_lowPrice_sortPriceAsc() throws Exception {
        doReturn(buildSearchResponse(0, List.of()))
                .when(client)
                .search(ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class));

        service.search("키워드", BookSortType.LOW_PRICE, 0, 5);
        SearchRequest req = captureAndBuildSearchRequest();

        SortOptions so = req.sort().get(0);
        assertThat(so.isField()).isTrue();
        assertThat(so.field().field()).isEqualTo("price");
        assertThat(so.field().order()).isEqualTo(SortOrder.Asc);
    }

    @Test
    @DisplayName("search: HIGH_PRICE -> sort price desc")
    void search_highPrice_sortPriceDesc() throws Exception {
        doReturn(buildSearchResponse(0, List.of()))
                .when(client)
                .search(ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class));

        service.search("키워드", BookSortType.HIGH_PRICE, 0, 5);
        SearchRequest req = captureAndBuildSearchRequest();

        SortOptions so = req.sort().get(0);
        assertThat(so.isField()).isTrue();
        assertThat(so.field().field()).isEqualTo("price");
        assertThat(so.field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    @DisplayName("search: REVIEW -> sort reviewCount desc")
    void search_review_sortReviewCountDesc() throws Exception {
        doReturn(buildSearchResponse(0, List.of()))
                .when(client)
                .search(ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class));

        service.search("키워드", BookSortType.REVIEW, 0, 5);
        SearchRequest req = captureAndBuildSearchRequest();

        SortOptions so = req.sort().get(0);
        assertThat(so.isField()).isTrue();
        assertThat(so.field().field()).isEqualTo("reviewCount");
        assertThat(so.field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    @DisplayName("search: NEW -> sort publishedDate desc")
    void search_new_sortPublishedDateDesc() throws Exception {
        doReturn(buildSearchResponse(0, List.of()))
                .when(client)
                .search(ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class));

        service.search("키워드", BookSortType.NEW, 0, 5);
        SearchRequest req = captureAndBuildSearchRequest();

        SortOptions so = req.sort().get(0);
        assertThat(so.isField()).isTrue();
        assertThat(so.field().field()).isEqualTo("publishedDate");
        assertThat(so.field().order()).isEqualTo(SortOrder.Desc);
    }

    // =======================
    // baseQuery structure
    // =======================

    @Test
    @DisplayName("search: baseQuery는 bool(must=multiMatch AND, should=title 관련 boost 쿼리 최소 1개 이상)")
    void search_baseQuery_structure_verified() throws Exception {
        doReturn(buildSearchResponse(0, List.of()))
                .when(client)
                .search(ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                        eq(Map.class));

        service.search("지리산", BookSortType.LOW_PRICE, 0, 10);
        SearchRequest req = captureAndBuildSearchRequest();

        Query base = req.query();
        assertThat(base.isBool()).isTrue();

        var b = base.bool();

        // must: multiMatch(query="지리산", operator=AND) 유지 검증
        assertThat(b.must()).isNotEmpty();
        Query must0 = b.must().get(0);
        assertThat(must0.isMultiMatch()).isTrue();
        assertThat(must0.multiMatch().query()).isEqualTo("지리산");
        assertThat(must0.multiMatch().operator()).isEqualTo(Operator.And);

        // should: "title" 관련 boost 쿼리가 최소 1개 이상 존재하면 통과
        // (구현이 title.enum / title.keyword / title / title.ngram 등 어떤 필드명을 쓰든 대응)
        boolean hasTitleBoostedShould = b.should().stream().anyMatch(q -> {
            // 1) match 쿼리 형태
            if (q.isMatch()) {
                String field = q.match().field();
                boolean isTitleField = field != null && field.startsWith("title");
                boolean isSameKeyword = Objects.equals("지리산", q.match().query());
                boolean hasBoost = q.match().boost() != null;
                return isTitleField && isSameKeyword && hasBoost;
            }

            // 2) match_phrase 쿼리 형태
            if (q.isMatchPhrase()) {
                String field = q.matchPhrase().field();
                boolean isTitleField = field != null && field.startsWith("title");
                boolean isSameKeyword = Objects.equals("지리산", q.matchPhrase().query());
                boolean hasBoost = q.matchPhrase().boost() != null;
                return isTitleField && isSameKeyword && hasBoost;
            }

            // 3) multi_match를 should에 넣는 구현도 흔함 (fields에 title이 들어가고 boost가 있거나, title^가 있으면 OK)
            if (q.isMultiMatch()) {
                var mm = q.multiMatch();
                boolean isSameKeyword = Objects.equals("지리산", mm.query());
                boolean hasTitleField = mm.fields() != null && mm.fields().stream().anyMatch(f ->
                        f != null && (f.startsWith("title") || f.contains("title^"))
                );
                // multiMatch는 boost가 없을 수도 있어 fields의 ^로 주는 경우도 있으니 둘 중 하나라도 만족하면 OK
                boolean hasBoost = mm.boost() != null || (mm.fields() != null && mm.fields().stream().anyMatch(f -> f != null && f.contains("^")));
                return isSameKeyword && hasTitleField && hasBoost;
            }

            return false;
        });

        assertThat(hasTitleBoostedShould).isTrue();
    }

    // =======================
    // saveAll
    // =======================

    @Test
    @DisplayName("saveAll: null/empty면 bulk 호출 안 함")
    void saveAll_empty_noBulkCall() throws Exception {
        service.saveAll(null);
        service.saveAll(List.of());
        verify(client, never()).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("saveAll: 정상 bulk -> errors=false면 예외 없음")
    void saveAll_success_bulk() throws Exception {
        BulkResponse br = mock(BulkResponse.class);
        when(br.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(br);

        BookResponse b1 = new BookResponse(
                1L, "T", "A", "I", 1000, "img",
                List.of(), List.of(), "c", "p", "2024-01-01",
                4.0, 10L, null, null,
                null, null
        );

        service.saveAll(List.of(b1));
        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    @DisplayName("saveAll: bulk errors=true면 BusinessException")
    void saveAll_bulkErrors_throws() throws Exception {
        BulkResponse br = mock(BulkResponse.class);
        when(br.errors()).thenReturn(true);
        when(client.bulk(any(BulkRequest.class))).thenReturn(br);

        BookResponse b1 = new BookResponse(
                1L, "T", "A", "I", 1000, "img",
                List.of(), List.of(), "c", "p", "2024-01-01",
                4.0, 10L, null, null,
                null, null
        );

        List<BookResponse> booksToSave = List.of(b1);

        assertThatThrownBy(() -> service.saveAll(booksToSave))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.EXTERNAL_SERVER_ERROR.getMessage());
    }

    // =======================
    // reviewCount update
    // =======================

    @Test
    @DisplayName("increaseReviewCount: update 호출 + 스크립트 포함")
    void increaseReviewCount_callsUpdate_withScript() throws Exception {
        doReturn(null).when(client)
                .update(ArgumentMatchers.<Function<UpdateRequest.Builder<Void, Void>, ObjectBuilder<UpdateRequest<Void, Void>>>>any(),
                        eq(Void.class));

        service.increaseReviewCount(99L);

        UpdateRequest<Void, Void> req = captureAndBuildUpdateRequest();
        assertThat(req.index()).isEqualTo(INDEX);
        assertThat(req.id()).isEqualTo("99");
        assertThat(req.script()).isNotNull();
        assertThat(req.script().source()).contains("reviewCount += 1");
    }

    @Test
    @DisplayName("decreaseReviewCount: update 호출 + 스크립트 포함")
    void decreaseReviewCount_callsUpdate_withScript() throws Exception {
        doReturn(null).when(client)
                .update(ArgumentMatchers.<Function<UpdateRequest.Builder<Void, Void>, ObjectBuilder<UpdateRequest<Void, Void>>>>any(),
                        eq(Void.class));

        service.decreaseReviewCount(100L);

        UpdateRequest<Void, Void> req = captureAndBuildUpdateRequest();
        assertThat(req.index()).isEqualTo(INDEX);
        assertThat(req.id()).isEqualTo("100");
        assertThat(req.script()).isNotNull();
        assertThat(req.script().source()).contains("Math.max(0, ctx._source.reviewCount - 1)");
    }

    @Test
    @DisplayName("updateReviewCount: update 예외 발생 시 BusinessException 래핑")
    void updateReviewCount_exception_wrapped() throws Exception {
        doThrow(new RuntimeException("boom")).when(client)
                .update(ArgumentMatchers.<Function<UpdateRequest.Builder<Void, Void>, ObjectBuilder<UpdateRequest<Void, Void>>>>any(),
                        eq(Void.class));

        assertThatThrownBy(() -> service.increaseReviewCount(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.EXTERNAL_SERVER_ERROR.getMessage());
    }
}
