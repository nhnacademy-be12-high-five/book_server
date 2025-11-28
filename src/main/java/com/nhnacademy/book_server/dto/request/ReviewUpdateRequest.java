package com.nhnacademy.book_server.dto.request;

import java.util.List;

public record ReviewUpdateRequest(String content,
                                  int rating,
                                  List<Long> deleteImageIds) {}
