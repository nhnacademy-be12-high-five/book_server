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

    /**
     * 파싱된 데이터를 DB에 저장 (Insert + Update)
     */
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

        // 2. 출판사/작가 처리 (별도 트랜잭션으로 미리 확보하여 영속화)
        //    -> 이후 로직에서 조회 시 확실하게 DB에 존재하도록 보장
        Map<String, Publisher> publisherMap = transactionalService.executeInNewTransaction(
                () -> resolvePublishers(allPublisherNames)
        );
        Map<String, Author> authorMap = transactionalService.executeInNewTransaction(
                () -> resolveAuthors(allAuthorNames)
        );

        // 3. 책 데이터 배치 처리 (Insert + Update)
        saveBooksInBatch(records, publisherMap, authorMap);
    }

    /**
     * 도서 정보를 배치 단위로 나누어 처리 (메모리 이슈 방지 및 트랜잭션 범위 조절)
     */
    private void saveBooksInBatch(List<ParsingDto> dtos, Map<String, Publisher> publisherMap, Map<String, Author> authorMap) {
        int total = dtos.size();
        log.info("총 {}권의 도서 저장을 시작합니다.", total);

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int start = i;
            int end = Math.min(total, i + BATCH_SIZE);

            try {
                // 배치 단위로 새로운 트랜잭션 실행
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

                    // 신규 생성된 책인지 여부를 추적 (ISBN 기준) -> 신규일 때만 작가 관계 맺기 위함
                    Set<String> newBookIsbns = new HashSet<>();

                    // 3) DTO 루프: 있으면 Update, 없으면 Create
                    for (ParsingDto dto : batchDtos) {
                        String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
                        if (!StringUtils.hasText(isbn)) continue;

                        // 출판사 연결 (미리 구해둔 Map 사용)
                        Publisher publisher = publisherMap.get(dto.getPublisher() != null ? dto.getPublisher().trim() : "");

                        // [중요] Publisher가 준영속 상태일 수 있으므로 merge하여 영속 상태로 전환
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
                            // 기존 책도 명시적으로 리스트에 담음 (saveAll 호출 시 merge 됨)
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
                            newBookIsbns.add(isbn);
                        }
                    }

                    // 4) 책 일괄 저장 (영속화)
                    List<Book> savedBooks = bookRepository.saveAll(booksToSave);
                    entityManager.flush(); // ID 생성을 위해 플러시

                    // 5) 작가 연결 (BookAuthor)
                    List<BookAuthor> bookAuthorsToSave = new ArrayList<>();

                    // savedBooks 리스트를 순회하며 처리
                    for (Book book : savedBooks) {
                        // 신규 책이거나, 작가 정보 업데이트가 필요한 정책이라면 여기서 처리
                        // (현재 로직: 신규 책인 경우에만 작가 연결하여 중복 방지)
                        if (newBookIsbns.contains(book.getIsbn13())) {
                            ParsingDto matchedDto = findDtoByIsbn(batchDtos, book.getIsbn13());

                            if (matchedDto != null && StringUtils.hasText(matchedDto.getAuthor())) {
                                String[] splitAuthors = matchedDto.getAuthor().split("[,;]");
                                Set<String> distinctNames = new HashSet<>(); // 한 책 내에서 작가 중복 방지

                                for (String rawName : splitAuthors) {
                                    String name = rawName.trim();
                                    if(!StringUtils.hasText(name) || !distinctNames.add(name)) continue;

                                    Author author = authorMap.get(name);
                                    if (author != null) {
                                        // 작가 엔티티도 merge
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
                    entityManager.clear(); // 1차 캐시 비우기 (메모리 확보)
                    return null;
                });

                log.info("진행률: {}/{} 권 처리 완료", end, total);

            } catch (Exception e) {
                log.error("❌ 배치 저장 실패 (Index: {} ~ {}). 원인: {}", start, end, e.getMessage());
                // 필요 시 e.printStackTrace();
            }
        }
        log.info("모든 데이터 처리 완료!");
    }

    /**
     * 출판사 이름 목록을 받아 DB 확인 후 없으면 생성하여 Map으로 반환
     */
    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        Map<String, Publisher> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);
        Map<String, Publisher> lowerCaseMap = new HashMap<>();

        // 1. DB에서 기존 출판사 조회 (배치)
        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            transactionalService.executeInNewTransaction(() -> {
                publisherRepository.findAllByNameIn(new HashSet<>(batch))
                        .forEach(p -> lowerCaseMap.put(p.getName().toLowerCase(), p));
                return null;
            });
        }

        // 2. 없는 출판사 식별
        List<Publisher> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Publisher newPub = Publisher.builder().name(name).build();
                toSave.add(newPub);
                lowerCaseMap.put(lowerName, newPub); // 저장 전이지만 참조를 위해 맵에 추가
            }
        }

        // 3. 신규 출판사 저장
        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Publisher> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
                    // 일괄 저장 시도 (트랜잭션 분리)
                    transactionalService.executeInNewTransaction(() -> {
                        publisherRepository.saveAll(batch);
                        return null;
                    });
                } catch (Exception e) {
                    // 실패 시 개별 저장 (안전 모드)
                    for (Publisher p : batch) {
                        try {
                            transactionalService.executeInNewTransaction(() -> {
                                publisherRepository.save(p);
                                return null;
                            });
                        } catch (Exception ex) {
                            // 동시성 문제로 이미 생겼을 수 있음 -> 재조회
                            try {
                                Publisher existing = transactionalService.executeInNewTransaction(() ->
                                        publisherRepository.findByName(p.getName()).orElse(null)
                                );
                                if (existing != null) {
                                    lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                                }
                            } catch (Exception fatal) {
                                log.error("🚨 출판사 최종 실패: {}", p.getName());
                            }
                        }
                    }
                }
            }
        }

        // 4. 원본 이름 키로 Map 완성
        for (String name : names) {
            Publisher p = lowerCaseMap.get(name.toLowerCase());
            if (p != null) map.put(name, p);
        }
        return map;
    }

    /**
     * 작가 이름 목록을 받아 DB 확인 후 없으면 생성하여 Map으로 반환
     */
    private Map<String, Author> resolveAuthors(Set<String> names) {
        Map<String, Author> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);
        Map<String, Author> lowerCaseMap = new HashMap<>();

        // 1. 기존 작가 조회
        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            transactionalService.executeInNewTransaction(() -> {
                authorRepository.findAllByNameIn(new HashSet<>(batch))
                        .forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));
                return null;
            });
        }

        // 2. 없는 작가 식별
        List<Author> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Author newAuthor = Author.builder().name(name).build();
                toSave.add(newAuthor);
                lowerCaseMap.put(lowerName, newAuthor);
            }
        }

        // 3. 신규 작가 저장
        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Author> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
                    transactionalService.executeInNewTransaction(() -> {
                        authorRepository.saveAll(batch);
                        return null;
                    });
                } catch (Exception e) {
                    for (Author a : batch) {
                        try {
                            transactionalService.executeInNewTransaction(() -> {
                                authorRepository.save(a);
                                return null;
                            });
                        } catch (Exception ex) {
                            try {
                                Author existing = transactionalService.executeInNewTransaction(() ->
                                        authorRepository.findByName(a.getName()).orElse(null)
                                );
                                if (existing != null) {
                                    lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                                }
                            } catch (Exception fatal) {
                                log.error("🚨 작가 최종 실패: {}", a.getName());
                            }
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

    private ParsingDto findDtoByIsbn(List<ParsingDto> dtos, String isbn) {
        for (ParsingDto dto : dtos) {
            String dtoIsbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
            if (dtoIsbn.equals(isbn)) {
                return dto;
            }
        }
        return null;
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

    // 날짜 수정 전용 유틸리티 메서드 (필요시 사용)
    public void fixDatesOnly(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) return;

        log.info("📅 날짜 복구 작업 시작! 총 {}건", records.size());

        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end = Math.min(records.size(), i + BATCH_SIZE);
            List<ParsingDto> batchDtos = records.subList(i, end);

            transactionalService.executeInNewTransaction(() -> {
                Set<String> isbns = batchDtos.stream()
                        .map(dto -> dto.getIsbn().trim())
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());

                List<Book> books = bookRepository.findAllByIsbn13In(isbns);
                Map<String, Book> bookMap = books.stream()
                        .collect(Collectors.toMap(Book::getIsbn13, book -> book));

                List<Book> dirtyBooks = new ArrayList<>();

                for (ParsingDto dto : batchDtos) {
                    Book book = bookMap.get(dto.getIsbn().trim());
                    if (book != null) {
                        String newDateStr = parseDate(dto.getPubDate()).toString();
                        if (!newDateStr.equals(book.getPublishedDate())) {
                            book.setPublishedDate(newDateStr);
                            dirtyBooks.add(book);
                        }
                    }
                }

                if (!dirtyBooks.isEmpty()) {
                    bookRepository.saveAll(dirtyBooks);
                }
                return null;
            });
            entityManager.clear();
        }
        log.info("🎉 모든 날짜 복구 작업 완료!");
    }
}