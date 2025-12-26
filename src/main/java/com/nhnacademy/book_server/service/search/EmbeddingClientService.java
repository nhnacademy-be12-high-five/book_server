package com.nhnacademy.book_server.service.search;

import java.util.List;

public interface EmbeddingClientService {
    List<Float> embed(String text);
}
