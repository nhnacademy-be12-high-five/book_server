package com.nhnacademy.book_server.dto.event;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record ReviewImageUploadEvent(
        Long reviewId,
        List<MultipartFile> images
) {}
