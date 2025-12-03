package com.nhnacademy.book_server.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AladinItemLookUpResponse {

    // 상품 조회 API
    private String subTitle;
    private String originalTitle;
    private RatingInfo ratingScore;
    private RatingInfo ratingCount;
    private RatingInfo myReviewCount;

    @Getter
    @Setter
    public static class RatingInfo {
        // ratingInfo 객체의 하위 필드들
        private Double ratingScore;         // 상품의 별 평점
        private Integer ratingCount;         // 상품에 별을 남긴 개수
        private Integer commentReviewCount;  // 100자평 남긴 개수 (누락 필드 추가)
        private Integer myReviewCount;       // 마이리뷰 남긴 개수
    }

}
