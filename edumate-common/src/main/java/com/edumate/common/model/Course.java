package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    private String id;
    private String name;
    private int coverColorIndex;
    private int chapterCount;
    @Builder.Default
    private Instant createdAt = Instant.now();
}