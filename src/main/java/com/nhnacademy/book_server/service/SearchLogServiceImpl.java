package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.SearchLog;
import com.nhnacademy.book_server.repository.SearchLogRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor //final 필드를 매개변수로 받을때 생성자 자동생성
@Transactional(readOnly = true)
public class SearchLogServiceImpl implements SearchLogService{

    private final SearchLogRepository searchLogRepository;

    @Override
    @Transactional
    public void setSearchLog(String keyword){
        SearchLog log = searchLogRepository.findByKeyword(keyword).orElseGet(()-> SearchLog.builder().keyword(keyword).searchCount(0L).build());
        log.increaseCount(); //검색 시 해당키워드 있으면 ++, 없으면 새로 만들고 1로 설정
        searchLogRepository.save(log); //엔티티 -> DB에 삽입
    }

    @Override
    public long getSearchCount(String keyword){
        return searchLogRepository.findByKeyword(keyword).map(SearchLog::getSearchCount).orElse(0L);
    }

}
