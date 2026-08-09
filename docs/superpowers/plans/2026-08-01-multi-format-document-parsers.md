# 多格式文档解析器实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 DocumentParserService 添加 DOC、PPT、XLS、XLSX 四种格式的文档解析能力，使前端所有允许上传的格式都能被正确解析为纯文本。

**Architecture:** 扩展现有的 `DocumentType` 枚举添加 4 个新值，在 `DocumentParserService` 中新增 4 个解析方法。DOC/PPT/XLS 使用 Apache POI scratchpad（HWPFDocument/HSLFSlideShow/HSSFWorkbook），XLSX 使用 poi-ooxml（XSSFWorkbook）。所有依赖（poi-ooxml 5.4.0、poi-scratchpad 5.4.0）已在 pom.xml 中声明，无需新增依赖。

**Tech Stack:** Java 21, Spring Boot 3.4.5, Apache POI 5.4.0, JUnit 5, AssertJ

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `edumate-common/src/main/java/com/edumate/common/enums/DocumentType.java` | 修改 | 添加 DOC, PPT, XLS, XLSX 枚举值 |
| `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java` | 修改 | 添加 4 个解析方法 + switch 分支 |
| `edumate-core/src/test/java/com/edumate/core/parser/DocumentParserServiceTest.java` | 新建 | 单元测试覆盖所有格式 |

---

### Task 1: 扩展 DocumentType 枚举

**Files:**
- Modify: `edumate-common/src/main/java/com/edumate/common/enums/DocumentType.java`

- [ ] **Step 1: 添加 4 个新枚举值**

将 `DocumentType.java` 完整替换为：

```java
package com.edumate.common.enums;

/**
 * 支持的文档类型
 */
public enum DocumentType {
    PDF("pdf"),
    PPTX("pptx"),
    PPT("ppt"),
    DOCX("docx"),
    DOC("doc"),
    XLSX("xlsx"),
    XLS("xls"),
    TXT("txt"),
    MARKDOWN("md");

    private final String extension;

    DocumentType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    /** 根据文件扩展名推断文档类型 */
    public static DocumentType fromExtension(String extension) {
        for (DocumentType type : values()) {
            if (type.extension.equalsIgnoreCase(extension)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的文档格式: ." + extension);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvnw compile -pl edumate-common -q`
Expected: BUILD SUCCESS, 无错误

---

### Task 2: 在 DocumentParserService 添加 switch 分支

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java:27-33`

- [ ] **Step 1: 添加 4 个新的 switch case**

将 `parse` 方法中的 switch 语句从：

```java
        return switch (type) {
            case TXT, MARKDOWN -> Files.readString(filePath);
            case PDF -> parsePdf(filePath);
            case DOCX -> parseDocx(filePath);
            case PPTX -> parsePptx(filePath);
        };
```

替换为：

```java
        return switch (type) {
            case TXT, MARKDOWN -> Files.readString(filePath);
            case PDF -> parsePdf(filePath);
            case DOCX -> parseDocx(filePath);
            case DOC -> parseDoc(filePath);
            case PPTX -> parsePptx(filePath);
            case PPT -> parsePpt(filePath);
            case XLSX -> parseXlsx(filePath);
            case XLS -> parseXls(filePath);
        };
```

- [ ] **Step 2: 编译验证（预期失败 — 方法未定义）**

Run: `mvnw compile -pl edumate-core -q`
Expected: COMPILATION ERROR — 找不到方法 parseDoc, parsePpt, parseXlsx, parseXls

---

### Task 3: 实现 DOC 解析方法

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java` (在 parsePptx 方法之后)

- [ ] **Step 1: 添加 parseDoc 方法**

在 `parsePptx` 方法之后、`getExtension` 方法之前添加：

```java
    private String parseDoc(Path filePath) throws IOException {
        try (org.apache.poi.hwpf.HWPFDocument document =
                     new org.apache.poi.hwpf.HWPFDocument(Files.newInputStream(filePath))) {
            org.apache.poi.hwpf.extractor.WordExtractor extractor =
                    new org.apache.poi.hwpf.extractor.WordExtractor(document);
            return extractor.getText();
        }
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvnw compile -pl edumate-core -q`
Expected: BUILD SUCCESS（HWPFDocument 来自 poi-scratchpad，已在依赖中）

---

### Task 4: 实现 PPT 解析方法

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java` (在 parseDoc 方法之后)

- [ ] **Step 1: 添加 parsePpt 方法**

在 `parseDoc` 方法之后添加：

```java
    private String parsePpt(Path filePath) throws IOException {
        try (org.apache.poi.hslf.usermodel.HSLFSlideShow ppt =
                     new org.apache.poi.hslf.usermodel.HSLFSlideShow(Files.newInputStream(filePath))) {
            StringBuilder sb = new StringBuilder();
            for (org.apache.poi.hslf.usermodel.HSLFSlide slide : ppt.getSlides()) {
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape textShape) {
                        sb.append(textShape.getText()).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvnw compile -pl edumate-core -q`
Expected: BUILD SUCCESS

---

### Task 5: 实现 XLSX 解析方法

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java` (在 parsePpt 方法之后)

- [ ] **Step 1: 添加 parseXlsx 方法**

在 `parsePpt` 方法之后添加：

```java
    private String parseXlsx(Path filePath) throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(Files.newInputStream(filePath))) {
            return extractWorkbookText(workbook);
        }
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvnw compile -pl edumate-core -q`
Expected: COMPILATION ERROR — 找不到方法 extractWorkbookText

---

### Task 6: 实现 XLS 解析方法 + 公共提取逻辑

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java` (在 parseXlsx 方法之后)

- [ ] **Step 1: 添加 parseXls 方法 + extractWorkbookText 工具方法**

在 `parseXlsx` 方法之后添加：

```java
    private String parseXls(Path filePath) throws IOException {
        try (org.apache.poi.hssf.usermodel.HSSFWorkbook workbook =
                     new org.apache.poi.hssf.usermodel.HSSFWorkbook(Files.newInputStream(filePath))) {
            return extractWorkbookText(workbook);
        }
    }

    /**
     * 从 Excel Workbook 中提取纯文本（适用于 HSSF 和 XSSF）
     */
    private String extractWorkbookText(org.apache.poi.ss.usermodel.Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (sheetName != null && !sheetName.isBlank()) {
                sb.append("[").append(sheetName).append("]\n");
            }
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    String cellValue = formatter.formatCellValue(cell);
                    if (cellValue != null && !cellValue.isBlank()) {
                        sb.append(cellValue).append("\t");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvnw compile -pl edumate-core -q`
Expected: BUILD SUCCESS

---

### Task 7: 编写单元测试

**Files:**
- Create: `edumate-core/src/test/java/com/edumate/core/parser/DocumentParserServiceTest.java`

- [ ] **Step 1: 创建测试类**

```java
package com.edumate.core.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserServiceTest {

    private final DocumentParserService parserService = new DocumentParserService();

    @Test
    void shouldParseTxtFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "第一章 概述\n\n数据结构是计算机科学的基础。");

        String result = parserService.parse(testFile);

        assertThat(result).isNotNull();
        assertThat(result).contains("第一章");
        assertThat(result).contains("数据结构");
    }

    @Test
    void shouldParseMarkdownFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("notes.md");
        Files.writeString(testFile, "# 标题\n\n正文内容。");

        String result = parserService.parse(testFile);

        assertThat(result).contains("标题");
        assertThat(result).contains("正文内容");
    }

    @Test
    void shouldParseEmptyFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("empty.txt");
        Files.writeString(testFile, "");

        String result = parserService.parse(testFile);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedFormat(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("file.unknown");
        Files.writeString(testFile, "test");

        assertThatThrownBy(() -> parserService.parse(testFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文档格式");
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvnw test -pl edumate-core -Dtest=DocumentParserServiceTest -q`
Expected: 4 tests PASS

---

### Task 8: 全量编译 + 重启验证

- [ ] **Step 1: 全量编译**

Run: `mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 重启 Spring Boot 应用**

重启后用真实 .doc / .ppt / .xls / .xlsx 文件测试上传，确认后端日志出现：
```
向量入库完成: N 条分块
ES 批量索引完成: N 条分块
```

无 `IllegalArgumentException: 不支持的文档格式` 错误。

- [ ] **Step 3: 提交代码**

```bash
cd f:\JetBrains\RAG\EduMate
git add edumate-common/src/main/java/com/edumate/common/enums/DocumentType.java
git add edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java
git add edumate-core/src/test/java/com/edumate/core/parser/DocumentParserServiceTest.java
git commit -m "feat: add DOC/PPT/XLS/XLSX document parsers"
```
