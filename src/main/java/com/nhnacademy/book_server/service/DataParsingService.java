package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.AuthorRepository;
import com.nhnacademy.book_server.repository.BookAuthorRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataParsingService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final EntityManager entityManager;

    // 한 번에 처리할 배치 사이즈 (DB 파라미터 제한 회피용)
    private static final int BATCH_SIZE = 1000;

    @Transactional
    public void saveAll(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        log.info("총 {}건의 데이터 파싱 완료. 중복 확인 및 저장 시작...", records.size());

        Set<String> allPublisherNames = new HashSet<>();
        Set<String> allAuthorNames = new HashSet<>();
        Set<String> allIsbns = new HashSet<>();

        // 1. 데이터 수집
        for (ParsingDto dto : records) {
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNames.add(dto.getPublisher().trim());
            }
            if (StringUtils.hasText(dto.getAuthor())) {
                String[] splitAuthors = dto.getAuthor().split("[,;]");
                for (String authorName : splitAuthors) {
                    if (StringUtils.hasText(authorName)) {
                        allAuthorNames.add(authorName.trim());
                    }
                }
            }
            if (StringUtils.hasText(dto.getIsbn())) {
                allIsbns.add(dto.getIsbn().trim());
            }
        }

        // 2. 출판사 처리 (배치 조회)
        Map<String, Publisher> publisherMap = resolvePublishers(allPublisherNames);

        // 3. 작가 처리 (배치 조회)
        Map<String, Author> authorMap = resolveAuthors(allAuthorNames);

        // 4. 이미 존재하는 ISBN 확인 (배치 조회)
        Set<String> existingIsbnSet = new HashSet<>();
        List<String> isbnList = new ArrayList<>(allIsbns);

        for (int i = 0; i < isbnList.size(); i += BATCH_SIZE) {
            List<String> batch = isbnList.subList(i, Math.min(isbnList.size(), i + BATCH_SIZE));
            bookRepository.findAllByIsbn13In(new HashSet<>(batch))
                    .forEach(book -> existingIsbnSet.add(book.getIsbn13()));
        }

        // 5. Book 객체 생성
        List<Book> newBooks = new ArrayList<>();
        List<ParsingDto> booksToSaveDtos = new ArrayList<>();

        for (ParsingDto dto : records) {
            String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";

            if (isbn.isEmpty() || existingIsbnSet.contains(isbn)) {
                continue;
            }
            existingIsbnSet.add(isbn); // CSV 내부 중복 방지

            String pubName = dto.getPublisher() != null ? dto.getPublisher().trim() : "";
            Publisher publisher = publisherMap.get(pubName);

            // [중요] 맵에서 못 찾았을 경우 경고 로그 출력!!
            if (publisher == null) {
                if (!pubName.isEmpty()) {
                    // 이름은 있는데 맵에 없다? -> 로직 문제
                    log.error("🚨 비상: 출판사 매핑 실패! 이름: [{}]", pubName);
                } else {
                    // 이름 자체가 비어있다? -> CSV 파서 문제 (인덱스 확인 필요)
                    log.warn("⚠️ 경고: 출판사 데이터가 비어있음. ISBN: {}", isbn);
                }
            }

//            Publisher publisher = publisherMap.get(dto.getPublisher().trim());

            Book book = Book.builder()
                    .isbn13(isbn)
                    .title(dto.getTitle())
                    .publisher(publisher)
                    .price(parsePrice(dto.getPrice()))
                    .content(dto.getDescription())
                    .image(dto.getImageUrl())
                    .publishedDate(parseDate(dto.getPubDate()).toString())
                    .build();

            newBooks.add(book);
            booksToSaveDtos.add(dto);
        }

        // 6. Book 일괄 저장 (배치 저장)
        if (!newBooks.isEmpty()) {
            saveBooksInBatch(newBooks, booksToSaveDtos, authorMap);
        } else {
            log.info("저장할 새로운 도서가 없습니다.");
        }
    }

    // 도서와 작가 관계를 배치로 나누어 저장하는 메서드
    private void saveBooksInBatch(List<Book> books, List<ParsingDto> dtos, Map<String, Author> authorMap) {
        int total = books.size();
        log.info("새로운 도서 {}권 저장을 시작합니다.", total);

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(total, i + BATCH_SIZE);
            List<Book> bookBatch = books.subList(i, end);
            List<ParsingDto> dtoBatch = dtos.subList(i, end);

            // 1) 책 저장
            bookRepository.saveAll(bookBatch);

            // 2) 책-작가 관계 생성
            List<BookAuthor> bookAuthors = new ArrayList<>();
            for (int j = 0; j < bookBatch.size(); j++) {
                Book book = bookBatch.get(j);
                ParsingDto dto = dtoBatch.get(j);

                if (StringUtils.hasText(dto.getAuthor())) {
                    String[] splitAuthors = dto.getAuthor().split("[,;]");
                    Set<Author> distinctAuthors = new HashSet<>();
                    for (String rawName : splitAuthors) {
                        String name = rawName.trim();
                        Author author = authorMap.get(name);
                        if (author != null && distinctAuthors.add(author)) {

                            Author managedAuthor = entityManager.merge(author);

                            bookAuthors.add(BookAuthor.builder()
                                    .book(book)
                                    .author(managedAuthor)
                                    .build());
                        }
                    }
                }
            }

            // 3) 관계 저장
            if (!bookAuthors.isEmpty()) {
                bookAuthorRepository.saveAll(bookAuthors);
            }

            log.info("진행률: {}/{} 권 저장 완료", end, total);
        }
        log.info("모든 데이터 저장 완료!");
    }

    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        Map<String, Publisher> map = new HashMap<>(); // 최종 반환용 (Key: 원본 이름)
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);

        // 1. DB 조회 및 중복 체크용 맵 생성 (Key: 소문자 이름)
        Map<String, Publisher> lowerCaseMap = new HashMap<>();

        // 배치 조회 (있는 것 찾기)
        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            publisherRepository.findAllByNameIn(new HashSet<>(batch))
                    .forEach(p -> lowerCaseMap.put(p.getName().toLowerCase(), p));
        }

        // 2. 없는 것만 필터링 (대소문자 중복 방지)
        List<Publisher> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Publisher newPub = Publisher.builder().name(name).build();
                toSave.add(newPub);
                lowerCaseMap.put(lowerName, newPub); // 임시 등록 (ID 없음)
            }
        }

        // 3. 배치 저장 및 안전 로직 (좀비 퇴치 기능 포함)
        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Publisher> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
                    // [시도 A] 시원하게 한 번에 저장
                    publisherRepository.saveAll(batch).forEach(p ->
                            lowerCaseMap.put(p.getName().toLowerCase(), p)
                    );
                } catch (Exception e) {
                    // [실패 시] 1. 일단 좀비 객체들(batch)을 메모리에서 쫓아냄
                    entityManager.clear();
                    log.warn("배치 저장 실패(중복 등). 개별 처리 및 메모리 정리 완료.");

                    // [시도 B] 한 땀 한 땀 개별 저장
                    for (Publisher p : batch) {
                        try {
                            // 개별 저장 시도
                            Publisher saved = publisherRepository.save(p);
                            lowerCaseMap.put(saved.getName().toLowerCase(), saved);
                        } catch (Exception ex) {

                            entityManager.clear();

                            try {
                                Publisher existing = publisherRepository.findByName(p.getName())
                                        .orElseThrow(() -> new RuntimeException("구제 불능 데이터: " + p.getName()));
                                lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                            } catch (Exception fatal) {
                                log.error("🚨 처리 불가 출판사: {}", p.getName());
                            }
                        }
                    }
                }
            }
        }

        // 4. 최종 결과 맵 생성 (Key: 원본 CSV에 있던 이름)
        for (String name : names) {
            Publisher p = lowerCaseMap.get(name.toLowerCase());
            if (p != null) {
                map.put(name, p);
            }
        }

        return map;
    }

    // [수정된 작가 처리 메서드] - 출판사 처리와 똑같이 '안전 장치' 추가
    private Map<String, Author> resolveAuthors(Set<String> names) {
        Map<String, Author> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);

        // 1. DB 조회 (중복 체크용)
        Map<String, Author> lowerCaseMap = new HashMap<>();
        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            authorRepository.findAllByNameIn(new HashSet<>(batch))
                    .forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));
        }

        // 2. 저장할 대상 필터링
        List<Author> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Author newAuthor = Author.builder().name(name).build();
                toSave.add(newAuthor);
                lowerCaseMap.put(lowerName, newAuthor);
            }
        }

        // 3. 안전 저장 로직 (출판사 처리와 동일하게 적용)
        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Author> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
                    // [시도 A] 한 번에 저장
                    authorRepository.saveAll(batch).forEach(a ->
                            lowerCaseMap.put(a.getName().toLowerCase(), a)
                    );
                } catch (Exception e) {
                    // [실패 시] 1. 좀비 객체(영속성 컨텍스트) 정리 -> 이게 핵심!
                    entityManager.clear();

                    log.warn("작가 배치 저장 실패. 개별 처리로 전환합니다.");

                    // [시도 B] 한 땀 한 땀 개별 저장
                    for (Author a : batch) {
                        try {
                            Author saved = authorRepository.save(a);
                            lowerCaseMap.put(saved.getName().toLowerCase(), saved);
                        } catch (Exception ex) {
                            // 개별 실패 시에도 detach 필수
                            entityManager.clear();

                            // [최후의 수단] DB에서 조회
                            try {
                                Author existing = authorRepository.findByName(a.getName())
                                        .orElseThrow(() -> new RuntimeException("작가 구제 불능: " + a.getName()));
                                lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                            } catch (Exception fatal) {
                                log.error("🚨 작가 처리 완전 실패: {}", a.getName());
                            }
                        }
                    }
                }
            }
        }

        // 4. 결과 매핑
        for (String name : names) {
            Author a = lowerCaseMap.get(name.toLowerCase());
            if (a != null) {
                map.put(name, a);
            }
        }
        return map;
    }

    private int parsePrice(String priceStr) {
        if (!StringUtils.hasText(priceStr)) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return LocalDate.now();
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}