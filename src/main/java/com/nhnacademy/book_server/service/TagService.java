package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.request.TagRequest;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.entity.Tag;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository repository;

    // 태그 하나 생성
    @Transactional
    public TagResponse createTag(TagRequest tagRequest) {

        if (repository.existsByName(tagRequest.name())) {
            throw new BusinessException(ErrorCode.TAG_ALREADY_EXISTS);
        }

        Tag tag = Tag.builder()
                .name(tagRequest.name())
                .build();

        Tag savedTag = repository.save(tag);

        // 4. 저장된 Entity -> Response DTO로 변환하여 반환
        return new TagResponse(savedTag.getTagId(), savedTag.getName());
    }

    public TagResponse getTag(Long id) {
        Tag tag = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("태그가 존재하지 않습니다."));

        // Tag 엔티티의 데이터를 꺼내서 TagResponse DTO 생성자에 넣어줍니다.
        return new TagResponse(tag.getTagId(),tag.getName());
    }

    public List<TagResponse> getAllTags() {

        List<Tag> tags=repository.findAll();

        return tags.stream()
                .map(tag -> new TagResponse(tag.getTagId(), tag.getName())) // 하나씩 DTO로 변환
                .toList(); // 다시 리스트로 묶음
    }
}
