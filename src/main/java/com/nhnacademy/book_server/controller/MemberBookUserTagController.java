package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.dto.UserTagRequest;
import com.nhnacademy.book_server.service.MemberBookUserTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
public class MemberBookUserTagController {

    private final MemberBookUserTagService service;

    /**
     * 회원이 특정 책에 대해 선택한 태그 목록 조회
     * Header: X-MEMBER-ID
     */
    @GetMapping("/{bookId}/user-tags")
    public ResponseEntity<List<String>> getUserTags(
            @RequestHeader("X-MEMBER-ID") Long memberId,
            @PathVariable Long bookId
    ) {
        return ResponseEntity.ok(service.getUserTags(memberId, bookId));
    }

    /**
     * 태그 선택(추가)
     * Header: X-MEMBER-ID
     * Body: { "tagCode": "TO_READ" }
     */
    @PostMapping("/{bookId}/user-tags")
    public ResponseEntity<Void> addUserTag(
            @RequestHeader("X-MEMBER-ID") Long memberId,
            @PathVariable Long bookId,
            @RequestBody UserTagRequest request
    ) {
        service.addUserTag(memberId, bookId, request.getTagCode());
        return ResponseEntity.noContent().build();
    }

    /**
     * 태그 해제(삭제)
     * Header: X-MEMBER-ID
     */
    @DeleteMapping("/{bookId}/user-tags/{tagCode}")
    public ResponseEntity<Void> removeUserTag(
            @RequestHeader("X-MEMBER-ID") Long memberId,
            @PathVariable Long bookId,
            @PathVariable String tagCode
    ) {
        service.removeUserTag(memberId, bookId, tagCode);
        return ResponseEntity.noContent().build();
    }
}
