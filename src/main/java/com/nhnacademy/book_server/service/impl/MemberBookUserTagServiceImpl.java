package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.entity.MemberBookUserTag;
import com.nhnacademy.book_server.repository.MemberBookUserTagRepository;
import com.nhnacademy.book_server.resolver.UserTagCode;
import com.nhnacademy.book_server.service.MemberBookUserTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberBookUserTagServiceImpl implements MemberBookUserTagService {

    private final MemberBookUserTagRepository repo;

    @Override
    @Transactional(readOnly = true)
    public List<String> getUserTags(Long memberId, Long bookId) {
        return repo.findAllByMemberIdAndBookId(memberId, bookId)
                .stream()
                .map(MemberBookUserTag::getTagCode)
                .toList();
    }

    @Override
    public void addUserTag(Long memberId, Long bookId, String tagCode) {
        validate(tagCode);

        if (repo.existsByMemberIdAndBookIdAndTagCode(memberId, bookId, tagCode)) return;

        repo.save(MemberBookUserTag.builder()
                .memberId(memberId)
                .bookId(bookId)
                .tagCode(tagCode)
                .build());
    }

    @Override
    public void removeUserTag(Long memberId, Long bookId, String tagCode) {
        validate(tagCode);
        repo.deleteByMemberIdAndBookIdAndTagCode(memberId, bookId, tagCode);
    }

    private void validate(String tagCode) {
        if (tagCode == null || tagCode.isBlank()) {
            throw new IllegalArgumentException("tagCode가 비어 있습니다.");
        }
        try {
            UserTagCode.valueOf(tagCode);
        } catch (Exception e) {
            throw new IllegalArgumentException("허용되지 않은 tagCode: " + tagCode);
        }
    }
}
