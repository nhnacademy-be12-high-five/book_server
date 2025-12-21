package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.request.BookCreateRequest;
import com.nhnacademy.book_server.dto.response.GoogleBookResponse;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookRegistrationService {
    private final RestTemplate restTemplate;
    private final GeminiTextClientService geminiService;

    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes?";

    public BookCreateRequest getBookInfoWithAi(String isbn) {
        if (isbn == null || !isbn.matches("^(\\d{10}|\\d{13})$")) {
            throw new IllegalArgumentException("유효하지 않은 ISBN 형식입니다: " + isbn);
        }
        String url = UriComponentsBuilder.fromHttpUrl(GOOGLE_BOOKS_API_URL)
                        .queryParam("q", "isbn:" + isbn)
                        .build()
                        .toUriString();
        GoogleBookResponse response = restTemplate.getForObject(url, GoogleBookResponse.class);

        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            throw new RuntimeException("Google Books에서 책을 찾을 수 없습니다. ISBN: " + isbn);
        }
        GoogleBookResponse.VolumeInfo info = response.getItems().get(0).getVolumeInfo();

        BookCreateRequest request = new BookCreateRequest();
        request.setIsbn(isbn);
        request.setTitle(info.getTitle());

        String publisher = StringUtils.hasText(info.getPublisher()) ? info.getPublisher() : "출판사 정보 없음";
        request.setPublisher(publisher);

        request.setPublishedDate(formatDate(info.getPublishedDate()));
        request.setPrice(0);

        request.setAuthors(info.getAuthors() != null ? info.getAuthors() : new ArrayList<>());

        String kyoboImageUrl = "https://contents.kyobobook.co.kr/sih/fit-in/200x0/pdt/" + isbn + ".jpg";
        request.setImage(kyoboImageUrl);
        String originalDescription = info.getDescription();
        String aiGeneratedContent = enhanceDescriptionWithGemini(info.getTitle(), request.getAuthors(), originalDescription);

        request.setDescription(aiGeneratedContent);
        return request;
    }

    private String enhanceDescriptionWithGemini(String title, List<String> authors, String originalDescription) {
        String authorStr = (authors != null && !authors.isEmpty()) ? String.join(", ", authors) : "미상";

        StringBuilder prompt = new StringBuilder();
        prompt.append("너는 전문 도서 MD야. 다음 책에 대해 독자의 구매욕구를 자극하는 상세한 서평 스타일의 소개글을 작성해줘.\n");
        prompt.append("책 제목: ").append(title).append("\n");
        prompt.append("저자: ").append(authorStr).append("\n");

        if (StringUtils.hasText(originalDescription)) {
            prompt.append("참고할 원문 설명: ").append(originalDescription).append("\n");
        }

        prompt.append("\n[작성 규칙]\n");
        prompt.append("1. **반드시 HTML 태그를 사용해서 작성해.** (예: <h3>, <p>, <ul>, <li>, <strong> 등)\n");
        prompt.append("2. <html>, <body>, ```html 같은 불필요한 태그나 마크다운 기호는 넣지 마. 내용(`<div>` 내부)만 줘.\n");
        prompt.append("3. 소제목은 <h3>를 사용하고, 강조할 부분은 <strong>을 사용해.\n");
        prompt.append("4. 핵심 포인트는 <ul>과 <li>를 사용해서 리스트로 정리해줘.\n");
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
