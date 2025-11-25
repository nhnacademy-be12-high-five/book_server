package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Entity
@Builder
@Table(name = "publishers")
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class Publisher {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long PublisherId;

  @Column(name = "publisher_name", nullable = false, unique = true)
  private String name; // ✅ 출판사 이름
}
