package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.TagSwagger;
import com.nhnacademy.book_server.dto.request.TagRequest;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.service.TagService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "태그", description = "태그 API 입니다.")
@RequiredArgsConstructor
@Slf4j
public class TagController implements TagSwagger {

    private final TagService tagService; // 서비스 계층 가정

    @Override
    @PostMapping("/tag")
    public ResponseEntity<TagResponse> createTag(@RequestBody TagRequest tagRequest) {
        log.info("태그 생성 : {}",tagRequest);
        TagResponse response = tagService.createTag(tagRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 태그 하나 조회
    @GetMapping("/tag/{tagId}")
    public ResponseEntity<TagResponse> getTag(@PathVariable("tagId") Long id){
        TagResponse response=tagService.getTag(id);
        return ResponseEntity.ok(response);
    }

    // 태그 다량 조회
    @Override
    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getTags() {
        List<TagResponse> responses = tagService.getAllTags();
        return ResponseEntity.ok(responses);
    }
}
