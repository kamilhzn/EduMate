package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {
    private String id;
    private String name;
    private String courseName;
    private String source;
    private int count;
    private String difficulty;
    private List<QuizQuestion> questions;
    @Builder.Default
    private Instant createdAt = Instant.now();
}