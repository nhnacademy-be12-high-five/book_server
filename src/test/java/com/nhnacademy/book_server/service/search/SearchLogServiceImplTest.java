package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.entity.SearchLog;
import com.nhnacademy.book_server.repository.SearchLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchLogServiceImplTest {

    @Mock
    SearchLogRepository searchLogRepository;

    @InjectMocks
    SearchLogServiceImpl searchLogService;

    @Test
    @DisplayName("기존 로그가 없으면 새 SearchLog를 생성하고 searchCount=1로 저장")
    void setSearchLog_whenNotExist_createNew() {
        // given
        String keyword = "자바";
        when(searchLogRepository.findByKeyword(keyword))
                .thenReturn(Optional.empty());

        // when
        searchLogService.setSearchLog(keyword);

        // then
        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogRepository).save(captor.capture());

        SearchLog saved = captor.getValue();
        assertThat(saved.getKeyword()).isEqualTo(keyword);
        assertThat(saved.getSearchCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기존 로그가 있으면 searchCount를 1 증가시켜 저장")
    void setSearchLog_whenExist_increaseCount() {
        // given
        String keyword = "자바";
        SearchLog existing = SearchLog.builder()
                .id(1L)
                .keyword(keyword)
                .searchCount(3L)
                .build();

        when(searchLogRepository.findByKeyword(keyword))
                .thenReturn(Optional.of(existing));

        // when
        searchLogService.setSearchLog(keyword);

        // then
        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogRepository).save(captor.capture());

        SearchLog saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getKeyword()).isEqualTo(keyword);
        assertThat(saved.getSearchCount()).isEqualTo(4L); // 3 -> 4
    }
}
