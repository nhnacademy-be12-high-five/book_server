package com.nhnacademy.book_server.entity;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.BatchSize;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Entity
@Table(name = "book")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "도서 정보 모델입니다.")
@Getter
@Setter
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private String isbn13;
    // int로 하면 범위를 초과하므로 String으로 변경
    // ISBN_THIRTEEN_NO : ISBN 번호

    // VLM_NM : 권(卷) 이름/번호
    // -> 시리즈물일경우 (예: 1권, Vol. 2)
    private String volumeNumber;

    @NotNull
    @Column(nullable = false, length = 1000)
    private String title;
    // TITLE_NM : 제목

    @NotNull
    @Builder.Default
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookAuthor> bookAuthors = new ArrayList<>(); // List 초기화는 @Builder에서 처리됨
    // AUTHR_NM : 저자이름
    // 도서와 저자는 1:N 관계 -> 한권의 책에 여러 저자가 있을 수 있음

    // 출판사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;
    // 도서와 출판사는 N:1관계

    // 초판 발행일
    // PBLICTE_DE : 초판 발행일
    private LocalDate dateTime;

    // ADTION_SMBL_NM : 판차 기호명
    private String edition;

    // PRC_VALUE : 가격
    @NotNull
    @Column(nullable = false)
    private Integer price;

    // 이미지 URL : IMAGE_URL
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String image;

    //     BOOK_INTRCN_CN : 도서 소개 내용
    @NotNull
    @Column(columnDefinition = "TEXT")
    private String content;

    // KDC_NM : KDC 분류명
    private Integer kdcCode;

    // TITLE_SBST_NM : 부제
    private String subjectName;

    // AUTHR_SBST_NM : 역자/역할 이름
    // 저자 외의 인물(예: 역자, 감수자) 및 그 역할에 대한 정보입니다.
    private String contributorName;

    // TWO_PBLICTE_DE : 최종 발행일
    @NotNull
    private String publishedDate;

    // 서점 재고 여부
    private Boolean stockCheckedAt;

    // ISBN_NO : 표준 도서 번호 10자리
//    private String isbnNO;

    //    private String tag;
//    private String bookLike;
    private Boolean isPortalSiteBookExist;

    @ManyToOne
    @JoinColumn(name = "category_category_id")
    private Category category;
    //  도서와 카테고리는 1:N관계

    public void updateBookInfo(String title, Publisher publisher, int price, String content, String image, String publishedDate) {
        this.title = title;
        this.publisher = publisher; // 변경된 출판사(영속 상태) 반영
        this.price = price;
        this.content = content;
        this.image = image;
        this.publishedDate = publishedDate;
    }

}