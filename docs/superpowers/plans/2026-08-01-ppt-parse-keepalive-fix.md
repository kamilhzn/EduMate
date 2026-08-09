# PPT 解析修复 + 前端 keep-alive 与闪烁修复

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 PPT 解析失败、keep-alive 状态记忆失效、以及页面切换闪烁三个问题。

**Architecture:** 后端：`parsePpt()` 用 `POIFSFileSystem` 包装 InputStream 解决兼容性，`parsePptx()` 增加表格/组合形状递归提取。前端：交换 `keep-alive` 与 `transition` 嵌套顺序（keep-alive 在外），移除 `mode="out-in"`，删除所有子页面根元素的 `fade-in-up` 类消除动画冲突。

**Tech Stack:** Java 21, Apache POI 5.4.0, Vue 3 Composition API, Vue Router 4, Pinia

---

### Task 1: 修复 PPT 解析 — POIFSFileSystem 包装 + 表格/组合形状提取

**Files:**
- Modify: `edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java`

- [ ] **Step 1: 添加缺失的 import**

在现有 import 块中添加 PPT 表格/组合形状相关的类：

```java
import org.apache.poi.hslf.usermodel.HSLFGroupShape;
import org.apache.poi.hslf.usermodel.HSLFTable;
import org.apache.poi.hslf.usermodel.HSLFTableCell;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
```

将新的 import 按字母顺序插入到现有的 import 块中，参考位置：

```java
import org.apache.poi.hslf.usermodel.HSLFGroupShape;      // 在 HSLFShape 之后
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTable;            // 新增
import org.apache.poi.hslf.usermodel.HSLFTableCell;        // 新增
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;     // 新增
import org.apache.poi.ss.usermodel.Cell;
...
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;       // 新增
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;            // 新增
import org.apache.poi.xslf.usermodel.XSLFTableCell;        // 新增
import org.apache.poi.xslf.usermodel.XSLFTextShape;
```

- [ ] **Step 2: 替换 parsePpt 方法 — 使用 POIFSFileSystem 包装**

将 `parsePpt` 方法（第 146-161 行）替换为：

```java
    private String parsePpt(Path filePath) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(Files.newInputStream(filePath));
             HSLFSlideShow ppt = new HSLFSlideShow(fs)) {
            StringBuilder sb = new StringBuilder();
            for (HSLFSlide slide : ppt.getSlides()) {
                for (HSLFShape shape : slide.getShapes()) {
                    extractPptShapeText(shape, sb);
                }
            }
            return sb.toString();
        }
    }
```

- [ ] **Step 3: 替换 parsePptx 方法 — 增加表格/组合形状提取**

将 `parsePptx` 方法替换为：

```java
    private String parsePptx(Path filePath) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(filePath))) {
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    extractPptxShapeText(shape, sb);
                }
            }
            return sb.toString();
        }
    }
```

- [ ] **Step 4: 添加形状文本递归提取工具方法**

在 `parsePpt` 方法之后、`parseXlsx` 方法之前插入两个递归提取方法：

```java
    /**
     * 递归提取 PPT 形状中的文本（含表格和组合形状）
     */
    private void extractPptShapeText(HSLFShape shape, StringBuilder sb) {
        if (shape instanceof HSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n");
            }
        } else if (shape instanceof HSLFTable table) {
            for (HSLFTableCell cell : table.getCells()) {
                String text = cell.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\t");
                }
            }
            sb.append("\n");
        } else if (shape instanceof HSLFGroupShape group) {
            for (HSLFShape child : group.getShapes()) {
                extractPptShapeText(child, sb);
            }
        }
    }

    /**
     * 递归提取 PPTX 形状中的文本（含表格和组合形状）
     */
    private void extractPptxShapeText(XSLFShape shape, StringBuilder sb) {
        if (shape instanceof XSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n");
            }
        } else if (shape instanceof XSLFTable table) {
            for (XSLFTableCell cell : table.getCells()) {
                String text = cell.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\t");
                }
            }
            sb.append("\n");
        } else if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                extractPptxShapeText(child, sb);
            }
        }
    }
```

- [ ] **Step 5: 编译验证**

Run: `mvnw compile -pl edumate-common,edumate-core -q`
Expected: BUILD SUCCESS

---

### Task 2: 修复 keep-alive 状态记忆

**Files:**
- Modify: `edumate-frontend/src/components/layout/AppLayout.vue`

- [ ] **Step 1: 交换 keep-alive 和 transition 嵌套顺序**

将 `AppLayout.vue` 模板部分（第 7-13 行）替换为：

```vue
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
```

即：移除整个 `<transition name="page" mode="out-in">` 包裹层，只保留 `<keep-alive>` 在外层。

- [ ] **Step 2: 移除不再使用的 transition CSS**

删除 `AppLayout.vue` 中 `<style scoped>` 内的 transition 相关样式（第 63-76 行），即删除以下 4 个 CSS 规则：

```css
.page-enter-active,
.page-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
```

---

### Task 3: 修复界面闪烁 — 移除子页面 fade-in-up 动画

**Files:**
- Modify: `edumate-frontend/src/views/DocumentUpload.vue`
- Modify: `edumate-frontend/src/views/KnowledgeBase.vue`
- Modify: `edumate-frontend/src/views/KnowledgeBaseCourse.vue`
- Modify: `edumate-frontend/src/views/KnowledgeBaseReader.vue`
- Modify: `edumate-frontend/src/views/ChatTutor.vue`
- Modify: `edumate-frontend/src/views/QuizBank.vue`

- [ ] **Step 1: 逐个移除根元素上的 `fade-in-up` 类**

每个文件的修改方式相同：将根 `<div>` 的 class 中的 `fade-in-up` 移除。

**DocumentUpload.vue**（第 2 行）：
```vue
<!-- 改前 -->
<div class="document-upload-page fade-in-up">
<!-- 改后 -->
<div class="document-upload-page">
```

**KnowledgeBase.vue**（第 2 行）：
```vue
<!-- 改前 -->
<div class="knowledge-base fade-in-up">
<!-- 改后 -->
<div class="knowledge-base">
```

**KnowledgeBaseCourse.vue**（第 2 行）：
```vue
<!-- 改前 -->
<div class="course-chapters fade-in-up" v-loading="loading">
<!-- 改后 -->
<div class="course-chapters" v-loading="loading">
```

**KnowledgeBaseReader.vue**（第 2 行）：
```vue
<!-- 改前 -->
<div class="reader-page fade-in-up" v-loading="loading">
<!-- 改后 -->
<div class="reader-page" v-loading="loading">
```

**ChatTutor.vue**（第 2 行）：
```vue
<!-- 改前 -->
<div class="chat-tutor fade-in-up">
<!-- 改后 -->
<div class="chat-tutor">
```

**QuizBank.vue**（第 2 行）：
```vue
<!-- 改前 -->
<div class="quiz-bank fade-in-up" v-loading="loading">
<!-- 改后 -->
<div class="quiz-bank" v-loading="loading">
```

- [ ] **Step 2: 前端热更新验证**

前端 dev server 会自动热更新。切换各页面，确认：
- 无闪烁
- 上传文档后切换到其他页面再切回，文件列表/进度仍然保留
- 聊天消息切换后保留

---

### Task 4: 全量编译 + 重启验证

- [ ] **Step 1: 后端全量编译**

Run: `mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 重启 Spring Boot + 测试 PPT 上传**

重启后端后，上传 `.ppt` 和 `.pptx` 文件，确认：
- 后端日志无 `IllegalArgumentException`
- 出现 `向量入库完成: N 条分块` 和 `ES 批量索引完成: N 条分块`
- 提取的文本内容包含表格中的文字

- [ ] **Step 3: 提交代码**

```bash
cd f:\JetBrains\RAG\EduMate
git add edumate-core/src/main/java/com/edumate/core/parser/DocumentParserService.java
git add edumate-frontend/src/components/layout/AppLayout.vue
git add edumate-frontend/src/views/DocumentUpload.vue
git add edumate-frontend/src/views/KnowledgeBase.vue
git add edumate-frontend/src/views/KnowledgeBaseCourse.vue
git add edumate-frontend/src/views/KnowledgeBaseReader.vue
git add edumate-frontend/src/views/ChatTutor.vue
git add edumate-frontend/src/views/QuizBank.vue
git commit -m "fix: PPT parsing with POIFSFileSystem, keep-alive and page flash"
```