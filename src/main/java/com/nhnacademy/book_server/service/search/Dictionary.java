package com.nhnacademy.book_server.service.search;
import java.util.*;
public class Dictionary {
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("아기", List.of("유아")),
            Map.entry("학생", List.of("제자")),
            Map.entry("구입", List.of("구매")),
            Map.entry("예쁜", List.of("아름다운")),
            Map.entry("슬픈", List.of("우울한")),
            Map.entry("기질", List.of("특성")),
            Map.entry("LA", List.of("로스엔젤레스"))
    );

    public static Set<String> expand(String keyword){
        Set<String> result = new HashSet<>();

        if(keyword == null || keyword.isBlank()){
            return result;
        }

        String kw = keyword.trim().toLowerCase();
        result.add(kw);

        List<String> syn = SYNONYMS.get(kw);
        if(syn!=null){
            syn.forEach(s -> result.add(s.toLowerCase()));
        }

        return result;
    }
}
