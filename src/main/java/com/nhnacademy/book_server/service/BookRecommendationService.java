package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookRecommendationService {

    private final GeminiTextClientService geminiService; // 기존 AI 서비스 재사용
    private final BookRepository bookRepository; // 책 정보 조회용

    @Transactional(readOnly = true)
    public List<BookResponse> getRecommendBooksByCart(List<String> cartBookTitles) {
        if (cartBookTitles == null || cartBookTitles.isEmpty()) {
            return Collections.emptyList();
        }
        System.out.println("들어오나요?");

        // 1. 프롬프트 작성 (AI에게 역할을 부여하고 결과 포맷을 강제함)
        String prompt = String.format(
            "나는 온라인 서점의 AI 사서야. 고객이 장바구니에 다음 책들을 담았어: [%s]. " +
            "이 책들의 장르, 저자, 스타일을 분석해서 이와 가장 관련성이 높고 함께 읽으면 좋은 책 3권을 추천해줘. " +
            "다른 미사여구 없이 오직 '책 제목' 3개만 쉼표(,)로 구분해서 답변해줘. " +
            "예시: 채식주의자, 소년이 온다, 작별하지 않는다",
            String.join(", ", cartBookTitles)
        );

        try {
            // 2. AI에게 질문 (기존 메서드 활용)
            String answer = geminiService.generateAnswer(prompt);
            
            // 3. 응답 파싱 (쉼표로 분리)
            List<String> recommendedTitles = Arrays.stream(answer.split(","))
                    .map(String::trim)
                    .filter(title -> !title.isEmpty())
                    .toList();

            // 4. DB에서 실제 책 정보 조회 (우리 서점에 있는 책만 필터링됨)
            // findByTitleIn 메서드가 없다면 Repository에 추가 필요
            List<Book> books = bookRepository.findByTitleIn(recommendedTitles);

            // 5. DTO 변환 후 반환
            return books.stream()
                    .map(BookResponse::from)
                    .limit(3) // 혹시 모르니 3개로 제한
                    .toList();

        } catch (Exception e) {
            // AI 호출 실패 시 빈 리스트 반환하여 장바구니 에러 방지
            log.error("AI Recommendation Failed", e);
            return Collections.emptyList();
        }
    }
}