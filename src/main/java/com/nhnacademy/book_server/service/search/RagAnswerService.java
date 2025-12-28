package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
}