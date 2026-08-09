# EduMate Phase 1：基础骨架 —— 执行计划书

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 EduMate 从单模块 Spring Boot 空壳改造为支持 LangChain4j 集成、Docker 依赖服务一键启动、文档层级解析的多模块项目。

**Architecture:** 3 模块 Maven 多模块结构（edumate-common / edumate-core / edumate-admin），父 POM 统一管理依赖版本。后端基于 Spring Boot 4.1.0 + JDK 21 + LangChain4j 1.0.0-Beta1，基础设施通过 Docker Compose 编排 Qdrant / Elasticsearch / Neo4j / Redis。

**Tech Stack:** JDK 21, Spring Boot 4.1.0, LangChain4j 1.0.0-Beta1, Apache PDFBox 3.0.4, Apache POI 5.4.0, IBM Docling 2.7.0, Qdrant (Docker), Elasticsearch 8.15 (Docker), Neo4j 5 (Docker), Redis 7 (Docker)

---

## 当前状态

```
EduMate/
├── pom.xml                          # Spring Boot 4.1.0 单模块，groupId=rag，仅3个基础依赖
├── compose.yaml                     # 空文件（services: { }）
├── src/main/java/rag/edumate/
│   └── EduMateApplication.java      # 标准 @SpringBootApplication 启动类
├── src/main/resources/
│   └── application.properties       # 仅 spring.application.name=EduMate
└── src/test/java/rag/edumate/
    └── EduMateApplicationTests.java # 标准空测试
```

## 目标状态

```
EduMate/
├── pom.xml                          # 父 POM，packaging=pom，管理3个子模块
├── compose.yaml                     # Qdrant + ES + Neo4j + Redis 完整配置
├── edumate-common/                  # 通用模块：DTO/Entity/Enum/工具类
├── edumate-core/                    # 核心模块：parser/retrieval/graph/rag/agent/eval
├── edumate-admin/                   # 启动模块：Controller/Config + 启动类
└── docs/
```

---

## Task 1: JDK 21 环境验证

**Files:**
- 无新建文件（验证环境）

- [ ] **Step 1: 验证 JDK 21 已安装**

```powershell
java -version
```

预期输出包含 `21.` 开头的版本号（如 `21.0.x`）。如果不是，需先安装 JDK 21 并配置 `JAVA_HOME` 环境变量。

- [ ] **Step 2: 验证 Maven 已安装且版本兼容**

```powershell
mvn --version
```

预期输出包含 `Maven 3.9.x` 或更高版本，且 `Java version: 21.x`。

- [ ] **Step 3: 验证 Docker 已安装并运行**

```powershell
docker --version
docker ps
```

预期 `docker --version` 输出版本号，`docker ps` 不报错（允许空列表）。

- [ ] **Step 4: 验证现有项目可编译**

```powershell
cd f:\JetBrains\RAG\EduMate
mvn clean compile -q
```

预期：BUILD SUCCESS（编译通过，无报错）。

- [ ] **Step 5: 验证现有测试可运行**

```powershell
mvn test -q
```

预期：BUILD SUCCESS，`contextLoads()` 测试通过。

- [ ] **Step 6: Commit**

```bash
# 无代码变更，仅确认环境就绪后的标记
```

---

## Task 2: 重构为多模块 Maven 项目

### 2.1 改造父 POM

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\pom.xml`

- [ ] **Step 1: 将父 POM 改为聚合型 POM**

将 `pom.xml` 改为 `packaging=pom`，定义 `groupId=com.edumate`，添加 `dependencyManagement` 统一管理所有依赖版本。删除原有的 `<dependencies>`（具体依赖下放到子模块）。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.edumate</groupId>
    <artifactId>edumate</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>EduMate</name>
    <description>基于 RAG 与知识图谱的课程学习智能助手</description>

    <modules>
        <module>edumate-common</module>
        <module>edumate-core</module>
        <module>edumate-admin</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <langchain4j.version>1.0.0-beta1</langchain4j.version>
        <pdfbox.version>3.0.4</pdfbox.version>
        <poi.version>5.4.0</poi.version>
        <docling.version>2.7.0</docling.version>
        <lombok.version>1.18.36</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- 子模块内部依赖 -->
            <dependency>
                <groupId>com.edumate</groupId>
                <artifactId>edumate-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.edumate</groupId>
                <artifactId>edumate-core</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- LangChain4j BOM -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-bom</artifactId>
                <version>${langchain4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- PDF 解析 -->
            <dependency>
                <groupId>org.apache.pdfbox</groupId>
                <artifactId>pdfbox</artifactId>
                <version>${pdfbox.version}</version>
            </dependency>

            <!-- PPT/Word 解析 -->
            <dependency>
                <groupId>org.apache.poi</groupId>
                <artifactId>poi-ooxml</artifactId>
                <version>${poi.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.poi</groupId>
                <artifactId>poi-scratchpad</artifactId>
                <version>${poi.version}</version>
            </dependency>

            <!-- IBM Docling -->
            <dependency>
                <groupId>com.ibm.docling</groupId>
                <artifactId>docling-core</artifactId>
                <version>${docling.version}</version>
            </dependency>

            <!-- Lombok -->
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>central</id>
            <url>https://repo.maven.apache.org/maven2</url>
        </repository>
        <repository>
            <id>aliyun</id>
            <url>https://maven.aliyun.com/repository/public</url>
        </repository>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
        </repository>
    </repositories>
</project>
```

- [ ] **Step 2: 按新父 POM 编译验证**

```powershell
cd f:\JetBrains\RAG\EduMate
mvn validate -q
```

预期：BUILD SUCCESS。Maven 会提示子模块目录不存在，这是正常的——下一步创建它们。

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "refactor: convert to multi-module parent POM with dependency management"
```

### 2.2 创建 edumate-common 模块

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\pom.xml`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\DocumentChunk.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\model\CourseMetadata.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\enums\DocumentType.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-common\src\main\java\com\edumate\common\enums\KnowledgeRelationType.java`

- [ ] **Step 1: 创建 edumate-common 的 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.edumate</groupId>
        <artifactId>edumate</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>edumate-common</artifactId>
    <name>edumate-common</name>
    <description>通用模块：数据模型、枚举、工具类</description>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 DocumentChunk —— 文档分块数据模型**

```java
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
```

- [ ] **Step 3: 创建 CourseMetadata —— 课程元数据**

```java
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
```

- [ ] **Step 4: 创建 DocumentType 枚举**

```java
package com.edumate.common.enums;

/**
 * 支持的文档类型
 */
public enum DocumentType {
    PDF("pdf"),
    PPTX("pptx"),
    DOCX("docx"),
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

- [ ] **Step 5: 创建 KnowledgeRelationType 枚举**

```java
package com.edumate.common.enums;

/**
 * 知识图谱关系类型
 */
public enum KnowledgeRelationType {
    /** 前置知识 —— 学习当前知识点之前需要掌握的内容 */
    PREREQUISITE("前置知识"),
    /** 后继知识 —— 当前知识点是后续学习的基础 */
    SUCCESSOR("后继知识"),
    /** 关联 —— 两个知识点之间存在交叉或类比关系 */
    RELATED_TO("关联"),
    /** 属于 —— 知识点属于某个章节/课程 */
    BELONGS_TO("属于"),
    /** 应用 —— 知识点在某个场景中的应用 */
    APPLIED_IN("应用");

    private final String displayName;

    KnowledgeRelationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

- [ ] **Step 6: 编译 edumate-common 模块**

```powershell
cd f:\JetBrains\RAG\EduMate
mvn clean compile -pl edumate-common -q
```

预期：BUILD SUCCESS。

- [ ] **Step 7: Commit**

```bash
git add edumate-common/
git commit -m "feat: add edumate-common module with DocumentChunk, CourseMetadata, DocumentType, KnowledgeRelationType"
```

### 2.3 创建 edumate-core 模块

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\pom.xml`

- [ ] **Step 1: 创建 edumate-core 的 pom.xml（仅框架，暂不引入具体依赖）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.edumate</groupId>
        <artifactId>edumate</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>edumate-core</artifactId>
    <name>edumate-core</name>
    <description>核心业务模块：文档解析、检索、知识图谱、RAG、Agent、评测</description>

    <dependencies>
        <dependency>
            <groupId>com.edumate</groupId>
            <artifactId>edumate-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 编译 edumate-core 模块**

```powershell
mvn clean compile -pl edumate-core -q
```

预期：BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add edumate-core/
git commit -m "feat: add edumate-core module skeleton"
```

### 2.4 创建 edumate-admin 模块（迁移现有代码）

**Files:**
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\pom.xml`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\EduMateApplication.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\resources\application.yml`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\test\java\com\edumate\admin\EduMateApplicationTests.java`

- [ ] **Step 1: 创建 edumate-admin 的 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.edumate</groupId>
        <artifactId>edumate</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>edumate-admin</artifactId>
    <name>edumate-admin</name>
    <description>启动模块：Spring Boot 入口、控制器、配置</description>

    <dependencies>
        <dependency>
            <groupId>com.edumate</groupId>
            <artifactId>edumate-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.edumate.admin.EduMateApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 edumate-admin 启动类（新包路径 com.edumate.admin）**

```java
package com.edumate.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EduMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduMateApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml（替换原 application.properties）**

```yaml
spring:
  application:
    name: EduMate

server:
  port: 8080

logging:
  level:
    com.edumate: DEBUG
```

- [ ] **Step 4: 创建启动测试类**

```java
package com.edumate.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EduMateApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: 删除旧的单模块 src 目录**

```powershell
Remove-Item -Recurse -Force 'f:\JetBrains\RAG\EduMate\src'
Remove-Item -Recurse -Force 'f:\JetBrains\RAG\EduMate\.mvn'
Remove-Item -Force 'f:\JetBrains\RAG\EduMate\mvnw', 'f:\JetBrains\RAG\EduMate\mvnw.cmd', 'f:\JetBrains\RAG\EduMate\HELP.md'
```

**注意**：`.gitignore` 和 `.gitattributes` 保留不动。

- [ ] **Step 6: 全量编译整个多模块项目**

```powershell
cd f:\JetBrains\RAG\EduMate
mvn clean compile -q
```

预期：BUILD SUCCESS，所有 3 个子模块编译通过。

- [ ] **Step 7: 运行全量测试**

```powershell
mvn test -q
```

预期：BUILD SUCCESS，`contextLoads()` 测试通过。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: migrate to multi-module structure (common/core/admin)"
```

---

## Task 3: Docker Compose 依赖服务配置

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\compose.yaml`
- Create: `f:\JetBrains\RAG\EduMate\.env.example`

- [ ] **Step 1: 编写完整的 compose.yaml**

```yaml
services:
  # Qdrant 向量数据库
  qdrant:
    image: qdrant/qdrant:latest
    container_name: edumate-qdrant
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant_data:/qdrant/storage
    environment:
      - QDRANT__SERVICE__GRPC_PORT=6334
      - QDRANT__SERVICE__HTTP_PORT=6333
    restart: unless-stopped

  # Elasticsearch 关键词检索
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.15.0
    container_name: edumate-es
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    restart: unless-stopped

  # Neo4j 图数据库
  neo4j:
    image: neo4j:5-enterprise
    container_name: edumate-neo4j
    ports:
      - "7474:7474"
      - "7687:7687"
    environment:
      - NEO4J_AUTH=neo4j/password123
      - NEO4J_PLUGINS=["apoc", "graph-data-science"]
    volumes:
      - neo4j_data:/data
    restart: unless-stopped

  # Redis 缓存/会话
  redis:
    image: redis:7-alpine
    container_name: edumate-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    restart: unless-stopped

volumes:
  qdrant_data:
  es_data:
  neo4j_data:
  redis_data:
```

- [ ] **Step 2: 创建 .env.example 环境变量模板**

```properties
# 通义千问 API Key
DASHSCOPE_API_KEY=sk-your-api-key-here

# Qdrant
QDRANT_HOST=localhost
QDRANT_GRPC_PORT=6334
QDRANT_HTTP_PORT=6333

# Elasticsearch
ES_HOST=localhost
ES_PORT=9200

# Neo4j
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=password123

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
```

- [ ] **Step 3: 启动 Docker Compose 并验证服务**

```powershell
cd f:\JetBrains\RAG\EduMate
docker compose up -d
```

启动后等待约 30 秒，然后验证各服务：

```powershell
# 验证 Qdrant
curl http://localhost:6333/healthz

# 验证 Elasticsearch
curl http://localhost:9200

# 验证 Neo4j
curl http://localhost:7474

# 验证 Redis
docker exec edumate-redis redis-cli ping
```

预期：Qdrant 返回健康状态，ES 返回 JSON 集群信息，Neo4j 返回 HTTP 200，Redis 返回 `PONG`。

- [ ] **Step 4: 停止所有服务**

```powershell
docker compose down
```

- [ ] **Step 5: Commit**

```bash
git add compose.yaml .env.example
git commit -m "feat: add Docker Compose config for Qdrant/ES/Neo4j/Redis"
```

---

## Task 4: Spring Boot 4.1 + LangChain4j 集成

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\pom.xml`（添加 LangChain4j 依赖）
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\config\LangChain4jConfig.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\test\java\com\edumate\admin\config\LangChain4jConfigTest.java`
- Modify: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\resources\application.yml`

- [ ] **Step 1: 在 edumate-admin/pom.xml 中添加 LangChain4j 依赖**

在 `edumate-admin/pom.xml` 的 `<dependencies>` 中添加以下依赖：

```xml
<!-- LangChain4j 核心 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-core</artifactId>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
</dependency>

<!-- 通义千问集成 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
</dependency>

<!-- Qdrant 向量存储 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qdrant</artifactId>
</dependency>

<!-- Elasticsearch 检索 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-elasticsearch</artifactId>
</dependency>
```

- [ ] **Step 2: 更新 application.yml 添加 LangChain4j 配置**

```yaml
spring:
  application:
    name: EduMate

server:
  port: 8080

logging:
  level:
    com.edumate: DEBUG

# LangChain4j 配置
langchain4j:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:}
    chat-model:
      model-name: qwen-plus
      temperature: 0.7
      max-tokens: 4096
    embedding-model:
      model-name: text-embedding-v3
```

- [ ] **Step 3: 创建 LangChain4jConfig 配置类**

```java
package com.edumate.admin.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.DashScopeChatModel;
import dev.langchain4j.model.dashscope.DashScopeEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 核心 Bean 配置
 * 当前仅配置 ChatModel 和 EmbeddingModel，Qdrant/ES/Neo4j 客户端在后续 Phase 添加
 */
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.dashscope.api-key:}")
    private String apiKey;

    @Value("${langchain4j.dashscope.chat-model.model-name:qwen-plus}")
    private String chatModelName;

    @Value("${langchain4j.dashscope.chat-model.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.dashscope.chat-model.max-tokens:4096}")
    private Integer maxTokens;

    @Value("${langchain4j.dashscope.embedding-model.model-name:text-embedding-v3}")
    private String embeddingModelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return DashScopeEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .build();
    }
}
```

- [ ] **Step 4: 创建 LangChain4jConfig 测试类**

```java
package com.edumate.admin.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LangChain4jConfigTest {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void chatLanguageModelShouldBeConfigured() {
        assertThat(chatLanguageModel).isNotNull();
    }

    @Test
    void embeddingModelShouldBeConfigured() {
        assertThat(embeddingModel).isNotNull();
    }
}
```

- [ ] **Step 5: 编译并运行测试**

```powershell
cd f:\JetBrains\RAG\EduMate
mvn clean compile -q
mvn test -q
```

预期：BUILD SUCCESS。注意：如果未设置 `DASHSCOPE_API_KEY` 环境变量，配置类仍会加载 Bean（使用空字符串），但测试中 Bean 实例化会因为 API Key 无效而失败。这是预期行为——Phase 1 仅验证编译通过和 Bean 注入框架正确。

- [ ] **Step 6: Commit**

```bash
git add edumate-admin/
git commit -m "feat: integrate LangChain4j with DashScope ChatModel and EmbeddingModel"
```

---

## Task 5: 文档上传与 Docling 层级解析

**Files:**
- Modify: `f:\JetBrains\RAG\EduMate\edumate-core\pom.xml`（添加文档解析依赖）
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\parser\DocumentParserService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\main\java\com\edumate\core\parser\HierarchicalChunkerService.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\parser\DocumentParserServiceTest.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\java\com\edumate\core\parser\HierarchicalChunkerServiceTest.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-core\src\test\resources\test-document.txt`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\main\java\com\edumate\admin\controller\DocumentController.java`
- Create: `f:\JetBrains\RAG\EduMate\edumate-admin\src\test\java\com\edumate\admin\controller\DocumentControllerTest.java`

### 5.1 添加文档解析依赖

- [ ] **Step 1: 在 edumate-core/pom.xml 中添加解析依赖**

```xml
<!-- 文档解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-scratchpad</artifactId>
</dependency>

<!-- 测试 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 编译验证依赖可用**

```powershell
mvn clean compile -pl edumate-core -q
```

预期：BUILD SUCCESS。

### 5.2 创建测试文件

- [ ] **Step 3: 创建测试用 TXT 文档**

```text
第1章 数据结构基础

1.1 什么是数据结构

数据结构是计算机存储、组织数据的方式。常见的数据结构包括数组、链表、栈、队列、树和图。

1.2 算法复杂度分析

算法复杂度分为时间复杂度和空间复杂度。时间复杂度用大O表示法，如O(1)、O(n)、O(n²)。

1.3 线性表

线性表是最基本的数据结构，包括顺序表和链表两种实现方式。

第2章 树与二叉树

2.1 树的基本概念

树是一种非线性数据结构，由节点和边组成。根节点是树的起点，叶子节点没有子节点。

2.2 二叉树

二叉树是每个节点最多有两个子节点的树结构。满二叉树和完全二叉树是两种特殊的二叉树。

2.3 二叉搜索树

二叉搜索树（BST）是一种特殊的二叉树，左子树所有节点值小于根节点，右子树所有节点值大于根节点。
```

### 5.3 实现 DocumentParserService

- [ ] **Step 4: 编写 DocumentParserService 测试**

```java
package com.edumate.core.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldParseEmptyFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("empty.txt");
        Files.writeString(testFile, "");

        String result = parserService.parse(testFile);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 5: 运行测试验证失败**

```powershell
mvn test -pl edumate-core -Dtest=DocumentParserServiceTest -q
```

预期：FAIL —— `DocumentParserService` 类尚未创建。

- [ ] **Step 6: 实现 DocumentParserService**

```java
package com.edumate.core.parser;

import com.edumate.common.enums.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析服务 —— 将多种格式文档提取为纯文本
 */
@Service
public class DocumentParserService {

    public String parse(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        String extension = getExtension(fileName);
        DocumentType type = DocumentType.fromExtension(extension);

        return switch (type) {
            case TXT, MARKDOWN -> Files.readString(filePath);
            case PDF -> parsePdf(filePath);
            case DOCX -> parseDocx(filePath);
            case PPTX -> parsePptx(filePath);
        };
    }

    private String parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String parseDocx(Path filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(paragraph ->
                    sb.append(paragraph.getText()).append("\n"));
            return sb.toString();
        }
    }

    private String parsePptx(Path filePath) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(filePath))) {
            StringBuilder sb = new StringBuilder();
            ppt.getSlides().forEach(slide ->
                    slide.getShapes().forEach(shape -> {
                        if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
                            sb.append(textShape.getText()).append("\n");
                        }
                    }));
            return sb.toString();
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException("无法识别的文件类型: " + fileName);
        }
        return fileName.substring(dotIndex + 1);
    }
}
```

- [ ] **Step 7: 运行测试验证通过**

```powershell
mvn test -pl edumate-core -Dtest=DocumentParserServiceTest -q
```

预期：PASS —— 2 个测试通过。

### 5.4 实现 HierarchicalChunkerService

- [ ] **Step 8: 编写 HierarchicalChunkerService 测试**

```java
package com.edumate.core.parser;

import com.edumate.common.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalChunkerServiceTest {

    private HierarchicalChunkerService chunkerService;

    @BeforeEach
    void setUp() {
        chunkerService = new HierarchicalChunkerService();
    }

    @Test
    void shouldChunkByChapterHeadings() {
        String content = """
                第1章 数据结构基础
                1.1 什么是数据结构
                数据结构是计算机存储、组织数据的方式。
                1.2 算法复杂度分析
                算法复杂度分为时间复杂度和空间复杂度。
                第2章 树与二叉树
                2.1 树的基本概念
                树是一种非线性数据结构。
                """;

        List<DocumentChunk> chunks = chunkerService.chunk(content, "数据结构", "2026-春");

        assertThat(chunks).isNotEmpty();
        // 应该至少按章节切分出 2 个大块
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        // 每个块应有章节路径
        assertThat(chunks).allMatch(chunk -> chunk.getChapterPath() != null);
        // 每个块应关联课程名
        assertThat(chunks).allMatch(chunk -> "数据结构".equals(chunk.getCourseName()));
    }

    @Test
    void shouldHandleEmptyContent() {
        List<DocumentChunk> chunks = chunkerService.chunk("", "测试课程", "2026-春");

        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldPreserveChapterHierarchy() {
        String content = """
                第1章 概述
                1.1 背景
                这是背景内容。
                1.2 目标
                这是目标内容。
                """;

        List<DocumentChunk> chunks = chunkerService.chunk(content, "测试课程", "2026-春");

        // 子节的 chapterPath 应包含父章节
        boolean hasSubSection = chunks.stream()
                .anyMatch(c -> c.getChapterPath().contains("1.1"));
        assertThat(hasSubSection).isTrue();
    }
}
```

- [ ] **Step 9: 运行测试验证失败**

```powershell
mvn test -pl edumate-core -Dtest=HierarchicalChunkerServiceTest -q
```

预期：FAIL —— `HierarchicalChunkerService` 类尚未创建。

- [ ] **Step 10: 实现 HierarchicalChunkerService**

```java
package com.edumate.core.parser;

import com.edumate.common.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 层级感知切分服务 —— 按"章→节→小节→知识点"结构切分文档
 */
@Service
public class HierarchicalChunkerService {

    /** 匹配章节标题：第X章 / 第Y节 / X.Y 等 */
    private static final Pattern CHAPTER_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十\\d]+章)\\s*(.*)", Pattern.MULTILINE);
    private static final Pattern SECTION_PATTERN =
            Pattern.compile("^(\\d+\\.\\d+)\\s+(.*)", Pattern.MULTILINE);

    /** 块大小上限（字符数），超过则二次切分 */
    private static final int MAX_CHUNK_SIZE = 2000;
    /** 块间重叠大小（字符数） */
    private static final int OVERLAP_SIZE = 200;

    public List<DocumentChunk> chunk(String content, String courseName, String semester) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        String currentChapter = "";
        String currentSection = "";
        StringBuilder currentChunk = new StringBuilder();

        String[] lines = content.split("\n");
        for (String line : lines) {
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);

            if (chapterMatcher.find()) {
                // 发现新章，先保存当前积累的块
                flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);
                currentChapter = chapterMatcher.group(1) + " " + chapterMatcher.group(2).trim();
                currentSection = "";
                currentChunk = new StringBuilder();
            } else if (sectionMatcher.find()) {
                // 发现新节
                flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);
                currentSection = sectionMatcher.group(1) + " " + sectionMatcher.group(2).trim();
                currentChunk = new StringBuilder();
            }

            currentChunk.append(line).append("\n");

            // 如果当前块超过上限，切分
            if (currentChunk.length() > MAX_CHUNK_SIZE) {
                flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);
                currentChunk = new StringBuilder();
                // 保留重叠上下文
                String overlap = currentChunk.length() > OVERLAP_SIZE
                        ? currentChunk.substring(currentChunk.length() - OVERLAP_SIZE)
                        : currentChunk.toString();
                currentChunk = new StringBuilder(overlap);
            }
        }

        // 最后一块
        flushChunk(chunks, currentChunk, currentChapter, currentSection, courseName);

        return chunks;
    }

    private void flushChunk(List<DocumentChunk> chunks, StringBuilder content,
                            String chapter, String section, String courseName) {
        if (content.isEmpty()) {
            return;
        }
        String chapterPath = buildChapterPath(chapter, section);
        chunks.add(DocumentChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content.toString().trim())
                .courseName(courseName)
                .chapterPath(chapterPath)
                .build());
        content.setLength(0);
    }

    private String buildChapterPath(String chapter, String section) {
        if (section.isEmpty()) {
            return chapter;
        }
        return chapter + " > " + section;
    }
}
```

- [ ] **Step 11: 运行测试验证通过**

```powershell
mvn test -pl edumate-core -Dtest=HierarchicalChunkerServiceTest -q
```

预期：PASS —— 3 个测试通过。

- [ ] **Step 12: 运行 edumate-core 全量测试**

```powershell
mvn test -pl edumate-core -q
```

预期：PASS —— 全部 5 个测试通过。

### 5.5 创建 DocumentController（REST 接口）

- [ ] **Step 13: 编写 DocumentController 测试**

```java
package com.edumate.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldUploadTxtFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "第1章 概述\n\n数据结构是计算机科学的基础。".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("courseName", "数据结构")
                        .param("semester", "2026-春"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test.txt"))
                .andExpect(jsonPath("$.chunks").isArray())
                .andExpect(jsonPath("$.chunks.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void shouldRejectUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.exe",
                "application/octet-stream",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("courseName", "数据结构")
                        .param("semester", "2026-春"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 14: 运行测试验证失败**

```powershell
mvn test -pl edumate-admin -Dtest=DocumentControllerTest -q
```

预期：FAIL —— `DocumentController` 尚未创建。

- [ ] **Step 15: 实现 DocumentController**

```java
package com.edumate.admin.controller;

import com.edumate.common.model.DocumentChunk;
import com.edumate.core.parser.DocumentParserService;
import com.edumate.core.parser.HierarchicalChunkerService;
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
            List.of("pdf", "pptx", "docx", "txt", "md");

    private final DocumentParserService parserService;
    private final HierarchicalChunkerService chunkerService;

    public DocumentController(DocumentParserService parserService,
                              HierarchicalChunkerService chunkerService) {
        this.parserService = parserService;
        this.chunkerService = chunkerService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("courseName") String courseName,
            @RequestParam("semester") String semester) throws IOException {

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
```

- [ ] **Step 16: 运行测试验证通过**

```powershell
mvn test -pl edumate-admin -Dtest=DocumentControllerTest -q
```

预期：PASS —— 2 个测试通过。

- [ ] **Step 17: 运行全量测试**

```powershell
cd f:\JetBrains\RAG\EduMate
mvn test -q
```

预期：BUILD SUCCESS，所有模块测试通过。

- [ ] **Step 18: Commit**

```bash
git add edumate-core/ edumate-admin/
git commit -m "feat: implement document parsing and hierarchical chunking with REST upload API"
```

---

## 验收检查清单

执行完所有 Task 后，逐项确认：

- [ ] `java -version` 输出 JDK 21
- [ ] `mvn clean compile` 在 EduMate 根目录下全量编译通过
- [ ] `mvn test` 全量测试通过（至少 10 个测试用例）
- [ ] `docker compose up -d` 能启动 Qdrant/ES/Neo4j/Redis 四个服务
- [ ] `curl http://localhost:6333/healthz` 返回 Qdrant 健康状态
- [ ] `curl http://localhost:9200` 返回 ES 集群信息
- [ ] 项目结构符合 3 模块架构（common / core / admin）
- [ ] 包路径统一为 `com.edumate.*`
- [ ] LangChain4j ChatModel 和 EmbeddingModel Bean 可注入
- [ ] POST `/api/documents/upload` 上传 TXT 文件返回解析后的分块 JSON
- [ ] 上传不支持的格式返回 400 错误

---

## Phase 1 完成后的项目结构

```
EduMate/
├── pom.xml                              # 父 POM，3 子模块 + dependencyManagement
├── compose.yaml                         # Qdrant/ES/Neo4j/Redis 完整配置
├── .env.example                         # 环境变量模板
├── edumate-common/
│   ├── pom.xml
│   └── src/main/java/com/edumate/common/
│       ├── model/
│       │   ├── DocumentChunk.java       # 文档分块模型
│       │   └── CourseMetadata.java      # 课程元数据
│       └── enums/
│           ├── DocumentType.java        # 文档类型枚举
│           └── KnowledgeRelationType.java # 知识图谱关系类型
├── edumate-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/edumate/core/
│       │   └── parser/
│       │       ├── DocumentParserService.java       # 多格式文档解析
│       │       └── HierarchicalChunkerService.java  # 层级感知切分
│       └── test/
│           ├── java/com/edumate/core/parser/
│           │   ├── DocumentParserServiceTest.java
│           │   └── HierarchicalChunkerServiceTest.java
│           └── resources/
│               └── test-document.txt
└── edumate-admin/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/edumate/admin/
        │   │   ├── EduMateApplication.java
        │   │   ├── config/
        │   │   │   └── LangChain4jConfig.java
        │   │   └── controller/
        │   │       └── DocumentController.java
        │   └── resources/
        │       └── application.yml
        └── test/java/com/edumate/admin/
            ├── EduMateApplicationTests.java
            ├── config/
            │   └── LangChain4jConfigTest.java
            └── controller/
                └── DocumentControllerTest.java
```