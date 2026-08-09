package com.edumate.admin.controller;

import com.edumate.common.model.EvaluationResult;
import com.edumate.common.model.EvaluationSample;
import com.edumate.common.model.TraceRecord;
import com.edumate.core.evaluation.EvaluationOrchestrator;
import com.edumate.core.evaluation.TraceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 评测控制器 —— 检索评测、RAGAS 评测、Trace 查询
 */
@RestController
@RequestMapping("/api/eval")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvaluationOrchestrator evaluationOrchestrator;
    private final TraceService traceService;
    private final ObjectMapper objectMapper;

    public EvaluationController(EvaluationOrchestrator evaluationOrchestrator,
                                TraceService traceService,
                                ObjectMapper objectMapper) {
        this.evaluationOrchestrator = evaluationOrchestrator;
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行检索评测
     * <p>
     * 默认加载内置 50 条评测数据集，也可通过请求体传入自定义样本。
     *
     * @param request 可选的自定义评测样本列表
     * @return 评测结果（含 Recall@K、MRR、逐样本明细）
     */
    @PostMapping("/retrieval")
    public ResponseEntity<?> runRetrievalEval(@RequestBody(required = false) EvalRequest request) {
        try {
            List<EvaluationSample> samples;
            if (request != null && request.samples() != null && !request.samples().isEmpty()) {
                samples = request.samples();
            } else {
                samples = loadBuiltInDataset();
            }

            if (samples.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "评测数据集为空，请先上传文档并标注相关分块"));
            }

            EvaluationResult result = evaluationOrchestrator.runRetrievalEvaluation(samples);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("检索评测失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "评测失败: " + e.getMessage()));
        }
    }

    /**
     * 查询 Trace 记录
     *
     * @param traceId 追踪 ID
     * @return TraceRecord 或 404
     */
    @GetMapping("/trace/{traceId}")
    public ResponseEntity<?> getTrace(@PathVariable String traceId) {
        TraceRecord trace = traceService.getTrace(traceId);
        if (trace == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trace);
    }

    /**
     * 查询最近 N 条 Trace 记录
     *
     * @param count 返回数量（默认 10，最大 50）
     * @return Trace 记录列表
     */
    @GetMapping("/traces")
    public ResponseEntity<?> getRecentTraces(@RequestParam(defaultValue = "10") int count) {
        int limit = Math.min(count, 50);
        List<TraceRecord> traces = traceService.getRecentTraces(limit);
        return ResponseEntity.ok(Map.of("count", traces.size(), "traces", traces));
    }

    /**
     * 加载内置评测数据集（从 classpath 的 eval/eval-dataset.json）
     */
    private List<EvaluationSample> loadBuiltInDataset() {
        try {
            ClassPathResource resource = new ClassPathResource("eval/eval-dataset.json");
            try (InputStream is = resource.getInputStream()) {
                List<EvaluationSample> samples = objectMapper.readValue(is,
                        new TypeReference<List<EvaluationSample>>() {});
                log.info("加载内置评测数据集: {} 条样本", samples.size());
                return samples;
            }
        } catch (Exception e) {
            log.warn("加载内置评测数据集失败: {}", e.getMessage());
            return List.of();
        }
    }

    public record EvalRequest(List<EvaluationSample> samples) {}
}