package com.nhnacademy.book_server.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryMapper {
    private CategoryMapper() {
        throw new IllegalStateException("Utility class");
    }

    private static final Map<Integer, List<String>> CATEGORY_RULES = new HashMap<>();

    private static final Map<Integer, Integer> PARENT_MAP = new HashMap<>();

    private static final List<Integer> SEARCH_ORDER = List.of(
            10, 14, 13, 8, 9, 11, 12
    );

    // 대분류 검색 순서 (1~7)
    private static final List<Integer> GENERAL_ORDER = List.of(
            1, 2, 3, 4, 5, 6, 7
    );

    static {

        // 8. 소분류: 소설/시/희곡 (가장 구체적인 것부터 매칭)
        CATEGORY_RULES.put(8, List.of(
                "소설", "에세이", "시집", "산문", "희곡", "문학",
                "이야기", "장편", "단편", "동화", "판타지", "무협", "로맨스",
                "추리", "미스터리", "스릴러", "공포", "SF"
        ));

        // [추가/보강] 9. 소분류: 경제/경영
        CATEGORY_RULES.put(9, List.of(
                "경제", "경영", "트렌드", "마케팅", "투자", "재테크", "주식", "부동산",
                "부자", "돈", "창업", "비즈니스", "리더십", "기업", "회계", "세금"
        ));

        // 10. 소분류: IT/컴퓨터
        CATEGORY_RULES.put(10, List.of(
                "프로그래밍", "개발", "자바", "java", "파이썬", "python", "c언어", "스크립트",
                "리눅스", "aws", "클라우드", "데이터베이스", "sql", "인공지능", "ai",
                "웹디자인", "ui/ux", "코딩", "알고리즘", "보안", "해킹"
        ));

        // 11. 소분류: 인문/사회/역사 (여행, 요리 포함)
        CATEGORY_RULES.put(11, List.of(
                "여행", "에세이", "심리", "마음", "요리", "레시피", "맛집",
                "역사", "세계사", "한국사", "철학", "인문", "교양", "정치", "사회", "종교"
        ));

        // 12. 소분류: 유아/아동/만화
        CATEGORY_RULES.put(12, List.of(
                "그림책", "동화", "만화", "코믹", "웹툰", "애니메이션",
                "학습만화", "어린이", "유아", "초등"
        ));

        // 13. 소분류: 외국어/수험서
        CATEGORY_RULES.put(13, List.of(
                "토익", "토플", "오픽", "영어", "중국어", "일본어", "회화", "문법", "단어",
                "수험", "자격증", "기사", "공무원", "한국사능력", "ncs", "면접"
        ));

        // 14. 소분류: 과학/공학
        CATEGORY_RULES.put(14, List.of(
                "공학", "수학", "물리", "화학", "생물", "지구", "우주", "천문",
                "기계", "전기", "전자", "건축", "토목", "환경"
        ));

        // ---------------------------------------------------------
        // 대분류 (소분류에 안 걸린 것들을 넓은 그물로 잡음)
        // ---------------------------------------------------------

        // 1. 소설/문학
        CATEGORY_RULES.put(1, List.of("소설", "문학", "작품", "이야기", "픽션"));

        // 2. 경제/경영
        CATEGORY_RULES.put(2, List.of("기업", "혁신", "성공", "관리", "매니지먼트"));

        // 3. IT
        CATEGORY_RULES.put(3, List.of("IT", "컴퓨터", "소프트웨어", "하드웨어", "모바일", "앱", "인터넷"));

        // 4. 인문/사회
        CATEGORY_RULES.put(4, List.of("사회", "문화", "생활", "예술", "미술", "음악"));

        // 5. 유아/아동
        CATEGORY_RULES.put(5, List.of("육아", "교육", "놀이", "태교"));

        // 6. 수험서
        CATEGORY_RULES.put(6, List.of("문제집", "참고서", "입문", "실기", "필기"));

        // 7. 자연/과학
        CATEGORY_RULES.put(7, List.of("자연", "과학", "동물", "식물", "곤충", "공룡"));

        PARENT_MAP.put(8, 1);  // 소설/시 -> 소설/문학
        PARENT_MAP.put(9, 2);  // 경제/경영 -> 경제/경영
        PARENT_MAP.put(10, 3); // IT/컴퓨터 -> IT
        PARENT_MAP.put(11, 4); // 인문/사회 -> 인문/사회
        PARENT_MAP.put(12, 5); // 유아/만화 -> 유아/아동
        PARENT_MAP.put(13, 6); // 외국어 -> 수험서
        PARENT_MAP.put(14, 7); // 과학/공학 -> 자연/과학
    }

    public static int getParentId(int categoryId) {
        if (categoryId >= 1 && categoryId <= 7)
            return 0; // 대분류

        return PARENT_MAP.getOrDefault(categoryId, 1);
    }

    public static Integer findCategoryId(String title) {
        // 1. 방어 로직: 제목이 없으면 null 반환
        if (title == null || title.isEmpty()) {
            return null;
        }

        // 2. 매칭 확률을 높이기 위해 소문자로 변환
        String searchTitle = title.toLowerCase();

        // 3. 구체적인 소분류 우선 검색
        Integer categoryId = findMatchingCategory(searchTitle, SEARCH_ORDER);
        if (categoryId != null) {
            return categoryId;
        }

        // 4. 대분류 검색 (1~7)
        return findMatchingCategory(searchTitle, GENERAL_ORDER);
    }

    private static Integer findMatchingCategory(String searchTitle, List<Integer> categoryIds) {
        for (Integer id : categoryIds) {
            if (hasMatchingKeyword(searchTitle, id)) {
                return id;
            }
        }
        return null;
    }

    private static boolean hasMatchingKeyword(String searchTitle, Integer id) {
        List<String> keywords = CATEGORY_RULES.get(id);
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (searchTitle.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}