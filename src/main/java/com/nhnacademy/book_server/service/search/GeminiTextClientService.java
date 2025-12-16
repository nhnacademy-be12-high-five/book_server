package com.nhnacademy.book_server.service.search;

import java.util.List;

public interface GeminiTextClientService {
    String generateAnswer(String prompt);
    String getReviewSummary(String bookTitle, List<String> reviews);
}
