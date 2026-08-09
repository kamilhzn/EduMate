package com.edumate.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 流水线追踪记录 —— 记录单次查询经过各阶段的完整信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceRecord {
    /** 追踪 ID */
    private String traceId;
    /** 原始查询 */
    private String query;
    /** 会话 ID */
    private String sessionId;
    /** 时间戳 */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** ── 各阶段记录 ── */
    /** Query 改写 */
    private StageRecord queryRewrite;
    /** 意图识别 */
    private StageRecord intentClassification;
    /** 拒答判断 */
    private StageRecord refusalCheck;
    /** 向量检索 */
    private StageRecord vectorRetrieval;
    /** 关键词检索 */
    private StageRecord keywordRetrieval;
    /** 图谱检索 */
    private StageRecord graphRetrieval;
    /** RRF 融合 */
    private StageRecord rrfFusion;
    /** LLM 生成 */
    private StageRecord llmGeneration;

    /** 总耗时（毫秒） */
    private long totalLatencyMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageRecord {
        /** 阶段名称 */
        private String stage;
        /** 耗时（毫秒） */
        private long latencyMs;
        /** 输入数据摘要 */
        private String input;
        /** 输出数据摘要 */
        private String output;
        /** 是否成功 */
        private boolean success;
        /** 错误信息 */
        private String error;
        /** 扩展元数据（如检索结果数、得分等） */
        private Map<String, Object> metadata;
    }
}