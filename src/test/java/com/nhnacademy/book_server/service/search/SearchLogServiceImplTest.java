package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.response.SearchLogResponse;
import com.nhnacademy.book_server.entity.SearchLog;
import com.nhnacademy.book_server.repository.SearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchLogServiceImplTest {

    @Mock
    SearchLogRepository searchLogRepository;

    SearchLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchLogServiceImpl(searchLogRepository);
    }

    @Test
    @DisplayName("setSearchLog: 기존 키워드가 있으면 count 증가 후 save 호출")
    void setSearchLog_existingKeyword_increaseAndSave() {
        String keyword = "java";

        SearchLog existing = mock(SearchLog.class);
        when(searchLogRepository.findByKeyword(keyword)).thenReturn(Optional.of(existing));

        service.setSearchLog(keyword);

        verify(existing, times(1)).increaseCount();
        verify(searchLogRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("setSearchLog: 기존 키워드가 없으면 새 엔티티 생성 -> count 증가 -> save 호출")
    void setSearchLog_newKeyword_createIncreaseAndSave() {
        String keyword = "spring";

        // orElseGet 경로 타게: empty
        when(searchLogRepository.findByKeyword(keyword)).thenReturn(Optional.empty());

        // SearchLog.builder()로 만든 실제 객체를 캡처해서 검증
        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);

        service.setSearchLog(keyword);

        verify(searchLogRepository).save(captor.capture());
        SearchLog saved = captor.getValue();

        // 새로 생성된 객체가 keyword를 가지고 있는지(빌더 세팅) 검증
        // (SearchLog에 getKeyword()/getSearchCount()가 있다는 전제: 서비스도 사용 중)
        assertThat(saved.getKeyword()).isEqualTo(keyword);
        // increaseCount()가 호출되어야 하므로 0 -> 1이 되었을 가능성이 큼
        // 단, increaseCount() 구현이 다를 수 있으니 최소한 null 아님/0 이상만 보수적으로 체크
        assertThat(saved.getSearchCount()).isPositive();

        verify(searchLogRepository, times(1)).findByKeyword(keyword);
    }

    @Test
    @DisplayName("getPopularKeywords: limit<=0이면 기본값 10 적용 + repository 호출 + DTO 매핑")
    void getPopularKeywords_limitNonPositive_default10_andMapping() {
        // 12개를 넣어 두면 기본값 10이 정확히 적용되는지 검증 가능
        List<SearchLog> logs = IntStreamTestHelper.makeLogs(12);

        when(searchLogRepository.findAllByOrderBySearchCountDesc()).thenReturn(logs);

        List<SearchLogResponse> result = service.getPopularKeywords(0);

        verify(searchLogRepository, times(1)).findAllByOrderBySearchCountDesc();

        assertThat(result).hasSize(10);

        // 정렬은 repo에서 이미 desc로 준 것으로 가정하므로
        // mapping 정확성만 검증(키워드/카운트)
        SearchLog first = logs.get(0);
        assertThat(result.get(0).keyword()).isEqualTo(first.getKeyword());
        assertThat(result.get(0).searchCount()).isEqualTo(first.getSearchCount());
    }

    @Test
    @DisplayName("getPopularKeywords: limit이 양수면 해당 개수만큼 limit 적용")
    void getPopularKeywords_positiveLimit_appliesLimit() {
        List<SearchLog> logs = IntStreamTestHelper.makeLogs(5);
        when(searchLogRepository.findAllByOrderBySearchCountDesc()).thenReturn(logs);

        List<SearchLogResponse> result = service.getPopularKeywords(3);

        assertThat(result).hasSize(3);
        assertThat(result.get(2).keyword()).isEqualTo(logs.get(2).getKeyword());
        assertThat(result.get(2).searchCount()).isEqualTo(logs.get(2).getSearchCount());
    }

    @Test
    @DisplayName("getPopularKeywords: limit이 전체보다 크면 전체만 반환")
    void getPopularKeywords_limitGreaterThanSize_returnsAll() {
        List<SearchLog> logs = IntStreamTestHelper.makeLogs(4);
        when(searchLogRepository.findAllByOrderBySearchCountDesc()).thenReturn(logs);

        List<SearchLogResponse> result = service.getPopularKeywords(100);

        assertThat(result).hasSize(4);
        assertThat(result.get(3).keyword()).isEqualTo(logs.get(3).getKeyword());
    }

    /**
     * 테스트에서 SearchLog를 실제 객체로 만들기 위한 헬퍼.
     * - service 구현이 getKeyword()/getSearchCount()를 사용하므로 실제 값이 필요합니다.
     * - SearchLog 엔티티가 Lombok builder를 지원한다는 전제로 작성했습니다.
     */
    static class IntStreamTestHelper {
        static List<SearchLog> makeLogs(int n) {
            // searchCount desc인 상황을 만들기 위해 n..1로 넣음
            // keyword는 k1..kn 형태
            java.util.ArrayList<SearchLog> list = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                long count = (long) (n - i);
                String keyword = "k" + (i + 1);

                SearchLog log = SearchLog.builder()
                        .keyword(keyword)
                        .searchCount(count)
                        .build();

                list.add(log);
            }
            return list;
        }
    }
}
