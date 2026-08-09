package com.edumate.core.evaluation;

import com.edumate.common.model.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 全链路 Trace 追踪服务 —— 记录单次查询经过各阶段的详细信息
 * <p>
 * 使用内存存储（ConcurrentHashMap），不依赖外部数据库。
 * 最多保留最近 200 条 Trace 记录，超过自动淘汰。
 * <p>
 * 追踪阶段：queryRewrite → intentClassification → refusalCheck →
 * vectorRetrieval → keywordRetrieval → graphRetrieval → rrfFusion → llmGeneration
 */
@Service
public class TraceService {

    private static final Logger log = LoggerFactory.getLogger(TraceService.class);

    private static final int MAX_TRACES = 200;

    private final Map<String, TraceRecord> traces = new ConcurrentHashMap<>();
    private final Deque<String> recentTraceIds = new ConcurrentLinkedDeque<>();

    /**
     * 开始一次新的 Trace 追踪
     *
     * @param query     原始查询
     * @param sessionId 会话 ID
     * @return 初始化后的 TraceRecord
     */
    public TraceRecord startTrace(String query, String sessionId) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        TraceRecord trace = TraceRecord.builder()
                .traceId(traceId)
                .query(query)
                .sessionId(sessionId)
                .timestamp(Instant.now())
                .build();
        traces.put(traceId, trace);
        recentTraceIds.addLast(traceId);

        // 淘汰旧记录
        while (recentTraceIds.size() > MAX_TRACES) {
            String oldest = recentTraceIds.pollFirst();
            if (oldest != null) traces.remove(oldest);
        }

        log.debug("Trace 开始: traceId={}, query='{}'", traceId, query);
        return trace;
    }

    /**
     * 记录一个阶段的执行信息
     *
     * @param traceId  追踪 ID
     * @param stage    阶段名称（如 "vectorRetrieval"）
     * @param latencyMs 耗时（毫秒）
     * @param input    输入摘要
     * @param output   输出摘要
     * @param success  是否成功
     * @param error    错误信息（成功时为 null）
     */
    public void recordStage(String traceId, String stage, long latencyMs,
                            String input, String output, boolean success, String error) {
        TraceRecord trace = traces.get(traceId);
        if (trace == null) {
            log.warn("Trace 不存在: traceId={}", traceId);
            return;
        }

        TraceRecord.StageRecord record = TraceRecord.StageRecord.builder()
                .stage(stage)
                .latencyMs(latencyMs)
                .input(input)
                .output(output)
                .success(success)
                .error(error)
                .build();

        setStageField(trace, stage, record);
        log.debug("Trace 阶段记录: traceId={}, stage={}, latency={}ms, success={}",
                traceId, stage, latencyMs, success);
    }

    /**
     * 完成 Trace 追踪，计算总耗时
     *
     * @param traceId 追踪 ID
     * @return 完整的 TraceRecord
     */
    public TraceRecord completeTrace(String traceId) {
        TraceRecord trace = traces.get(traceId);
        if (trace == null) return null;

        trace.setTotalLatencyMs(
                Duration.between(trace.getTimestamp(), Instant.now()).toMillis());
        log.info("Trace 完成: traceId={}, totalLatency={}ms", traceId, trace.getTotalLatencyMs());
        return trace;
    }

    /**
     * 获取指定 Trace 记录
     *
     * @param traceId 追踪 ID
     * @return TraceRecord 或 null
     */
    public TraceRecord getTrace(String traceId) {
        return traces.get(traceId);
    }

    /**
     * 获取最近 N 条 Trace 记录（按时间倒序）
     *
     * @param count 返回数量
     * @return Trace 记录列表
     */
    public List<TraceRecord> getRecentTraces(int count) {
        List<TraceRecord> result = new ArrayList<>();
        Iterator<String> it = recentTraceIds.descendingIterator();
        while (it.hasNext() && result.size() < count) {
            TraceRecord trace = traces.get(it.next());
            if (trace != null) {
                result.add(trace);
            }
        }
        return result;
    }

    /**
     * 将阶段记录写入 TraceRecord 的对应字段
     */
    private void setStageField(TraceRecord trace, String stage, TraceRecord.StageRecord record) {
        switch (stage) {
            case "queryRewrite" -> trace.setQueryRewrite(record);
            case "intentClassification" -> trace.setIntentClassification(record);
            case "refusalCheck" -> trace.setRefusalCheck(record);
            case "vectorRetrieval" -> trace.setVectorRetrieval(record);
            case "keywordRetrieval" -> trace.setKeywordRetrieval(record);
            case "graphRetrieval" -> trace.setGraphRetrieval(record);
            case "rrfFusion" -> trace.setRrfFusion(record);
            case "llmGeneration" -> trace.setLlmGeneration(record);
            default -> log.warn("未知阶段: {}", stage);
        }
    }
}