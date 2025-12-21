package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.KakaoBookSearchResponse;
import com.nhnacademy.book_server.dto.request.BookCreateRequest;
import com.nhnacademy.book_server.dto.response.GoogleBookResponse;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookRegistrationService {
    private final RestTemplate restTemplate;
    private final GeminiTextClientService geminiService;

    private static final String KAKAO_BOOKS_API_URL = "https://dapi.kakao.com/v3/search/book";
    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes";

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    public BookCreateRequest getBookInfoWithAi(String isbn) {
        if (isbn == null || !isbn.matches("^(\\d{10}|\\d{13})$")) {
            throw new IllegalArgumentException("유효하지 않은 ISBN 형식입니다: " + isbn);
        }

        BookCreateRequest request = null;

        try {
            request = searchKakao(isbn);
        } catch (Exception e) {
            log.warn("카카오 검색 실패 또는 결과 없음. 구글 검색으로 전환합니다. ISBN: {}", isbn);
        }

        // 2. [2순위] 카카오에 없으면 구글 API 검색 시도
        if (request == null) {
            try {
                request = searchGoogle(isbn);
            } catch (Exception e) {
                log.error("구글 검색 실패. ISBN: {}", isbn, e);
                throw new RuntimeException("해당 ISBN으로 도서를 찾을 수 없습니다: " + isbn);
            }
        }

        String kyoboImageUrl = "https://contents.kyobobook.co.kr/sih/fit-in/200x0/pdt/" + isbn + ".jpg";
        request.setImage(kyoboImageUrl);

        // Gemini에게 서평 작성 요청
        String aiGeneratedContent = enhanceDescriptionWithGemini(request.getTitle(), request.getAuthors(), request.getDescription());
        request.setDescription(aiGeneratedContent);

        return request;
    }

    private BookCreateRequest searchKakao(String isbn) {
        // 헤더 설정 (KakaoAK)
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // URL 생성
        URI uri = UriComponentsBuilder.fromHttpUrl(KAKAO_BOOKS_API_URL)
                .queryParam("target", "isbn")
                .queryParam("query", isbn)
                .build()
                .toUri();

        // 호출
        ResponseEntity<KakaoBookSearchResponse> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, KakaoBookSearchResponse.class
        );

        KakaoBookSearchResponse body = response.getBody();

        if (body == null || body.getDocuments() == null || body.getDocuments().isEmpty()) {
            return null; // 결과 없음
        }

        // 첫 번째 결과 매핑
        KakaoBookSearchResponse.Document doc = body.getDocuments().get(0);

        BookCreateRequest req = new BookCreateRequest();
        req.setIsbn(isbn);
        req.setTitle(doc.getTitle());
        req.setPublisher(StringUtils.hasText(doc.getPublisher()) ? doc.getPublisher() : "출판사 정보 없음");

        // 날짜 포맷 (ISO 8601 -> YYYY-MM-DD)
        req.setPublishedDate(formatDate(doc.getDatetime()));

        // 가격 (카카오는 정가를 제공함)
        req.setPrice(doc.getPrice() != null ? doc.getPrice() : 0);

        req.setAuthors(doc.getAuthors() != null ? doc.getAuthors() : new ArrayList<>());

        // 카카오의 contents는 줄거리 요약이 포함되어 있어 품질이 좋음
        req.setDescription(doc.getContents());

        log.info("카카오 API 검색 성공: {}", doc.getTitle());
        return req;
    }

    private BookCreateRequest searchGoogle(String isbn) {
        URI uri = UriComponentsBuilder.fromHttpUrl(GOOGLE_BOOKS_API_URL)
                .queryParam("q", "isbn:" + isbn)
                .build()
                .toUri();

        GoogleBookResponse response = restTemplate.getForObject(uri, GoogleBookResponse.class);

        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return null; // 결과 없음
        }

        GoogleBookResponse.VolumeInfo info = response.getItems().get(0).getVolumeInfo();

        BookCreateRequest req = new BookCreateRequest();
        req.setIsbn(isbn);
        req.setTitle(info.getTitle());
        req.setPublisher(StringUtils.hasText(info.getPublisher()) ? info.getPublisher() : "출판사 정보 없음");
        req.setPublishedDate(formatDate(info.getPublishedDate()));
        req.setPrice(0); // 구글은 가격 정보가 없는 경우가 많음
        req.setAuthors(info.getAuthors() != null ? info.getAuthors() : new ArrayList<>());
        req.setDescription(info.getDescription());

        log.info("구글 API 검색 성공: {}", info.getTitle());
        return req;
    }

    private String enhanceDescriptionWithGemini(String title, List<String> authors, String originalDescription) {
        String authorStr = (authors != null && !authors.isEmpty()) ? String.join(", ", authors) : "미상";

        StringBuilder prompt = new StringBuilder();
        // 해외 도서(구글 검색 결과)일 수도 있으므로 '한국어로 작성해달라'는 요청을 명시
        prompt.append("너는 전문 도서 MD야. 다음 책에 대해 독자의 구매욕구를 자극하는 상세한 서평 스타일의 소개글을 **한국어로** 작성해줘.\n");
        prompt.append("책 제목: ").append(title).append("\n");
        prompt.append("저자: ").append(authorStr).append("\n");

        if (StringUtils.hasText(originalDescription)) {
            prompt.append("참고할 원문 설명: ").append(originalDescription).append("\n");
        } else {
            prompt.append("정보가 부족하므로 제목과 저자를 바탕으로 내용을 추론해서 풍성하게 작성해줘.\n");
        }

        prompt.append("\n[작성 규칙]\n");
        prompt.append("1. **책 제목과 '이 책을 선택해야 하는 이유' 같은 소제목은 반드시 <h3> 태그를 사용해.**\n");
        prompt.append("2. <h3> 태그 앞에는 반드시 줄바꿈을 두 번 넣어줘.\n");
        prompt.append("3. 본문 내용은 <p> 태그로 감싸고, 핵심 내용은 <ul>, <li> 리스트로 작성해.\n");
        prompt.append("4. **모든 내용은 반드시 자연스러운 한국어로 작성해.**\n");
        prompt.append("5. 문단 사이에는 적절한 줄바꿈을 넣어줘.\n");

        try {
            return geminiService.generateAnswer(prompt.toString());
        } catch (Exception e) {
            log.error("Gemini 호출 실패");
            return originalDescription != null ? originalDescription.replace("\n", "<br>") : "";
        }
    }

    private String formatDate(String date) {
        if (date == null) return null;
        // 연도만 있는 경우 (예: "2023")
        if (date.matches("^\\d{4}$")) {
            return date + "-01-01";
        }
        // 연도-월 형식 (예: "2023-05")
        if (date.matches("^\\d{4}-\\d{2}$")) {
            return date + "-01";
        }
        // 이미 완전한 형식이거나 기타 형식
        return date;
    }

}
