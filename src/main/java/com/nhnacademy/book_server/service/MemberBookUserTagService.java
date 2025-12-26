package com.nhnacademy.book_server.service;

import java.util.List;

public interface MemberBookUserTagService {
    List<String> getUserTags(Long memberId, Long bookId);
    void addUserTag(Long memberId, Long bookId, String tagCode);
    void removeUserTag(Long memberId, Long bookId, String tagCode);
}
