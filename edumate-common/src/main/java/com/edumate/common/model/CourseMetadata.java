package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程元数据 —— 上传文档时关联的课程信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseMetadata {
    /** 课程名称，如 "数据结构" */
    private String courseName;
    /** 授课教师 */
    private String teacher;
    /** 学期，如 "2026-春" */
    private String semester;
    /** 章节名 */
    private String chapter;
    /** 章节层级（章=1, 节=2, 小节=3） */
    private int level;
}