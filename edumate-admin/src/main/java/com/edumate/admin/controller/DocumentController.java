package com.edumate.admin.controller;

import com.edumate.common.model.DocumentChunk;
import com.edumate.common.model.KnowledgePoint;
import com.edumate.admin.service.KnowledgeExtractionService;
import com.edumate.core.graph.KnowledgeGraphService;
import com.edumate.core.parser.DocumentParserService;
import com.edumate.core.parser.HierarchicalChunkerService;
import com.edumate.core.retrieval.KeywordIndexService;
import com.edumate.core.retrieval.VectorStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器 —— 上传、解析、切分课程资料
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final List<String> ALLOWED_EXTENSIONS =
            List.of("pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "txt", "md");

    private final DocumentParserService parserService;
    private final HierarchicalChunkerService chunkerService;
    private final VectorStoreService vectorStoreService;
    private final KeywordIndexService keywordIndexService;
    private final KnowledgeExtractionService extractionService;
    private final KnowledgeGraphService knowledgeGraphService;

    public DocumentController(DocumentParserService parserService,
                              HierarchicalChunkerService chunkerService,
                              VectorStoreService vectorStoreService,
                              KeywordIndexService keywordIndexService,
                              KnowledgeExtractionService extractionService,
                              KnowledgeGraphService knowledgeGraphService) {
        this.parserService = parserService;
        this.chunkerService = chunkerService;
        this.vectorStoreService = vectorStoreService;
        this.keywordIndexService = keywordIndexService;
        this.extractionService = extractionService;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("courseName") String courseName,
            @RequestParam(value = "semester", required = false, defaultValue = "") String semester) throws IOException {

        String fileName = file.getOriginalFilename();
        if (fileName == null || !isAllowed(fileName)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "不支持的文件格式，仅支持: " + ALLOWED_EXTENSIONS));
        }

        // 保存临时文件
        Path tempFile = Files.createTempFile("edumate-", "-" + fileName);
        file.transferTo(tempFile.toFile());

        try {
            // 1. 解析文档
            String content = parserService.parse(tempFile);

            // 2. 层级切分
            List<DocumentChunk> chunks = chunkerService.chunk(content, courseName, semester);

            // 3. 索引到向量库和关键词索引
            vectorStoreService.indexChunks(chunks);
            keywordIndexService.indexChunks(chunks);

            // 4. 知识抽取并存入图谱
            List<KnowledgePoint> points = extractionService.extract(chunks);
            knowledgeGraphService.savePoints(points);

            return ResponseEntity.ok(Map.of(
                    "fileName", fileName,
                    "courseName", courseName,
                    "semester", semester,
                    "chunkCount", chunks.size(),
                    "chunks", chunks
            ));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private boolean isAllowed(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }
}