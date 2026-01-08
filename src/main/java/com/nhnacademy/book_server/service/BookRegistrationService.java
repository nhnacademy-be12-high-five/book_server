package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.KakaoBookSearchResponse;
import com.nhnacademy.book_server.dto.response.GoogleBookResponse;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookRegistrationService {
    private final RestTemplate restTemplate;
    private final GeminiTextClientService geminiService;
    private final MinioImageService minioImageService;

    private static final String KAKAO_BOOKS_API_URL = "https://dapi.kakao.com/v3/search/book";
    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes";

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    public BookInfoDto getBookInfoWithAi(String isbn) {
        if (isbn == null || !isbn.matches("^(\\d{10}|\\d{13})$")) {
            throw new IllegalArgumentException("유효하지 않은 ISBN 형식입니다: " + isbn);
        }

        BookInfoDto dto = null;

        try {
            dto = searchKakao(isbn);
        } catch (Exception e) {
            log.warn("카카오 검색 실패 또는 결과 없음. 구글 검색으로 전환합니다. ISBN: {}", isbn);
        }

        // 2. [2순위] 카카오에 없으면 구글 API 검색 시도
        if (dto == null) {
            try {
                dto = searchGoogle(isbn);
            } catch (Exception e) {
                log.error("구글 검색 실패. ISBN: {}", isbn, e);
                throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
            }
        }

        if (dto == null) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }


        String kyoboImageUrl = "https://contents.kyobobook.co.kr/sih/fit-in/200x0/pdt/" + isbn + ".jpg";
        String minioImageUrl = minioImageService.uploadImageFromUrl(kyoboImageUrl, isbn);
        dto.setImage(minioImageUrl);

        // Gemini에게 서평 작성 요청
        String authorStr = (dto.getAuthors() != null && !dto.getAuthors().isEmpty()) ? dto.getAuthors().get(0) : "미상";
        String aiGeneratedContent = enhanceDescriptionWithGemini(dto.getTitle(), authorStr, dto.getDescription());
        dto.setDescription(aiGeneratedContent);

        return dto;
    }

    private BookInfoDto searchKakao(String isbn) {
        // URL 생성
        URI uri = UriComponentsBuilder.fromUriString(KAKAO_BOOKS_API_URL)
                .queryParam("target", "isbn")
                .queryParam("query", isbn)
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

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

        return BookInfoDto.builder()
                .isbn(isbn)
                .title(doc.getTitle())
                .publisher(StringUtils.hasText(doc.getPublisher()) ? doc.getPublisher() : "출판사 정보 없음")
                .publishedDate(parseToLocalDate(doc.getDatetime()))
                .price(doc.getPrice() != null ? doc.getPrice() : 0)
                .authors(doc.getAuthors() != null ? doc.getAuthors() : Collections.emptyList())
                .description(doc.getContents())
                .build();
    }

    private BookInfoDto searchGoogle(String isbn) {
        URI uri = UriComponentsBuilder.fromUriString(GOOGLE_BOOKS_API_URL)
                .queryParam("q", "isbn:" + isbn)
                .build()
                .toUri();

        GoogleBookResponse response = restTemplate.getForObject(uri, GoogleBookResponse.class);

        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return null; // 결과 없음
        }

        GoogleBookResponse.VolumeInfo info = response.getItems().get(0).getVolumeInfo();

        return BookInfoDto.builder()
                .isbn(isbn)
                .title(info.getTitle())
                .publisher(StringUtils.hasText(info.getPublisher()) ? info.getPublisher() : "출판사 정보 없음")
                .publishedDate(parseToLocalDate(info.getPublishedDate()))
                .price(0)
                .authors(info.getAuthors() != null ? info.getAuthors() : Collections.emptyList())
                .description(info.getDescription())
                .build();
    }

    private String enhanceDescriptionWithGemini(String title, String author, String originalDescription) {

        StringBuilder prompt = new StringBuilder();
        // 해외 도서(구글 검색 결과)일 수도 있으므로 '한국어로 작성해달라'는 요청을 명시
        prompt.append("너는 전문 도서 MD야. 다음 책에 대해 독자의 구매욕구를 자극하는 상세한 서평 스타일의 소개글을 **한국어로** 작성해줘.\n");
        prompt.append("책 제목: ").append(title).append("\n");
        prompt.append("저자: ").append(author != null ? author : "미상").append("\n");

        if (StringUtils.hasText(originalDescription)) {
            prompt.append("참고할 원문 설명: ").append(originalDescription).append("\n");
        } else {
            prompt.append("정보가 부족하므로 제목과 저자를 바탕으로 내용을 추론해서 풍성하게 작성해줘.\n");
        }

        prompt.append("\n[작성 규칙]\n");
        prompt.append("1. 책 제목과 '이 책을 선택해야 하는 이유' 같은 소제목은 반드시 <h3> 태그를 사용해.\n");
        prompt.append("2. <h3> 태그 앞에는 반드시 줄바꿈을 두 번 넣어줘.\n");
        prompt.append("3. 본문 내용은 <p> 태그로 감싸고, 핵심 내용은 <ul>, <li> 리스트로 작성해.\n");
        prompt.append("4. 모든 내용은 반드시 자연스러운 한국어로 작성해.\n");
        prompt.append("5. 문단 사이에는 적절한 줄바꿈을 넣어줘.\n");

        try {
            return geminiService.generateAnswer(prompt.toString());
        } catch (Exception e) {
            log.error("Gemini 호출 실패");
            return originalDescription != null ? originalDescription.replace("\n", "<br>") : "";
        }
    }

    private LocalDate parseToLocalDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        try {
            // ISO Date Time (2023-12-25T10:00:00...)
            if (dateStr.length() >= 10) {
                return LocalDate.parse(dateStr.substring(0, 10));
            }
            // YYYY (2023) -> 2023-01-01
            if (dateStr.matches("^\\d{4}$")) {
                return LocalDate.of(Integer.parseInt(dateStr), 1, 1);
            }
            // YYYY-MM (2023-05) -> 2023-05-01
            if (dateStr.matches("^\\d{4}-\\d{2}$")) {
                return LocalDate.parse(dateStr + "-01");
            }
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: {}", dateStr);
        }
        return null; // 파싱 실패 시 null (BookService에서 기본값 처리)
    }

}
