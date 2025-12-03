package com.nhnacademy.book_server.service.search;

import java.util.List;

public interface EmbeddingClientService {
    //단일 텍스트 -> 벡터
        List<Float> embed(String text);
    //여러 텍스트 -> 여러 벡터
        List<List<Float>> embedAll(List<String> texts);
}
