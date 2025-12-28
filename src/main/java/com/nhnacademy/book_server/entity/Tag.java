package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Tag")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tagId;
    private String name;
}
