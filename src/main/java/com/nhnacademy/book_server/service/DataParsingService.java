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

    private final TransactionalService transactionalService;

    private static final int BATCH_SIZE = 1000;

    @Transactional
    public void saveAll(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) return;

        log.info("총 {}건의 데이터 파싱 완료. 데이터 처리(Upsert) 시작...", records.size());

        Set<String> allPublisherNames = new HashSet<>();
        Set<String> allAuthorNames = new HashSet<>();

        // 1. 데이터 수집 (출판사, 작가 이름 모으기)
        for (ParsingDto dto : records) {
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNames.add(dto.getPublisher().trim());
            }
            if (StringUtils.hasText(dto.getAuthor())) {
                for (String authorName : dto.getAuthor().split("[,;]")) {
                    if (StringUtils.hasText(authorName)) {
                        allAuthorNames.add(authorName.trim());
                    }
                }
            }
        }

        // 2. 출판사/작가 처리 (트랜잭션 분리하여 미리 확보)
        Map<String, Publisher> publisherMap = transactionalService.executeInNewTransaction(
                () -> resolvePublishers(allPublisherNames)
        );
        Map<String, Author> authorMap = transactionalService.executeInNewTransaction(
                () -> resolveAuthors(allAuthorNames)
        );

        // 3. 책 데이터 배치 처리 (Insert + Update)
        saveBooksInBatch(records, publisherMap, authorMap);
    }

    private void saveBooksInBatch(List<ParsingDto> dtos, Map<String, Publisher> publisherMap, Map<String, Author> authorMap) {
        int total = dtos.size();

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int start = i;
            int end = Math.min(total, i + BATCH_SIZE);

            transactionalService.executeInNewTransaction(() -> {
                List<ParsingDto> batchDtos = dtos.subList(start, end);

                // 1) 현재 배치의 ISBN 목록 추출
                Set<String> batchIsbns = batchDtos.stream()
                        .map(dto -> dto.getIsbn() != null ? dto.getIsbn().trim() : "")
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());

                // 2) DB에서 이미 존재하는 책들을 조회 (Map으로 변환: ISBN -> Book)
                List<Book> existingBooks = bookRepository.findAllByIsbn13In(batchIsbns);
                Map<String, Book> bookMap = existingBooks.stream()
                        .collect(Collectors.toMap(Book::getIsbn13, book -> book));

                List<Book> booksToSave = new ArrayList<>();
                // 신규 생성된 책인지 여부를 추적하기 위한 Set (ISBN 기준)
                Set<String> newBookIsbns = new HashSet<>();

                // 3) DTO 루프: 있으면 Update, 없으면 Create
                for (ParsingDto dto : batchDtos) {
                    String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
                    if (!StringUtils.hasText(isbn)) continue;

                    Publisher publisher = publisherMap.get(dto.getPublisher() != null ? dto.getPublisher().trim() : "");
                    // [중요] 출판사 Merge (준영속 상태 -> 영속 상태로 전환)
                    Publisher managedPublisher = (publisher != null) ? entityManager.merge(publisher) : null;

                    Book book = bookMap.get(isbn);

                    if (book != null) {
                        // A. 이미 존재함 -> 정보 업데이트 (Dirty Checking)
                        book.updateBookInfo(
                                dto.getTitle(),
                                managedPublisher,
                                parsePrice(dto.getPrice()),
                                dto.getDescription(),
                                dto.getImageUrl(),
                                parseDate(dto.getPubDate()).toString()
                        );
                        // 기존 책은 booksToSave에 넣어서 saveAll 호출 (merge 효과)
                        booksToSave.add(book);
                    } else {
                        // B. 없음 -> 신규 생성
                        book = Book.builder()
                                .isbn13(isbn)
                                .title(dto.getTitle())
                                .publisher(managedPublisher)
                                .price(parsePrice(dto.getPrice()))
                                .content(dto.getDescription())
                                .image(dto.getImageUrl())
                                .publishedDate(parseDate(dto.getPubDate()).toString())
                                .build();

                        booksToSave.add(book);
                        newBookIsbns.add(isbn); // 신규 책임을 표시
                    }
                }

                // 4) 책 일괄 저장 (영속화)
                // saveAll은 신규 엔티티는 persist, 기존 엔티티는 merge 처리합니다.
                List<Book> savedBooks = bookRepository.saveAll(booksToSave);
                entityManager.flush();

                // 5) 작가 연결 (신규 책인 경우에만 수행하여 중복 방지)
                List<BookAuthor> bookAuthorsToSave = new ArrayList<>();

                // savedBooks 순서는 booksToSave 순서와 동일함
                for (int idx = 0; idx < savedBooks.size(); idx++) {
                    Book book = savedBooks.get(idx);
                    // 방금 저장된 책이 '신규'인 경우에만 작가 관계를 맺음 (기존 책은 관계 유지)
                    if (newBookIsbns.contains(book.getIsbn13())) {
                        // 원본 DTO 찾기 (ISBN으로 매칭하거나 순서 이용)
                        // 여기서는 순서가 같으므로 batchDtos가 아니라 booksToSave 생성 시점의 DTO 매칭이 필요하지만,
                        // 구조상 batchDtos를 순회하며 booksToSave를 만들었으므로 약간의 인덱스 차이가 있을 수 있음.
                        // 안전하게 ISBN으로 DTO를 다시 찾습니다. (성능상 Map 사용 추천하지만 여기선 간단히)
                        ParsingDto matchedDto = findDtoByIsbn(batchDtos, book.getIsbn13());

                        if (matchedDto != null && StringUtils.hasText(matchedDto.getAuthor())) {
                            String[] splitAuthors = matchedDto.getAuthor().split("[,;]");
                            for (String rawName : splitAuthors) {
                                Author author = authorMap.get(rawName.trim());
                                if (author != null) {
                                    Author managedAuthor = entityManager.merge(author);
                                    bookAuthorsToSave.add(BookAuthor.builder()
                                            .book(book)
                                            .author(managedAuthor)
                                            .build());
                                }
                            }
                        }
                    }
                }

                if (!bookAuthorsToSave.isEmpty()) {
                    bookAuthorRepository.saveAll(bookAuthorsToSave);
                }

                entityManager.flush();
                entityManager.clear(); // 1차 캐시 비우기
                return null;
            });

            log.info("진행률: {}/{} 권 처리 완료", end, total);
        }
        log.info("모든 데이터 처리 완료!");
    }

    // 리스트에서 ISBN으로 DTO 찾는 헬퍼 메서드
    private ParsingDto findDtoByIsbn(List<ParsingDto> dtos, String isbn) {
        for (ParsingDto dto : dtos) {
            String dtoIsbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
            if (dtoIsbn.equals(isbn)) {
                return dto;
            }
        }
        return null;
    }

    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        Map<String, Publisher> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);
        Map<String, Publisher> lowerCaseMap = new HashMap<>();

        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            publisherRepository.findAllByNameIn(new HashSet<>(batch))
                    .forEach(p -> lowerCaseMap.put(p.getName().toLowerCase(), p));
        }

        List<Publisher> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Publisher newPub = Publisher.builder().name(name).build();
                toSave.add(newPub);
                lowerCaseMap.put(lowerName, newPub);
            }
        }

        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Publisher> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
                    publisherRepository.saveAll(batch).forEach(p ->
                            lowerCaseMap.put(p.getName().toLowerCase(), p)
                    );
                } catch (Exception e) {
                    entityManager.clear();
                    // 개별 저장 로직 (생략 없이 이전 코드와 동일하게 사용하시면 됩니다)
                    // ... (이전 답변의 resolvePublishers 안전 저장 로직 참조)
                    for (Publisher p : batch) {
                        try {
                            Publisher saved = publisherRepository.save(p);
                            lowerCaseMap.put(saved.getName().toLowerCase(), saved);
                        } catch (Exception ex) {
                            entityManager.clear();
                            try {
                                Publisher existing = publisherRepository.findByName(p.getName()).orElseThrow();
                                lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                            } catch (Exception fatal) {}
                        }
                    }
                }
            }
        }

        for (String name : names) {
            Publisher p = lowerCaseMap.get(name.toLowerCase());
            if (p != null) map.put(name, p);
        }
        return map;
    }

    private Map<String, Author> resolveAuthors(Set<String> names) {
        // ... (resolvePublishers와 동일한 로직, AuthorRepository 사용) ...
        // 코드가 너무 길어져서 생략했지만, 이전 답변의 resolveAuthors 메서드를 그대로 쓰시면 됩니다.
        Map<String, Author> map = new HashMap<>();
        if (names.isEmpty()) return map;
        List<String> nameList = new ArrayList<>(names);
        Map<String, Author> lowerCaseMap = new HashMap<>();

        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            authorRepository.findAllByNameIn(new HashSet<>(batch))
                    .forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));
        }

        List<Author> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Author newAuthor = Author.builder().name(name).build();
                toSave.add(newAuthor);
                lowerCaseMap.put(lowerName, newAuthor);
            }
        }

        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Author> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
                    authorRepository.saveAll(batch).forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));
                } catch (Exception e) {
                    entityManager.clear();
                    for (Author a : batch) {
                        try {
                            Author saved = authorRepository.save(a);
                            lowerCaseMap.put(saved.getName().toLowerCase(), saved);
                        } catch (Exception ex) {
                            entityManager.clear();
                            try {
                                Author existing = authorRepository.findByName(a.getName()).orElseThrow();
                                lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                            } catch (Exception fatal) {}
                        }
                    }
                }
            }
        }

        for (String name : names) {
            Author a = lowerCaseMap.get(name.toLowerCase());
            if (a != null) map.put(name, a);
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