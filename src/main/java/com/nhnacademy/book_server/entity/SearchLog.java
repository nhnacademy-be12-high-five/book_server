package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name= "s4.java21.net")
public class SearchLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //pk

    @Column
    private String keyword; //검색어

    @Column(nullable = false)
    private long searchCount; //누적 검색 횟수


    public void increaseCount(){
        this.searchCount++;
    }

}
