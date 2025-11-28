package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.response.AladinSearchResponse;
import com.nhnacademy.book_server.service.BookService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AladinService {

    private final RestTemplate restTemplate;
    private final BookService bookService;

    @Value("${aladin.ttb-key}")
    private String ttbKey;

    private static final String BASE_URL =
            "http://www.aladin.co.kr/ttb/api/ItemSearch.aspx?ttbkey={ttbKey}&Query={query}&queryType={queryType}&SearchTarget=Book&output=JS&Version=20131101";

    public AladinSearchResponse searchBooks(String query, String queryType) {

        // RestTemplate.getForObject()를 사용하여 GET 요청을 보내고,
        // 응답 JSON을 AladinResponse.class로 자동 변환합니다.

        Object[] params = new Object[] {ttbKey, query};

        // 알라딘 API는 기본적으로 XML을 반환하므로 output=js 파라미터를 추가하여 JSOn 응답을 받도록 설정해야
        // spring template가 자동으로 json을 POJO로 변환

        try {
            return restTemplate.getForObject(
                    BASE_URL,
                    AladinSearchResponse.class, // JSON을 변환할 최종 POJO 클래스
                    ttbKey,               // URL의 {ttbKey}에 매핑
                    query,                // URL의 {query}에 매핑
                    queryType
            );
        } catch (Exception e) {
            // HTTP 통신 오류, JSON 파싱 오류 등을 처리
            throw new RuntimeException("알라딘 API 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

}
