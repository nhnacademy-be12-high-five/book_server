package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private final RagSearchService ragSearchService;
    private final RerankerService rerankerService;
    private final GeminiTextClientService geminiTextClientService;

    public String answer(String keyword) {
        // 1. Retrieval (검색): 후보군 50개 가져오기
        //    (RagSearchService에서 BGE-M3 + KNN 검색 수행)
        SearchResult<BookResponse> searchResult = ragSearchService.searchByRag(keyword, 0, 50);
        List<BookResponse> candidates = searchResult.content();

        if (candidates.isEmpty()) {
            return "해당 키워드와 관련된 도서를 찾을 수 없어 답변을 생성하기 어렵습니다.";
        }

        // 2. Reranking (재순위화): 정확도 순으로 재정렬
        List<BookResponse> rerankedBooks = rerankerService.rerank(candidates, keyword);

        // 3. Context Selection (문맥 선택): 상위 5개만 선택
        List<BookResponse> topDocs = rerankedBooks.stream()
                .limit(5)
                .toList();

        // 4. Prompt Engineering (프롬프트 구성)
        String prompt = buildPrompt(keyword, topDocs);

        return geminiTextClientService.generateAnswer(prompt); // 메서드명이 chat 또는 generateAnswer 인지 확인 필요
    }

    private String buildPrompt(String userQuestion, List<BookResponse> books) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 도서 추천 전문가입니다. 사용자의 질문에 대해 아래 '검색된 도서 목록'을 바탕으로 친절하게 답변해주세요.\n");
        sb.append("답변은 추천 도서의 장점과 이유를 포함해야 하며, 없는 내용은 지어내지 마세요.\n\n");

        sb.append("사용자 질문: \"").append(userQuestion).append("\"\n\n");

        sb.append("검색된 도서 목록:\n");
        for (int i = 0; i < books.size(); i++) {
            BookResponse book = books.get(i);
            sb.append(i + 1).append(". 제목: ").append(book.title()).append("\n");
            sb.append("   저자: ").append(book.author()).append("\n");
            sb.append("   가격: ").append(book.price()).append("원\n");
            sb.append("   설명: ").append(truncate(book.content(), 200)).append("\n\n");
        }

        return sb.toString();
    }

    private String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() > length ? text.substring(0, length) + "..." : text;
    }

    public List<BookResponse> getRecommendations(String keyword) {
        // 1. 검색 (후보군 확보)
        SearchResult<BookResponse> searchResult = ragSearchService.searchByRag(keyword, 0, 20);
        List<BookResponse> candidates = searchResult.content();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. 재순위화 (정확도 향상)
        List<BookResponse> rerankedBooks = rerankerService.rerank(candidates, keyword);

        // 3. 상위 5개 선택
        List<BookResponse> topDocs = rerankedBooks.stream()
                .limit(5)
                .toList();

        // 4. Gemini를 통해 추천 사유 생성 (책 소개가 아닌 연관성 위주)
        String prompt = buildRecommendationPrompt(keyword, topDocs);
        String aiResponse = geminiTextClientService.generateAnswer(prompt);

        // 5. 생성된 사유를 파싱하여 BookResponse의 aiSummary 교체
        return mapReasonsToBooks(topDocs, aiResponse);
    }

    private String buildRecommendationPrompt(String userInterest, List<BookResponse> books) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 전문 도서 큐레이터입니다. 사용자가 장바구니에 담은 책들('").append(userInterest).append("')을 기반으로 아래 도서들을 추천하려고 합니다.\n");
        sb.append("각 추천 도서에 대해, **책 줄거리 요약이 아닌, 왜 이 책을 추천했는지에 대한 이유**를 1문장으로 간결하게 작성해주세요.\n");
        sb.append("사용자의 관심사(장르, 주제, 작가 등)와 추천 도서의 연결고리를 강조해야 합니다.\n");
        sb.append("응답 형식은 반드시 다음과 같이 작성하세요:\n");
        sb.append("[책ID]: 추천사유\n\n");
        sb.append("추천 도서 목록:\n");

        for (BookResponse book : books) {
            sb.append("ID: ").append(book.bookId()).append(", 제목: ").append(book.title())
                    .append(", 설명: ").append(truncate(book.content(), 100)).append("\n");
        }

        return sb.toString();
    }

    private List<BookResponse> mapReasonsToBooks(List<BookResponse> books, String aiResponse) {
        // AI 응답 파싱 (ID: 사유 형태)
        Map<Long, String> reasonMap = aiResponse.lines()
                .filter(line -> line.contains(":"))
                .map(line -> line.split(":", 2))
                .filter(parts -> isNumeric(parts[0].trim()))
                .collect(Collectors.toMap(
                        parts -> Long.parseLong(parts[0].trim()),
                        parts -> parts[1].trim(),
                        (existing, replacement) -> existing // 중복 시 기존 값 유지
                ));

        List<BookResponse> result = new ArrayList<>();
        for (BookResponse book : books) {
            String newReason = reasonMap.getOrDefault(book.bookId(), book.aiSummary()); // 생성 실패 시 기존 요약 사용

            // Record는 불변이므로 생성자를 통해 새 객체 생성 (필드가 많으므로 주의)
            result.add(new BookResponse(
                    book.bookId(), book.title(), book.author(), book.isbn(), book.price(),
                    book.image(), book.categories(), book.tags(), book.content(),
                    book.publisher(), book.publishedDate(), book.avgRating(), book.reviewCount(),
                    newReason, // [교체] AI가 생성한 추천 사유
                    book.aiReviewSummary(), book.categoryId(), book.parentId()
            ));
        }
        return result;
    }

    private boolean isNumeric(String str) {
        if (str == null) return false;
        return str.chars().allMatch(Character::isDigit);
    }
}