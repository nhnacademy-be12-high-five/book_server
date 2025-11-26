package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.SearchLog;
import com.nhnacademy.book_server.repository.SearchLogRepository;
import com.nhnacademy.book_server.service.search.SearchLogServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SearchLogServiceImplTest {

    @Mock
    SearchLogRepository searchLogRepository;

    @InjectMocks
    SearchLogServiceImpl searchLogService;

    @Test
    void setSearchLog_first(){
        String keyword ="처음";
        when(searchLogRepository.findByKeyword(keyword)).thenReturn(Optional.empty()); //given

        searchLogService.setSearchLog(keyword); //when

        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class); //then
        verify(searchLogRepository, times(1)).save(captor.capture());

        Assertions.assertEquals(keyword,captor.getValue().getKeyword());
        Assertions.assertEquals(1L,captor.getValue().getSearchCount());
    }

    @Test
    void setSearchLog_exist(){
        String keyword="존재";
        SearchLog log = SearchLog.builder().keyword(keyword).searchCount(5L).build();

        when(searchLogRepository.findByKeyword(keyword)).thenReturn(Optional.of(log));

        searchLogService.setSearchLog(keyword); //when

        verify(searchLogRepository,times(1)).save(log); //then
        Assertions.assertEquals(6L, log.getSearchCount());
    }

    @Test
    void geetSearchCount_nonExists(){
        when(searchLogRepository.findByKeyword("없는 키워드")).thenReturn(Optional.empty());

        long count = searchLogService.getSearchCount("없는 키워드");

        Assertions.assertEquals(0L,count);
    }

    @Test
    void getSearchCount_exist(){
        String keyword="자바";
        when(searchLogRepository.findByKeyword(keyword)).thenReturn(Optional.of(SearchLog.builder().keyword(keyword).searchCount(10L).build()));

        long count = searchLogService.getSearchCount(keyword);

        Assertions.assertEquals(10L, count);
    }

}
