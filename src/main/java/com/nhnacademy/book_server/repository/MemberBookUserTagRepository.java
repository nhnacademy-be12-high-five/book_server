package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.MemberBookUserTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberBookUserTagRepository extends JpaRepository<MemberBookUserTag, Long> {

    boolean existsByMemberIdAndBookIdAndTagCode(Long memberId, Long bookId, String tagCode);

    void deleteByMemberIdAndBookIdAndTagCode(Long memberId, Long bookId, String tagCode);

    List<MemberBookUserTag> findAllByMemberIdAndBookId(Long memberId, Long bookId);
}
