package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档分块 —— RAG 检索的最小单元
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    /** 唯一标识 */
    private String id;
    /** 分块文本内容 */
    private String content;
    /** 所属课程名称 */
    private String courseName;
    /** 章节路径，如 "第3章 > 3.2节 > 红黑树" */
    private String chapterPath;
    /** 父块ID（层级感知切分时关联父块） */
    private String parentChunkId;
    /** 在原始文档中的页码 */
    private int pageNumber;
    /** 扩展元数据 */
    private Map<String, String> metadata;
}