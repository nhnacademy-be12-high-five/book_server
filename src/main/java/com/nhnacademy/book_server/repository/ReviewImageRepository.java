package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
}
