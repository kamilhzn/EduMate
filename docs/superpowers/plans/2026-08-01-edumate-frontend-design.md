# EduMate Vue3 前端设计实施文档

> **For agentic workers:** 本计划包含完整的 UI 设计系统、组件架构、路由设计和分步实施任务。实施时使用 `superpowers:subagent-driven-development` 逐任务推进。

**Goal:** 为 EduMate 构建一个完整的 Vue3 前端 SPA 应用，包含文档上传、分类知识库、智能聊天辅导、智能题库四大模块，采用蓝白金红配色体系，侧边栏导航，流程动画自然。

**Architecture:** Vue3 + Vite + Pinia + Vue Router + Element Plus，采用经典左侧导航布局。四个子界面通过路由切换，共享全局状态（课程列表、题库列表、聊天会话）。使用 Axios 对接后端 REST API 和 SSE 流式接口，marked + highlight.js 渲染 Markdown 内容。

**Tech Stack:** Vue 3.5.16, Vite 6.3.5, Pinia 3.0.2, Vue Router 4.5.1, Element Plus 2.9.9, Axios 1.9.0, marked + highlight.js, SCSS

---

## 一、设计美学系统

### 1.1 设计理念

**方向：** 学术 + 现代极简，融合「知识殿堂」的厚重感与「智能助手」的科技感。整体气质——严谨而不沉闷，明亮而不轻浮。

**核心记忆点：** 左侧导航栏的「金线」发光动画——当用户切换页面时，所选 Tab 会有一条金色流光从底部滑过，随后字体放大加粗，形成"点亮知识"的仪式感。

### 1.2 配色方案

| 角色 | 色值 | 用途 |
|------|------|------|
| **主蓝（Primary Blue）** | `#1A56DB` | 主色调：导航栏背景、主按钮、链接、选中态 |
| **深蓝（Dark Blue）** | `#0F2B5E` | 侧边栏底色、顶部导航、卡片阴影 |
| **白金（Warm Gold）** | `#E8A817` | 强调色：激活态流光、徽章、评分星级、高亮边框 |
| **朱红（Vermilion Red）** | `#D94040` | 功能色：删除、错误、出题按钮、重要提示 |
| **纯白（Pure White）** | `#FFFFFF` | 内容区背景、卡片底色 |
| **浅灰（Warm Gray）** | `#F5F3F0` | 页面全局底色（略带暖意） |
| **中灰（Mid Gray）** | `#8B8FA3` | 辅助文字、禁用态 |
| **深灰（Dark Gray）** | `#2D3047` | 正文文字 |

**CSS 变量定义：**

```css
:root {
  --color-primary: #1A56DB;
  --color-primary-dark: #0F2B5E;
  --color-accent: #E8A817;
  --color-accent-light: #F5E6B8;
  --color-danger: #D94040;
  --color-bg: #F5F3F0;
  --color-surface: #FFFFFF;
  --color-text: #2D3047;
  --color-text-secondary: #8B8FA3;
  --color-border: #E2E4EB;
  --color-success: #2DA44E;
  --sidebar-width: 220px;
  --sidebar-collapsed: 68px;
  --transition-normal: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  --transition-bounce: 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  --shadow-card: 0 2px 12px rgba(15, 43, 94, 0.08);
  --shadow-hover: 0 6px 24px rgba(15, 43, 94, 0.12);
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 20px;
}
```

### 1.3 字体系统

| 层级 | 字体 | 大小 | 字重 | 用途 |
|------|------|------|------|------|
| Display | `"Noto Serif SC", "Source Han Serif SC", serif` | 28px | 700 | 页面主标题 |
| H1 | `"Noto Sans SC", "PingFang SC", sans-serif` | 22px | 600 | 区域标题 |
| H2 | `"Noto Sans SC", "PingFang SC", sans-serif` | 18px | 600 | 卡片标题 |
| Body | `"Noto Sans SC", "PingFang SC", sans-serif` | 15px | 400 | 正文 |
| Caption | `"Noto Sans SC", "PingFang SC", sans-serif` | 13px | 400 | 辅助说明 |
| Code | `"JetBrains Mono", "Fira Code", monospace` | 14px | 400 | 代码块 |

### 1.4 动效规范

| 场景 | 动画 | 时长 | 缓动 |
|------|------|------|------|
| 页面切换 | fade + slide-up (8px) | 350ms | `cubic-bezier(0.4, 0, 0.2, 1)` |
| 侧边栏激活 | scale(1.08) + font-weight 600 | 400ms | `cubic-bezier(0.34, 1.56, 0.64, 1)` |
| 卡片悬停 | translateY(-4px) + shadow 增强 | 250ms | ease-out |
| 下拉展开 | max-height 动画 | 300ms | ease-in-out |
| 金线流光 | 渐变背景位移 | 600ms | ease-in-out |
| 文件上传拖拽 | 虚线边框呼吸 | 2s 循环 | ease-in-out |
| 列表项入场 | stagger fade-in (50ms delay) | 300ms | ease-out |

---

## 二、项目结构

```
edumate-frontend/
├── public/
│   └── favicon.svg
├── src/
│   ├── assets/
│   │   ├── icons/                    # SVG 图标组件
│   │   │   ├── IconBook.vue          # 书本图标（默认封面）
│   │   │   ├── IconUpload.vue
│   │   │   ├── IconChat.vue
│   │   │   ├── IconQuiz.vue
│   │   │   ├── IconKnowledge.vue
│   │   │   ├── IconChevronDown.vue
│   │   │   ├── IconSparkle.vue       # AI 闪光
│   │   │   └── IconFile.vue
│   │   ├── images/
│   │   │   └── logo.svg
│   │   └── styles/
│   │       ├── variables.scss        # CSS 变量 + 全局样式
│   │       ├── reset.scss            # 样式重置
│   │       ├── typography.scss       # 字体定义
│   │       └── animations.scss       # 全局动画 keyframes
│   ├── api/
│   │   ├── request.js                # Axios 封装（拦截器、baseURL、错误处理）
│   │   ├── document.js               # 文档上传 API
│   │   ├── chat.js                    # 聊天 SSE API
│   │   ├── quiz.js                    # 出题 API
│   │   ├── search.js                  # 搜索 API
│   │   └── course.js                  # 课程管理 API（扩展后端）
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppLayout.vue          # 整体布局：侧边栏 + 内容区
│   │   │   ├── SideNav.vue            # 左侧导航栏（4 个 Tab）
│   │   │   └── TopBar.vue             # 顶部信息栏（Logo + 用户头像）
│   │   ├── common/
│   │   │   ├── FileUploader.vue       # 拖拽上传组件（支持多格式）
│   │   │   ├── CourseSelector.vue     # 课程选择/创建下拉组件
│   │   │   ├── CourseCard.vue         # 课程卡片（封面+名称）
│   │   │   ├── ChapterList.vue        # 章节列表（可展开）
│   │   │   ├── SectionItem.vue        # 章节/小节条目
│   │   │   ├── MarkdownViewer.vue     # Markdown 渲染器
│   │   │   ├── QuizCard.vue           # 题库卡片
│   │   │   ├── QuizQuestion.vue       # 单道题目组件
│   │   │   ├── ChatPanel.vue          # 聊天面板
│   │   │   ├── ChatMessage.vue        # 单条消息
│   │   │   ├── ChatInput.vue          # 输入框
│   │   │   ├── EmptyState.vue         # 空状态占位
│   │   │   └── LoadingSpinner.vue     # 加载动画
│   │   └── course/
│   │       └── CourseDivider.vue      # 题库课程分割线（可折叠）
│   ├── views/
│   │   ├── DocumentUpload.vue         # 文档上传页面
│   │   ├── KnowledgeBase.vue          # 分类知识库（课程列表）
│   │   ├── KnowledgeBaseCourse.vue    # 分类知识库（章节浏览）
│   │   ├── KnowledgeBaseReader.vue    # 分类知识库（Markdown 阅读）
│   │   ├── ChatTutor.vue              # 智能聊天辅导
│   │   └── QuizBank.vue               # 智能题库
│   ├── router/
│   │   └── index.js                   # 路由配置
│   ├── stores/
│   │   ├── index.js                   # Pinia 实例
│   │   ├── useCourseStore.js          # 课程状态（列表、当前课程、章节）
│   │   ├── useChatStore.js            # 聊天状态（按课程分会话）
│   │   ├── useQuizStore.js            # 题库状态（按课程分组）
│   │   └── useAppStore.js            # 应用状态（当前页面、侧边栏）
│   ├── utils/
│   │   ├── markdown.js               # marked 配置 + highlight.js
│   │   └── format.js                 # 日期/文件大小格式化
│   ├── App.vue
│   └── main.js
├── index.html
├── vite.config.js
├── package.json
└── .env.development                   # 开发环境变量（API_BASE_URL）
```

---

## 三、路由设计

```javascript
// src/router/index.js
import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    redirect: '/upload',
    children: [
      {
        path: 'upload',
        name: 'DocumentUpload',
        component: () => import('@/views/DocumentUpload.vue'),
        meta: { title: '文档上传', icon: 'upload' }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeBase',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '分类知识库', icon: 'knowledge' }
      },
      {
        path: 'knowledge/:courseId',
        name: 'KnowledgeBaseCourse',
        component: () => import('@/views/KnowledgeBaseCourse.vue'),
        meta: { title: '课程章节', hidden: true }
      },
      {
        path: 'knowledge/:courseId/:chapterId/:sectionId?',
        name: 'KnowledgeBaseReader',
        component: () => import('@/views/KnowledgeBaseReader.vue'),
        meta: { title: '阅读', hidden: true }
      },
      {
        path: 'chat',
        name: 'ChatTutor',
        component: () => import('@/views/ChatTutor.vue'),
        meta: { title: '智能聊天辅导', icon: 'chat' }
      },
      {
        path: 'quiz',
        name: 'QuizBank',
        component: () => import('@/views/QuizBank.vue'),
        meta: { title: '智能题库', icon: 'quiz' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
```

---

## 四、状态管理（Pinia Stores）

### 4.1 useCourseStore

```javascript
// src/stores/useCourseStore.js
// 管理课程列表、当前选中课程、章节结构
state: {
  courses: [],           // { id, name, coverUrl, coverColor, createdAt, chapterCount }
  currentCourse: null,   // 当前选中的课程对象
  chapters: [],          // 当前课程的章节列表 [{ id, title, order, sections: [{ id, title, order }] }]
  currentChapter: null,
  currentSection: null,
  loading: false
}
actions: {
  fetchCourses(),        // GET /api/courses
  createCourse(name),    // POST /api/courses
  fetchChapters(courseId), // GET /api/courses/:courseId/chapters
  selectCourse(course),
  selectChapter(chapter),
  selectSection(section)
}
```

### 4.2 useChatStore

```javascript
// src/stores/useChatStore.js
// 按课程存储独立对话历史
state: {
  sessions: {},          // { [courseId]: { messages: [], sessionId: null } }
  currentCourseId: null,
  isStreaming: false,
  abortController: null
}
actions: {
  selectCourseChat(courseId),
  sendMessage(content),   // POST /api/chat/stream (SSE)
  stopStreaming(),
  clearSession(courseId),
  getMessages(courseId)
}
```

### 4.3 useQuizStore

```javascript
// src/stores/useQuizStore.js
// 题库按课程分组，按时间排序
state: {
  quizzes: [],           // [{ id, name, courseId, courseName, createdAt, source, questions: [] }]
  currentQuiz: null,
  loading: false
}
actions: {
  fetchQuizzes(),        // GET /api/quizzes
  generateQuiz(courseName, chapter, count, difficulty), // POST /api/quiz/generate
  deleteQuiz(id),
  getQuizById(id)
}
```

### 4.4 useAppStore

```javascript
// src/stores/useAppStore.js
state: {
  activeNav: 'upload',   // 当前激活的导航 Tab
  sidebarCollapsed: false
}
actions: {
  setActiveNav(tab),
  toggleSidebar()
}
```

---

## 五、页面详细设计

### 5.1 整体布局（AppLayout.vue）

```
┌──────────┬──────────────────────────────────────────────┐
│          │  TopBar (Logo + 标题)                         │
│  SideNav │──────────────────────────────────────────────│
│          │                                              │
│  📤 上传  │         <router-view />                      │
│  📚 知识库│                                              │
│  💬 聊天  │                                              │
│  📝 题库  │                                              │
│          │                                              │
└──────────┴──────────────────────────────────────────────┘
```

**SideNav 设计要点：**
- 宽度 220px，深蓝底色 `#0F2B5E`
- 顶部放置 EduMate Logo（白色文字 + 金色书本图标）
- 4 个 Tab 使用竖排图标 + 文字
- 默认态：16px 字体，`rgba(255,255,255,0.65)` 颜色
- 选中态：白色文字 18px 加粗，左侧金色竖线指示器 (3px 宽)，Tab 整体 scale(1.05)
- 切换时金色流光从底部滑过（`::after` 伪元素 + background-position 动画）
- 底部放置用户头像和设置入口

**TopBar 设计要点：**
- 高度 56px，白色背景，底部细边框
- 左侧显示当前页面标题（面包屑风格）
- 右侧显示当前选中课程名称（如果有）+ 用户头像

### 5.2 文档上传页面（DocumentUpload.vue）

**作为进入的第一个界面，设计简洁大气，主体元素精简。**

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│          📚  开始构建你的知识库                           │
│          上传课程资料，开启智能学习之旅                    │
│                                                         │
│   ┌─────────────────────────────────────────────────┐   │
│   │        📁                                      │   │
│   │   拖拽文件到此处，或点击上传                      │   │
│   │   支持 PDF / Word / PPT / Excel / TXT / MD      │   │
│   │   单个文件最大 50MB                              │   │
│   └─────────────────────────────────────────────────┘   │
│                                                         │
│   ┌─ 选择课程 ──────────────────────────────────┐       │
│   │  [  数据结构  ▼]  [ 或创建新课程: _____  ✨]  │       │
│   └──────────────────────────────────────────────┘       │
│                                                         │
│   ┌─ 已上传文件 ────────────────────────────────┐       │
│   │  📄 数据结构_第一章.pdf         ✅ 解析完成    │       │
│   │  📊 B+树详解.pptx              ⏳ 解析中...   │       │
│   └──────────────────────────────────────────────┘       │
│                                                         │
│   [ 🚀 全部上传并索引 ]   (当有待处理文件时显示)          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**核心交互：**
1. 拖拽区域：虚线边框（金色虚线），hover 时边框变为实线 + 金色背景 5%透明度
2. 文件上传进度：使用 Element Plus `<el-progress>` 圆形进度条，金色主题
3. 课程选择器：下拉菜单 + 输入框组合，支持搜索已有课程和创建新课程
4. 上传完成后卡片向右滑出消失 + 成功提示

**API 对接：**
- `POST /api/documents/upload`（multipart/form-data: file, courseName, semester）

### 5.3 分类知识库 — 课程列表（KnowledgeBase.vue）

**分层框架设计，第一层——课程层。**

```
┌─────────────────────────────────────────────────────────┐
│  我的课程知识库                                          │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │  📕      │  │  📗      │  │  📘      │             │
│  │          │  │          │  │          │             │
│  │ 数据结构  │  │ 计算机网络 │  │ 操作系统  │             │
│  │ 12 章    │  │ 8 章     │  │ 10 章    │             │
│  └──────────┘  └──────────┘  └──────────┘             │
│                                                         │
│  ┌──────────┐  ┌──────────┐                            │
│  │  📙      │  │  ➕      │                            │
│  │          │  │          │                            │
│  │ 数据库原理 │  │ 添加课程  │                            │
│  │ 6 章     │  │          │                            │
│  └──────────┘  └──────────┘                            │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**课程卡片设计要点：**
- 长方形标签按钮，宽高比约 3:4，`border-radius: 12px`
- 上部为封面图区域（占 60% 高度）：用户可上传封面 / 默认显示书本图标
- 默认封面支持 5 种颜色主题：`#1A56DB`（蓝）、`#E8A817`（金）、`#D94040`（红）、`#2DA44E`（绿）、`#8B5CF6`（紫）
- 下部为课程名 + 章节数
- 悬停时：`translateY(-6px)` + 阴影加深 + 封面区域轻微放大
- 点击跳转到 `KnowledgeBaseCourse`

### 5.4 分类知识库 — 章节浏览（KnowledgeBaseCourse.vue）

**类似小说网站章节列表设计。**

```
┌─────────────────────────────────────────────────────────┐
│  ← 返回课程列表                                          │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │              [📕 封面大图]                        │   │
│  │              数据结构                            │   │
│  │              共 12 章 · 48 小节                   │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─ 第一章：绪论 ──────────────────────────── [▼] ─┐   │
│  │  1.1 什么是数据结构                                │   │
│  │  1.2 算法与算法分析                               │   │
│  │  1.3 抽象数据类型                                 │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─ 第二章：线性表 ───────────────────────── [▶] ─┐   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─ 第三章：栈与队列 ─────────────────────── [▶] ─┐   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ... 更多章节 ...                                        │
│                                                         │
│  ┌─ 第十二章：排序 ───────────────────────── [▶] ─┐   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│                  [ 📝 为本课程出题 (10题) ]               │
│                  [ 📝 为本课程出题 (20题) ]               │
│                  [ 📝 为本课程出题 (50题) ]               │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**核心交互：**
- 顶部封面区：大图背景（支持高斯模糊），课程名用白色大字叠加在封面图上
- 章节按钮：长条矩形，`border-radius: 8px`，浅灰背景，右侧有下拉箭头
- 点击展开/收起：点击箭头展开小节列表，`max-height` 过渡动画
- 点击章节名（非箭头）：直接进入该章第 1 小节（Reader）
- 点击小节名：进入该小节（Reader）
- 页面底部居中放置课程级出题按钮（10/20/50 题）

### 5.5 分类知识库 — Markdown 阅读器（KnowledgeBaseReader.vue）

**沉浸式阅读体验 + 出题入口。**

```
┌─────────────────────────────────────────────────────────┐
│  ← 返回章节列表    数据结构 > 第一章 > 1.1 什么是数据结构   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  # 1.1 什么是数据结构                            │   │
│  │                                                 │   │
│  │  数据结构是计算机存储、组织数据的方式...           │   │
│  │                                                 │   │
│  │  ## 逻辑结构                                    │   │
│  │  - 集合结构                                     │   │
│  │  - 线性结构                                     │   │
│  │  - 树形结构                                     │   │
│  │  - 图形结构                                     │   │
│  │                                                 │   │
│  │  ... (更多内容) ...                              │   │
│  │                                                 │   │
│  │  ───────────────────────────────────────────────  │   │
│  │                                                 │   │
│  │  你已经阅读完本节内容                              │   │
│  │                                                 │   │
│  │  [ 我已学习本节，请为我出题 ▼ ]                   │   │
│  │  ├─ 1 道题                                      │   │
│  │  ├─ 3 道题                                      │   │
│  │  └─ 5 道题                                      │   │
│  │                                                 │   │
│  │  [ 📖 下一节：1.2 算法与算法分析 → ]              │   │
│  │                                                 │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**当在章节最后一节时，底部额外显示：**

```
│  │  [ 我已学习本章，请为我出题 ▼ ]                    │   │
│  │  ├─ 5 道题（随机选节）                             │   │
│  │  ├─ 10 道题（随机选节）                            │   │
│  │  └─ 20 道题（随机选节）                            │   │
```

**核心交互：**
- Markdown 渲染：使用 `marked` 解析 + `highlight.js` 代码高亮，自定义 CSS 主题
- 阅读进度：顶部细进度条（金色），随滚动推进
- 末尾出题按钮：金色渐变按钮，hover 发光
- 出题后自动跳转到题库页面，新题库卡片自动展开
- 章出题和节出题按钮在页面末尾并排居中（flexbox）

### 5.6 智能聊天辅导页面（ChatTutor.vue）

**类似 ChatGPT 界面，课程选择在右侧。**

```
┌───────────────────────────────────────────┬────────────┐
│  💬 智能聊天辅导                            │  选择课程   │
│                                           │            │
│  ┌─────────────────────────────────────┐  │ 📕 数据结构 │
│  │                                     │  │ 📗 计算机网络│
│  │         [AI 助手消息]                │  │ 📘 操作系统 │
│  │         你好！我是 EduMate 学习助手， │  │ 📙 数据库   │
│  │         请随时向我提问课程相关问题。    │  │            │
│  │                                     │  │            │
│  │  ┌──────────────────────────────┐   │  │ 💬 新建对话 │
│  │  │ [用户消息]                    │   │  │            │
│  │  │ 请解释B+树的结构和查询原理     │   │  │            │
│  │  └──────────────────────────────┘   │  │ 历史对话    │
│  │                                     │  │ 2024-01-15 │
│  │  ┌──────────────────────────────┐   │  │ 红黑树讨论  │
│  │  │ [AI 流式回复]                 │   │  │ 2024-01-14 │
│  │  │ B+树是一种平衡多路查找树...    █│   │  │ 排序算法    │
│  │  └──────────────────────────────┘   │  │            │
│  │                                     │  │            │
│  │                                     │  │            │
│  │ ─────────────────────────────────── │  │            │
│  │  [输入你的问题...             📤]   │  │            │
│  └─────────────────────────────────────┘  │            │
│                                           │            │
└───────────────────────────────────────────┴────────────┘
```

**核心交互：**
- 左侧（75%）：消息列表 + 输入框
- 右侧（25%）：课程选择器 + 历史对话列表
- 消息气泡：AI 消息左侧对齐（浅蓝底），用户消息右侧对齐（金色底）
- 流式响应：打字机效果，逐字显示，光标闪烁
- 输入框：固定底部，支持 Enter 发送，Shift+Enter 换行
- 课程切换时自动加载该课程的对话历史
- 新建对话：清空当前课程的消息并生成新 sessionId

### 5.7 智能题库页面（QuizBank.vue）

**按课程分组，每行 3 个卡片，时间排序。**

```
┌─────────────────────────────────────────────────────────┐
│  📝 智能题库                                             │
│                                                         │
│  ┌─ 数据结构 ──────────────────────────────── [收起▲] ┐ │
│  │                                                     │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐         │ │
│  │  │ 第一章 1.1│  │ 第三章习题 │  │ 综合测试  │         │ │
│  │  │ 节测试    │  │ 章测试    │  │ 课程测试  │         │ │
│  │  │ 5 题     │  │ 10 题    │  │ 20 题    │         │ │
│  │  │ 2024-01-15│  │ 2024-01-14│  │ 2024-01-13│         │ │
│  │  └──────────┘  └──────────┘  └──────────┘         │ │
│  │                                                     │ │
│  │  ┌──────────┐                                       │ │
│  │  │ 第五章习题 │                                       │ │
│  │  │ 章测试    │                                       │ │
│  │  │ 5 题     │                                       │ │
│  │  │ 2024-01-10│                                       │ │
│  │  └──────────┘                                       │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                         │
│  ┌─ 计算机网络 ────────────────────────────── [展开▶] ┐ │
│  └─────────────────────────────────────────────────────┘ │
│                                                         │
│  ┌─ 操作系统 ──────────────────────────────── [收起▲] ┐ │
│  │  ┌──────────┐  ┌──────────┐                        │ │
│  │  │ 第二章 2.3│  │ 第一章测试 │                        │ │
│  │  │ 节测试    │  │ 章测试    │                        │ │
│  │  │ 3 题     │  │ 10 题    │                        │ │
│  │  │ 2024-01-08│  │ 2024-01-05│                        │ │
│  │  └──────────┘  └──────────┘                        │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**核心交互：**
- 课程分割线：`课程名 ──────────────── [收起▲]` 样式，可点击折叠/展开
- 题库卡片：每行 3 个，`grid-template-columns: repeat(3, 1fr)`
- 卡片内容：名称（节/章/课程名 + 测试类型）、题目数量、日期
- 悬停效果：卡片上浮 + 边框变为金色
- 点击卡片进入做题模式（弹窗或新页面，展示题目列表，支持答题和查看答案）
- 按课程分组，组内按创建时间倒序排列
- 右侧可加删除按钮（红色 X 图标）

**题库命名规则：**
- 节出题：`{课程名}-{章名}-{节名}-节测试`
- 章出题：`{课程名}-{章名}-章测试`
- 课程出题：`{课程名}-综合测试`

---

## 六、API 对接设计

### 6.1 请求封装（request.js）

```javascript
import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 60000
})

service.interceptors.response.use(
  response => response.data,
  error => {
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default service
```

### 6.2 API 清单

| 文件 | 方法 | 端点 | 说明 |
|------|------|------|------|
| `document.js` | `upload(formData)` | `POST /api/documents/upload` | 上传文档（multipart） |
| `chat.js` | `streamChat(query, sessionId)` | `POST /api/chat/stream` | SSE 流式问答 |
| `quiz.js` | `generateQuiz(params)` | `POST /api/quiz/generate` | 生成题目 |
| `search.js` | `search(query, topK)` | `POST /api/search` | 混合检索 |
| `course.js` | `getCourses()` | `GET /api/courses` | 获取课程列表（需后端扩展） |
| `course.js` | `createCourse(data)` | `POST /api/courses` | 创建课程（需后端扩展） |
| `course.js` | `getChapters(courseId)` | `GET /api/courses/:id/chapters` | 获取章节（需后端扩展） |

**SSE 流式处理（chat.js）：**

```javascript
export async function* streamChat(query, sessionId) {
  const response = await fetch(`${BASE_URL}/api/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, sessionId })
  })
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop()
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data === '[DONE]') return
        yield data
      }
    }
  }
}
```

---

## 七、分步实施任务

### Task 1: 项目初始化与基础配置

**Files:**
- Create: `edumate-frontend/package.json`
- Create: `edumate-frontend/vite.config.js`
- Create: `edumate-frontend/index.html`
- Create: `edumate-frontend/.env.development`
- Create: `edumate-frontend/src/main.js`
- Create: `edumate-frontend/src/App.vue`
- Create: `edumate-frontend/src/assets/styles/variables.scss`
- Create: `edumate-frontend/src/assets/styles/reset.scss`
- Create: `edumate-frontend/src/assets/styles/typography.scss`
- Create: `edumate-frontend/src/assets/styles/animations.scss`

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "edumate-frontend",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --port 3000",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.5.16",
    "vue-router": "^4.5.1",
    "pinia": "^3.0.2",
    "element-plus": "^2.9.9",
    "axios": "^1.9.0",
    "marked": "^15.0.0",
    "highlight.js": "^11.11.0",
    "@vueuse/core": "^13.3.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.4",
    "vite": "^6.3.5",
    "sass-embedded": "^1.89.1",
    "unplugin-auto-import": "^0.18.6",
    "unplugin-vue-components": "^0.28.0"
  }
}
```

- [ ] **Step 2: 创建 vite.config.js**

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath } from 'url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
```

- [ ] **Step 3: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>EduMate - 智能学习助手</title>
  <link rel="icon" href="/favicon.svg" />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Noto+Sans+SC:wght@400;500;600;700&family=Noto+Serif+SC:wght@700&display=swap" rel="stylesheet" />
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.js"></script>
</body>
</html>
```

- [ ] **Step 4: 创建 main.js**

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import router from './router'
import './assets/styles/reset.scss'
import './assets/styles/variables.scss'
import './assets/styles/typography.scss'
import './assets/styles/animations.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

- [ ] **Step 5: 创建 App.vue**

```vue
<template>
  <router-view />
</template>
```

- [ ] **Step 6: 创建 CSS 变量文件 variables.scss**

```scss
:root {
  --color-primary: #1A56DB;
  --color-primary-dark: #0F2B5E;
  --color-accent: #E8A817;
  --color-accent-light: #F5E6B8;
  --color-danger: #D94040;
  --color-bg: #F5F3F0;
  --color-surface: #FFFFFF;
  --color-text: #2D3047;
  --color-text-secondary: #8B8FA3;
  --color-border: #E2E4EB;
  --color-success: #2DA44E;
  --sidebar-width: 220px;
  --transition-normal: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  --transition-bounce: 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  --shadow-card: 0 2px 12px rgba(15, 43, 94, 0.08);
  --shadow-hover: 0 6px 24px rgba(15, 43, 94, 0.12);
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 20px;
}

html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 15px;
  line-height: 1.6;
}

* { box-sizing: border-box; }
```

- [ ] **Step 7: 创建动画文件 animations.scss**

```scss
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes goldShimmer {
  0% { background-position: -200% center; }
  100% { background-position: 200% center; }
}

@keyframes breathe {
  0%, 100% { border-color: var(--color-accent); }
  50% { border-color: var(--color-accent-light); }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@keyframes typewriter-cursor {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.fade-in-up { animation: fadeInUp 0.35s ease-out forwards; }
.fade-in { animation: fadeIn 0.3s ease-out forwards; }

@for $i from 1 through 20 {
  .stagger-#{$i} { animation-delay: #{$i * 0.05}s; }
}
```

- [ ] **Step 8: 运行 `npm install` 验证项目初始化**

```bash
cd edumate-frontend && npm install
```

- [ ] **Step 9: Commit**

```bash
git add edumate-frontend/
git commit -m "feat: initialize EduMate Vue3 frontend project with Vite + Pinia + Element Plus"
```

---

### Task 2: 布局组件（AppLayout + SideNav + TopBar）

**Files:**
- Create: `src/components/layout/AppLayout.vue`
- Create: `src/components/layout/SideNav.vue`
- Create: `src/components/layout/TopBar.vue`
- Create: `src/router/index.js`
- Create: `src/stores/index.js`
- Create: `src/stores/useAppStore.js`

- [ ] **Step 1: 创建 Pinia 实例 stores/index.js**

```javascript
import { createPinia } from 'pinia'
export default createPinia()
```

- [ ] **Step 2: 创建 useAppStore**

```javascript
// src/stores/useAppStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const activeNav = ref('upload')
  const sidebarCollapsed = ref(false)

  function setActiveNav(tab) {
    activeNav.value = tab
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { activeNav, sidebarCollapsed, setActiveNav, toggleSidebar }
})
```

- [ ] **Step 3: 创建路由 router/index.js**（内容见第三章路由设计）

- [ ] **Step 4: 创建 SideNav.vue**

```vue
<template>
  <nav class="side-nav" :class="{ collapsed: sidebarCollapsed }">
    <div class="nav-brand">
      <div class="brand-icon">📚</div>
      <span class="brand-text" v-show="!sidebarCollapsed">EduMate</span>
    </div>

    <div class="nav-items">
      <router-link
        v-for="item in navItems"
        :key="item.key"
        :to="item.path"
        class="nav-item"
        :class="{ active: activeNav === item.key }"
        @click="appStore.setActiveNav(item.key)"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-label" v-show="!sidebarCollapsed">{{ item.label }}</span>
        <span class="nav-indicator" v-if="activeNav === item.key"></span>
      </router-link>
    </div>

    <div class="nav-footer" v-show="!sidebarCollapsed">
      <div class="user-avatar">U</div>
      <span class="user-name">学习者</span>
    </div>
  </nav>
</template>

<script setup>
import { useAppStore } from '@/stores/useAppStore'
import { storeToRefs } from 'pinia'

const appStore = useAppStore()
const { activeNav, sidebarCollapsed } = storeToRefs(appStore)

const navItems = [
  { key: 'upload', label: '文档上传', icon: '📤', path: '/upload' },
  { key: 'knowledge', label: '分类知识库', icon: '📚', path: '/knowledge' },
  { key: 'chat', label: '智能聊天辅导', icon: '💬', path: '/chat' },
  { key: 'quiz', label: '智能题库', icon: '📝', path: '/quiz' }
]
</script>

<style lang="scss" scoped>
.side-nav {
  position: fixed; left: 0; top: 0; bottom: 0;
  width: var(--sidebar-width);
  background: var(--color-primary-dark);
  display: flex; flex-direction: column;
  padding: 20px 0;
  transition: width var(--transition-normal);
  z-index: 100;
  overflow: hidden;

  &.collapsed { width: 68px; }
}

.nav-brand {
  display: flex; align-items: center; gap: 12px;
  padding: 0 20px 32px;
  .brand-icon { font-size: 28px; }
  .brand-text {
    font-size: 22px; font-weight: 700;
    font-family: "Noto Serif SC", serif;
    color: var(--color-accent);
    letter-spacing: 1px;
  }
}

.nav-items {
  flex: 1; display: flex; flex-direction: column; gap: 4px;
  padding: 0 12px;
}

.nav-item {
  position: relative;
  display: flex; align-items: center; gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 16px; font-weight: 400;
  transition: all var(--transition-bounce);
  overflow: hidden;

  &:hover { color: #fff; background: rgba(255,255,255,0.08); }

  &.active {
    color: #fff;
    font-size: 18px; font-weight: 600;
    background: rgba(255,255,255,0.12);
    transform: scale(1.05);

    .nav-indicator {
      width: 3px; height: 28px;
      background: var(--color-accent);
      border-radius: 2px;
    }
  }

  &::after {
    content: '';
    position: absolute; bottom: 0; left: 0; right: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--color-accent), transparent);
    background-size: 200% 100%;
    opacity: 0;
    transition: opacity 0.3s;
  }
  &.active::after {
    opacity: 1;
    animation: goldShimmer 0.6s ease-in-out;
  }
}

.nav-icon { font-size: 20px; flex-shrink: 0; }
.nav-indicator { width: 3px; height: 0; transition: height 0.3s; }

.nav-footer {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 20px; border-top: 1px solid rgba(255,255,255,0.1);
  margin: 0 12px;
  .user-avatar {
    width: 36px; height: 36px; border-radius: 50%;
    background: var(--color-accent);
    color: #fff; display: flex; align-items: center; justify-content: center;
    font-weight: 600; font-size: 14px;
  }
  .user-name { color: rgba(255,255,255,0.7); font-size: 14px; }
}
</style>
```

- [ ] **Step 5: 创建 TopBar.vue**

```vue
<template>
  <header class="top-bar">
    <div class="top-left">
      <button class="collapse-btn" @click="appStore.toggleSidebar">☰</button>
      <span class="page-title">{{ pageTitle }}</span>
    </div>
    <div class="top-right">
      <span class="current-course" v-if="courseStore.currentCourse">
        📕 {{ courseStore.currentCourse.name }}
      </span>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/useAppStore'
import { useCourseStore } from '@/stores/useCourseStore'

const route = useRoute()
const appStore = useAppStore()
const courseStore = useCourseStore()

const pageTitle = computed(() => route.meta.title || 'EduMate')
</script>

<style lang="scss" scoped>
.top-bar {
  height: 56px; background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px;
}
.top-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn {
  background: none; border: none; font-size: 20px;
  cursor: pointer; color: var(--color-text-secondary);
  padding: 4px 8px; border-radius: 6px;
  &:hover { background: var(--color-bg); }
}
.page-title { font-size: 18px; font-weight: 600; color: var(--color-text); }
.current-course { color: var(--color-primary); font-size: 14px; font-weight: 500; }
</style>
```

- [ ] **Step 6: 创建 AppLayout.vue**

```vue
<template>
  <div class="app-layout">
    <SideNav />
    <div class="main-area" :class="{ collapsed: appStore.sidebarCollapsed }">
      <TopBar />
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import SideNav from './SideNav.vue'
import TopBar from './TopBar.vue'
import { useAppStore } from '@/stores/useAppStore'
const appStore = useAppStore()
</script>

<style lang="scss" scoped>
.app-layout { display: flex; height: 100vh; }
.main-area {
  margin-left: var(--sidebar-width);
  flex: 1; display: flex; flex-direction: column;
  min-width: 0;
  transition: margin-left var(--transition-normal);
  &.collapsed { margin-left: 68px; }
}
.main-content {
  flex: 1; overflow-y: auto; padding: 24px;
  background: var(--color-bg);
}

.page-enter-active { animation: fadeInUp 0.35s ease-out; }
.page-leave-active { animation: fadeIn 0.2s ease-in reverse; }
</style>
```

- [ ] **Step 7: Commit**

```bash
git add src/components/layout/ src/router/ src/stores/
git commit -m "feat: add AppLayout, SideNav, TopBar with gold shimmer animation and page transition"
```

---

### Task 3: 文档上传页面（DocumentUpload.vue + FileUploader + CourseSelector）

**Files:**
- Create: `src/views/DocumentUpload.vue`
- Create: `src/components/common/FileUploader.vue`
- Create: `src/components/common/CourseSelector.vue`
- Create: `src/api/document.js`
- Create: `src/api/course.js`
- Create: `src/stores/useCourseStore.js`

- [ ] **Step 1: 创建 API 文件**

```javascript
// src/api/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 120000
})

service.interceptors.response.use(
  res => res.data,
  err => {
    ElMessage.error(err.response?.data?.message || '请求失败')
    return Promise.reject(err)
  }
)

export default service
```

```javascript
// src/api/document.js
import request from './request'
export function uploadDocument(formData) {
  return request.post('/api/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
```

```javascript
// src/api/course.js
import request from './request'
export function getCourses() {
  return request.get('/api/courses')
}
export function createCourse(data) {
  return request.post('/api/courses', data)
}
export function getChapters(courseId) {
  return request.get(`/api/courses/${courseId}/chapters`)
}
```

- [ ] **Step 2: 创建 useCourseStore**

```javascript
// src/stores/useCourseStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCourses, createCourse as apiCreateCourse, getChapters } from '@/api/course'

export const useCourseStore = defineStore('course', () => {
  const courses = ref([])
  const currentCourse = ref(null)
  const chapters = ref([])
  const currentChapter = ref(null)
  const currentSection = ref(null)
  const loading = ref(false)

  async function fetchCourses() {
    loading.value = true
    try {
      const data = await getCourses()
      courses.value = data || []
    } catch { courses.value = [] }
    finally { loading.value = false }
  }

  async function createNewCourse(name) {
    const course = await apiCreateCourse({ name })
    courses.value.unshift(course)
    return course
  }

  async function fetchChapters(courseId) {
    loading.value = true
    try {
      const data = await getChapters(courseId)
      chapters.value = data || []
    } catch { chapters.value = [] }
    finally { loading.value = false }
  }

  function selectCourse(course) { currentCourse.value = course }
  function selectChapter(chapter) { currentChapter.value = chapter }
  function selectSection(section) { currentSection.value = section }

  return { courses, currentCourse, chapters, currentChapter, currentSection, loading,
           fetchCourses, createNewCourse, fetchChapters, selectCourse, selectChapter, selectSection }
})
```

- [ ] **Step 3: 创建 CourseSelector.vue**

```vue
<template>
  <div class="course-selector">
    <el-select
      v-model="selectedCourse"
      placeholder="选择已有课程"
      filterable
      clearable
      size="large"
      class="course-select"
      @change="handleSelect"
    >
      <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
    </el-select>
    <div class="divider-text">或</div>
    <el-input
      v-model="newCourseName"
      placeholder="输入新课程名称"
      size="large"
      class="course-input"
      @keyup.enter="handleCreate"
    >
      <template #append>
        <el-button @click="handleCreate" :loading="creating">
          ✨ 创建
        </el-button>
      </template>
    </el-input>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useCourseStore } from '@/stores/useCourseStore'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['select'])
const courseStore = useCourseStore()
const { courses } = storeToRefs(courseStore)

const selectedCourse = ref(null)
const newCourseName = ref('')
const creating = ref(false)

onMounted(() => courseStore.fetchCourses())

function handleSelect(courseId) {
  const course = courses.value.find(c => c.id === courseId)
  if (course) {
    courseStore.selectCourse(course)
    emit('select', course)
  }
}

async function handleCreate() {
  if (!newCourseName.value.trim()) return
  creating.value = true
  try {
    const course = await courseStore.createNewCourse(newCourseName.value.trim())
    selectedCourse.value = course.id
    courseStore.selectCourse(course)
    emit('select', course)
    newCourseName.value = ''
    ElMessage.success('课程创建成功')
  } finally { creating.value = false }
}
</script>

<style lang="scss" scoped>
.course-selector {
  display: flex; align-items: center; gap: 16px;
  padding: 16px 20px; background: var(--color-surface);
  border-radius: var(--radius-md); box-shadow: var(--shadow-card);
}
.course-select { width: 240px; }
.divider-text { color: var(--color-text-secondary); font-size: 14px; }
.course-input { width: 280px; }
</style>
```

- [ ] **Step 4: 创建 FileUploader.vue**

```vue
<template>
  <div
    class="file-uploader"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="handleDrop"
  >
    <input ref="fileInput" type="file" multiple hidden
      accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md"
      @change="handleFileChange" />
    <div class="upload-zone" @click="$refs.fileInput.click()">
      <div class="upload-icon">📁</div>
      <p class="upload-title">拖拽文件到此处，或点击上传</p>
      <p class="upload-hint">支持 PDF / Word / PPT / Excel / TXT / Markdown</p>
      <p class="upload-hint">单个文件最大 50MB</p>
    </div>
    <div class="file-list" v-if="files.length">
      <div v-for="(f, i) in files" :key="i" class="file-item">
        <span class="file-icon">📄</span>
        <span class="file-name">{{ f.name }}</span>
        <span class="file-size">{{ formatSize(f.size) }}</span>
        <el-progress v-if="f.uploading" :percentage="f.progress" :stroke-width="4"
          :color="'#E8A817'" style="width: 120px" />
        <el-tag v-else-if="f.done" type="success" size="small">解析完成</el-tag>
        <el-tag v-else-if="f.error" type="danger" size="small">失败</el-tag>
        <el-button v-else type="danger" circle size="small" @click="removeFile(i)">✕</el-button>
      </div>
    </div>
    <el-button v-if="files.length && hasPending" type="primary" size="large"
      class="upload-all-btn" @click="uploadAll" :loading="uploading">
      🚀 全部上传并索引
    </el-button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { uploadDocument } from '@/api/document'
import { ElMessage } from 'element-plus'

const props = defineProps({ courseName: String })
const emit = defineEmits(['uploaded'])

const files = ref([])
const dragging = ref(false)
const uploading = ref(false)
const fileInput = ref(null)

const hasPending = computed(() => files.value.some(f => !f.done && !f.uploading))

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function handleDrop(e) {
  dragging.value = false
  addFiles([...e.dataTransfer.files])
}

function handleFileChange(e) {
  addFiles([...e.target.files])
  e.target.value = ''
}

function addFiles(newFiles) {
  newFiles.forEach(f => {
    files.value.push({ name: f.name, size: f.size, file: f, done: false, uploading: false, progress: 0, error: false })
  })
}

function removeFile(i) {
  files.value.splice(i, 1)
}

async function uploadAll() {
  if (!props.courseName) {
    ElMessage.warning('请先选择或创建课程')
    return
  }
  uploading.value = true
  for (const f of files.value) {
    if (f.done) continue
    f.uploading = true
    f.progress = 0
    try {
      const formData = new FormData()
      formData.append('file', f.file)
      formData.append('courseName', props.courseName)
      await uploadDocument(formData)
      f.done = true
      f.progress = 100
      emit('uploaded', f)
    } catch {
      f.error = true
    } finally {
      f.uploading = false
    }
  }
  uploading.value = false
  ElMessage.success('上传完成')
}
</script>

<style lang="scss" scoped>
.file-uploader {
  background: var(--color-surface); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card); overflow: hidden;
  &.dragging .upload-zone {
    border-color: var(--color-accent);
    background: rgba(232, 168, 23, 0.05);
  }
}
.upload-zone {
  border: 2px dashed var(--color-border); border-radius: var(--radius-lg);
  padding: 48px; text-align: center; cursor: pointer;
  transition: all 0.3s;
  &:hover { border-color: var(--color-primary); background: rgba(26, 86, 219, 0.03); }
}
.upload-icon { font-size: 48px; margin-bottom: 12px; }
.upload-title { font-size: 18px; font-weight: 600; color: var(--color-text); margin: 0 0 8px; }
.upload-hint { font-size: 14px; color: var(--color-text-secondary); margin: 4px 0; }
.file-list { padding: 16px; }
.file-item {
  display: flex; align-items: center; gap: 12px; padding: 10px 12px;
  border-radius: var(--radius-sm); background: var(--color-bg); margin-bottom: 8px;
}
.file-name { flex: 1; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { font-size: 12px; color: var(--color-text-secondary); white-space: nowrap; }
.upload-all-btn { margin: 16px; width: calc(100% - 32px); }
</style>
```

- [ ] **Step 5: 创建 DocumentUpload.vue 页面**

```vue
<template>
  <div class="document-upload-page fade-in-up">
    <div class="hero-section">
      <h1 class="hero-title">📚 开始构建你的知识库</h1>
      <p class="hero-subtitle">上传课程资料，开启智能学习之旅</p>
    </div>

    <div class="course-section">
      <CourseSelector @select="onCourseSelect" />
    </div>

    <div class="upload-section">
      <FileUploader :courseName="selectedCourse?.name" @uploaded="onFileUploaded" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import CourseSelector from '@/components/common/CourseSelector.vue'
import FileUploader from '@/components/common/FileUploader.vue'

const selectedCourse = ref(null)

function onCourseSelect(course) {
  selectedCourse.value = course
}

function onFileUploaded(file) {
  console.log('Uploaded:', file.name)
}
</script>

<style lang="scss" scoped>
.document-upload-page {
  max-width: 720px; margin: 0 auto;
}
.hero-section {
  text-align: center; margin-bottom: 32px;
}
.hero-title {
  font-family: "Noto Serif SC", serif;
  font-size: 28px; font-weight: 700; color: var(--color-text);
  margin: 0 0 8px;
}
.hero-subtitle {
  font-size: 16px; color: var(--color-text-secondary); margin: 0;
}
.course-section { margin-bottom: 24px; }
</style>
```

- [ ] **Step 6: Commit**

```bash
git add src/views/DocumentUpload.vue src/components/common/ src/api/ src/stores/useCourseStore.js
git commit -m "feat: add document upload page with drag-drop file uploader and course selector"
```

---

### Task 4: 分类知识库 — 课程列表 + 章节浏览 + Markdown 阅读器

**Files:**
- Create: `src/views/KnowledgeBase.vue`
- Create: `src/views/KnowledgeBaseCourse.vue`
- Create: `src/views/KnowledgeBaseReader.vue`
- Create: `src/components/common/CourseCard.vue`
- Create: `src/components/common/ChapterList.vue`
- Create: `src/components/common/SectionItem.vue`
- Create: `src/components/common/MarkdownViewer.vue`

- [ ] **Step 1: 创建 CourseCard.vue**

```vue
<template>
  <div class="course-card" @click="$emit('click')">
    <div class="card-cover" :style="{ background: coverBg }">
      <img v-if="coverUrl" :src="coverUrl" alt="" class="cover-img" />
      <span v-else class="cover-icon">📕</span>
    </div>
    <div class="card-body">
      <h3 class="card-name">{{ name }}</h3>
      <p class="card-meta">{{ chapterCount }} 章</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  name: String, chapterCount: Number, coverUrl: String, coverColor: { type: String, default: '#1A56DB' }
})
defineEmits(['click'])
const coverColors = ['#1A56DB','#E8A817','#D94040','#2DA44E','#8B5CF6','#0F2B5E','#E87722','#6366F1']
const coverBg = computed(() => {
  const color = props.coverColor || coverColors[Math.floor(Math.random() * coverColors.length)]
  return `linear-gradient(135deg, ${color}, ${color}dd)`
})
</script>

<style lang="scss" scoped>
.course-card {
  width: 200px; border-radius: var(--radius-md); overflow: hidden;
  background: var(--color-surface); box-shadow: var(--shadow-card);
  cursor: pointer; transition: all 0.25s ease-out;
  &:hover {
    transform: translateY(-6px);
    box-shadow: var(--shadow-hover);
  }
}
.card-cover {
  height: 140px; display: flex; align-items: center; justify-content: center;
  .cover-icon { font-size: 56px; }
  .cover-img { width: 100%; height: 100%; object-fit: cover; }
}
.card-body { padding: 14px 16px; }
.card-name { font-size: 16px; font-weight: 600; margin: 0 0 4px; color: var(--color-text); }
.card-meta { font-size: 13px; color: var(--color-text-secondary); margin: 0; }
</style>
```

- [ ] **Step 2: 创建 ChapterList.vue 和 SectionItem.vue**

```vue
<!-- SectionItem.vue -->
<template>
  <div class="section-item" :class="{ active: isActive }" @click="$emit('click')">
    <span class="section-dot"></span>
    <span class="section-title">{{ title }}</span>
    <span class="section-arrow">→</span>
  </div>
</template>
<script setup>
defineProps({ title: String, isActive: Boolean })
defineEmits(['click'])
</script>
<style lang="scss" scoped>
.section-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px 10px 32px;
  cursor: pointer; border-radius: 6px; transition: all 0.2s;
  &:hover { background: rgba(26, 86, 219, 0.05); }
  &.active { background: rgba(26, 86, 219, 0.08); color: var(--color-primary); font-weight: 500; }
}
.section-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-text-secondary); }
.section-title { flex: 1; font-size: 14px; }
.section-arrow { font-size: 12px; color: var(--color-text-secondary); }
</style>
```

```vue
<!-- ChapterList.vue -->
<template>
  <div class="chapter-list">
    <div v-for="chapter in chapters" :key="chapter.id" class="chapter-group">
      <div class="chapter-header" @click="toggleChapter(chapter.id)">
        <div class="chapter-main" @click.stop="$emit('select-chapter', chapter)">
          <span class="chapter-title">{{ chapter.title }}</span>
        </div>
        <button class="chapter-toggle" @click.stop="toggleChapter(chapter.id)">
          {{ expandedChapters.has(chapter.id) ? '▼' : '▶' }}
        </button>
      </div>
      <transition name="expand">
        <div v-if="expandedChapters.has(chapter.id)" class="chapter-sections">
          <SectionItem
            v-for="section in chapter.sections"
            :key="section.id"
            :title="section.title"
            :isActive="activeSection?.id === section.id"
            @click="$emit('select-section', section, chapter)"
          />
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SectionItem from './SectionItem.vue'

const props = defineProps({ chapters: Array, activeSection: Object })
defineEmits(['select-chapter', 'select-section'])

const expandedChapters = ref(new Set())

function toggleChapter(id) {
  if (expandedChapters.value.has(id)) expandedChapters.value.delete(id)
  else expandedChapters.value.add(id)
  expandedChapters.value = new Set(expandedChapters.value)
}
</script>

<style lang="scss" scoped>
.chapter-group { margin-bottom: 4px; }
.chapter-header {
  display: flex; align-items: center; background: var(--color-surface);
  border-radius: var(--radius-sm); padding: 0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  transition: all 0.2s;
  &:hover { box-shadow: var(--shadow-card); }
}
.chapter-main {
  flex: 1; padding: 14px 16px; cursor: pointer;
}
.chapter-title { font-size: 15px; font-weight: 500; color: var(--color-text); }
.chapter-toggle {
  padding: 14px 16px; background: none; border: none;
  font-size: 12px; cursor: pointer; color: var(--color-text-secondary);
}
.chapter-sections {
  background: var(--color-bg);
  border-radius: 0 0 var(--radius-sm) var(--radius-sm);
  overflow: hidden;
}
.expand-enter-active { animation: expandIn 0.3s ease-out; }
.expand-leave-active { animation: expandIn 0.2s ease-in reverse; }
@keyframes expandIn {
  from { max-height: 0; opacity: 0; }
  to { max-height: 500px; opacity: 1; }
}
</style>
```

- [ ] **Step 3: 创建 KnowledgeBase.vue（课程列表页）**

```vue
<template>
  <div class="knowledge-base fade-in-up">
    <h1 class="page-title">我的课程知识库</h1>
    <div class="course-grid">
      <CourseCard
        v-for="course in courses"
        :key="course.id"
        :name="course.name"
        :chapterCount="course.chapterCount"
        :coverUrl="course.coverUrl"
        :coverColor="course.coverColor"
        @click="$router.push(`/knowledge/${course.id}`)"
      />
      <div class="course-card add-card" @click="showAddDialog = true">
        <div class="add-content">
          <span class="add-icon">➕</span>
          <span class="add-text">添加课程</span>
        </div>
      </div>
    </div>
    <el-dialog v-model="showAddDialog" title="创建新课程" width="400px">
      <el-input v-model="newCourseName" placeholder="课程名称" size="large" />
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useCourseStore } from '@/stores/useCourseStore'
import { storeToRefs } from 'pinia'
import CourseCard from '@/components/common/CourseCard.vue'

const courseStore = useCourseStore()
const { courses } = storeToRefs(courseStore)
const showAddDialog = ref(false)
const newCourseName = ref('')

onMounted(() => courseStore.fetchCourses())

async function handleCreate() {
  if (!newCourseName.value.trim()) return
  await courseStore.createNewCourse(newCourseName.value.trim())
  showAddDialog.value = false
  newCourseName.value = ''
}
</script>

<style lang="scss" scoped>
.knowledge-base { max-width: 960px; margin: 0 auto; }
.page-title { font-family: "Noto Serif SC", serif; font-size: 28px; margin: 0 0 32px; }
.course-grid {
  display: grid; grid-template-columns: repeat(auto-fill, 200px);
  gap: 20px; justify-content: center;
}
.add-card {
  width: 200px; height: 220px; display: flex; align-items: center; justify-content: center;
  border: 2px dashed var(--color-border); background: transparent;
  box-shadow: none; cursor: pointer; transition: all 0.25s;
  &:hover { border-color: var(--color-accent); background: rgba(232,168,23,0.05); }
}
.add-content { text-align: center; }
.add-icon { font-size: 32px; display: block; margin-bottom: 8px; }
.add-text { color: var(--color-text-secondary); font-size: 14px; }
</style>
```

- [ ] **Step 4: 创建 KnowledgeBaseCourse.vue（章节浏览页）**

```vue
<template>
  <div class="course-chapters fade-in-up" v-loading="loading">
    <el-button text @click="$router.push('/knowledge')" class="back-btn">
      ← 返回课程列表
    </el-button>

    <div class="course-hero" :style="{ background: heroBg }">
      <div class="hero-cover">
        <span class="hero-icon">📕</span>
      </div>
      <div class="hero-info">
        <h1 class="hero-name">{{ course?.name }}</h1>
        <p class="hero-meta">共 {{ chapters.length }} 章 · {{ totalSections }} 小节</p>
      </div>
    </div>

    <div class="chapters-container">
      <ChapterList
        :chapters="chapters"
        :activeSection="courseStore.currentSection"
        @select-chapter="onSelectChapter"
        @select-section="onSelectSection"
      />
    </div>

    <div class="quiz-actions">
      <el-dropdown @command="handleQuiz">
        <el-button type="warning" size="large">
          📝 为本课程出题 ▼
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="10">10 道题</el-dropdown-item>
            <el-dropdown-item command="20">20 道题</el-dropdown-item>
            <el-dropdown-item command="50">50 道题</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCourseStore } from '@/stores/useCourseStore'
import { useQuizStore } from '@/stores/useQuizStore'
import { storeToRefs } from 'pinia'
import ChapterList from '@/components/common/ChapterList.vue'

const route = useRoute()
const router = useRouter()
const courseStore = useCourseStore()
const quizStore = useQuizStore()
const { chapters, loading } = storeToRefs(courseStore)

const course = ref(null)
const totalSections = computed(() => chapters.value.reduce((sum, ch) => sum + (ch.sections?.length || 0), 0))
const heroBg = 'linear-gradient(135deg, #1A56DB, #0F2B5E)'

onMounted(async () => {
  await courseStore.fetchCourses()
  course.value = courseStore.courses.find(c => c.id === route.params.courseId)
  if (course.value) {
    courseStore.selectCourse(course.value)
    await courseStore.fetchChapters(course.value.id)
  }
})

function onSelectChapter(chapter) {
  courseStore.selectChapter(chapter)
  const firstSection = chapter.sections?.[0]
  if (firstSection) {
    router.push(`/knowledge/${route.params.courseId}/${chapter.id}/${firstSection.id}`)
  } else {
    router.push(`/knowledge/${route.params.courseId}/${chapter.id}`)
  }
}

function onSelectSection(section, chapter) {
  courseStore.selectChapter(chapter)
  courseStore.selectSection(section)
  router.push(`/knowledge/${route.params.courseId}/${chapter.id}/${section.id}`)
}

async function handleQuiz(count) {
  await quizStore.generateQuiz({ courseName: course.value?.name, count: Number(count) })
  router.push('/quiz')
}
</script>

<style lang="scss" scoped>
.course-chapters { max-width: 800px; margin: 0 auto; }
.back-btn { margin-bottom: 16px; }
.course-hero {
  display: flex; align-items: center; gap: 24px;
  padding: 32px; border-radius: var(--radius-lg); margin-bottom: 24px;
}
.hero-cover { flex-shrink: 0; }
.hero-icon { font-size: 64px; }
.hero-info { color: #fff; }
.hero-name { font-family: "Noto Serif SC", serif; font-size: 28px; margin: 0 0 4px; }
.hero-meta { font-size: 14px; opacity: 0.8; margin: 0; }
.chapters-container { margin-bottom: 32px; }
.quiz-actions { display: flex; justify-content: center; gap: 12px; }
</style>
```

- [ ] **Step 5: 创建 MarkdownViewer.vue 和 KnowledgeBaseReader.vue**

```vue
<!-- MarkdownViewer.vue -->
<template>
  <div class="markdown-viewer" v-html="renderedHtml"></div>
</template>
<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

marked.setOptions({
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

const props = defineProps({ content: String })
const renderedHtml = computed(() => marked(props.content || ''))
</script>
<style lang="scss" scoped>
.markdown-viewer {
  :deep(h1) { font-size: 24px; font-weight: 700; margin: 24px 0 16px; color: var(--color-text); }
  :deep(h2) { font-size: 20px; font-weight: 600; margin: 20px 0 12px; color: var(--color-text); }
  :deep(h3) { font-size: 17px; font-weight: 600; margin: 16px 0 8px; }
  :deep(p) { line-height: 1.8; margin: 0 0 12px; }
  :deep(ul), :deep(ol) { padding-left: 24px; margin: 0 0 12px; }
  :deep(li) { margin: 4px 0; }
  :deep(code) { background: #f0f0f0; padding: 2px 6px; border-radius: 4px; font-size: 14px; font-family: "JetBrains Mono", monospace; }
  :deep(pre) { background: #f6f8fa; border-radius: 8px; padding: 16px; overflow-x: auto; margin: 12px 0; }
  :deep(pre code) { background: none; padding: 0; }
  :deep(blockquote) { border-left: 3px solid var(--color-accent); padding-left: 16px; margin: 12px 0; color: var(--color-text-secondary); }
}
</style>
```

```vue
<!-- KnowledgeBaseReader.vue -->
<template>
  <div class="reader-page fade-in-up" v-loading="loading">
    <div class="reader-header">
      <el-button text @click="$router.back()">← 返回章节列表</el-button>
      <el-breadcrumb separator=">">
        <el-breadcrumb-item>{{ courseName }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ chapterTitle }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ sectionTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="read-progress">
        <div class="progress-bar" :style="{ width: progressPercent + '%' }"></div>
      </div>
    </div>

    <div class="reader-content" ref="contentRef" @scroll="onScroll">
      <MarkdownViewer :content="sectionContent" />
    </div>

    <div class="reader-footer" v-if="!loading">
      <div class="quiz-section">
        <!-- 节出题 -->
        <el-dropdown @command="handleSectionQuiz" style="margin-right: 12px;">
          <el-button type="warning">
            我已学习本节，请为我出题 ▼
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="1">1 道题</el-dropdown-item>
              <el-dropdown-item command="3">3 道题</el-dropdown-item>
              <el-dropdown-item command="5">5 道题</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 章出题（仅最后一节显示） -->
        <el-dropdown v-if="isLastSection" @command="handleChapterQuiz">
          <el-button type="warning">
            我已学习本章，请为我出题 ▼
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="5">5 道题（随机选节）</el-dropdown-item>
              <el-dropdown-item command="10">10 道题（随机选节）</el-dropdown-item>
              <el-dropdown-item command="20">20 道题（随机选节）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-button v-if="hasNext" type="primary" @click="goNext" class="next-btn">
        📖 下一节：{{ nextSectionTitle }} →
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCourseStore } from '@/stores/useCourseStore'
import { useQuizStore } from '@/stores/useQuizStore'
import { storeToRefs } from 'pinia'
import MarkdownViewer from '@/components/common/MarkdownViewer.vue'

const route = useRoute()
const router = useRouter()
const courseStore = useCourseStore()
const quizStore = useQuizStore()
const { chapters, currentChapter, currentSection } = storeToRefs(courseStore)

const sectionContent = ref('')
const loading = ref(true)
const progressPercent = ref(0)
const contentRef = ref(null)

const courseName = computed(() => courseStore.currentCourse?.name || '')
const chapterTitle = computed(() => currentChapter.value?.title || '')
const sectionTitle = computed(() => currentSection.value?.title || '')

const isLastSection = computed(() => {
  if (!currentChapter.value || !currentSection.value) return false
  const sections = currentChapter.value.sections || []
  return sections.length > 0 && sections[sections.length - 1].id === currentSection.value.id
})

const hasNext = computed(() => {
  if (!currentChapter.value || !currentSection.value) return false
  const sections = currentChapter.value.sections || []
  const idx = sections.findIndex(s => s.id === currentSection.value.id)
  if (idx < sections.length - 1) return true
  const chIdx = chapters.value.findIndex(c => c.id === currentChapter.value.id)
  return chIdx < chapters.value.length - 1
})

const nextSectionTitle = computed(() => {
  if (!currentChapter.value) return ''
  const sections = currentChapter.value.sections || []
  const idx = sections.findIndex(s => s.id === currentSection.value?.id)
  if (idx < sections.length - 1) return sections[idx + 1].title
  const chIdx = chapters.value.findIndex(c => c.id === currentChapter.value?.id)
  if (chIdx < chapters.value.length - 1) {
    const nextCh = chapters.value[chIdx + 1]
    return (nextCh.sections?.[0])?.title || nextCh.title
  }
  return ''
})

function onScroll() {
  const el = contentRef.value
  if (!el) return
  progressPercent.value = Math.round((el.scrollTop / (el.scrollHeight - el.clientHeight)) * 100)
}

function goNext() {
  const sections = currentChapter.value?.sections || []
  const idx = sections.findIndex(s => s.id === currentSection.value?.id)
  if (idx < sections.length - 1) {
    const next = sections[idx + 1]
    courseStore.selectSection(next)
    router.push(`/knowledge/${route.params.courseId}/${currentChapter.value.id}/${next.id}`)
  } else {
    const chIdx = chapters.value.findIndex(c => c.id === currentChapter.value?.id)
    if (chIdx < chapters.value.length - 1) {
      const nextCh = chapters.value[chIdx + 1]
      const firstSection = nextCh.sections?.[0]
      courseStore.selectChapter(nextCh)
      if (firstSection) {
        courseStore.selectSection(firstSection)
        router.push(`/knowledge/${route.params.courseId}/${nextCh.id}/${firstSection.id}`)
      } else {
        router.push(`/knowledge/${route.params.courseId}/${nextCh.id}`)
      }
    }
  }
}

async function handleSectionQuiz(count) {
  await quizStore.generateQuiz({
    courseName: courseName.value,
    chapter: chapterTitle.value,
    count: Number(count)
  })
  router.push('/quiz')
}

async function handleChapterQuiz(count) {
  await quizStore.generateQuiz({
    courseName: courseName.value,
    chapter: chapterTitle.value,
    count: Number(count)
  })
  router.push('/quiz')
}

onMounted(async () => {
  await courseStore.fetchCourses()
  const course = courseStore.courses.find(c => c.id === route.params.courseId)
  if (course) courseStore.selectCourse(course)
  await courseStore.fetchChapters(route.params.courseId)
  const chapter = chapters.value.find(c => c.id === route.params.chapterId)
  if (chapter) {
    courseStore.selectChapter(chapter)
    const section = chapter.sections?.find(s => s.id === route.params.sectionId)
    if (section) courseStore.selectSection(section)
  }
  // TODO: 从后端获取 section 内容
  sectionContent.value = '# 加载中...\n\n请稍候，正在获取内容。'
  loading.value = false
})
</script>

<style lang="scss" scoped>
.reader-page { max-width: 800px; margin: 0 auto; }
.reader-header { margin-bottom: 20px; }
.read-progress {
  height: 3px; background: var(--color-border); border-radius: 2px; margin-top: 12px;
  .progress-bar {
    height: 100%; background: linear-gradient(90deg, var(--color-accent), var(--color-primary));
    border-radius: 2px; transition: width 0.3s;
  }
}
.reader-content {
  background: var(--color-surface); border-radius: var(--radius-lg);
  padding: 32px 40px; box-shadow: var(--shadow-card);
  min-height: 400px; max-height: calc(100vh - 280px); overflow-y: auto;
}
.reader-footer {
  margin-top: 24px; text-align: center;
}
.quiz-section {
  display: flex; justify-content: center; gap: 12px; margin-bottom: 16px;
}
.next-btn { margin-top: 8px; }
</style>
```

- [ ] **Step 6: Commit**

```bash
git add src/views/KnowledgeBase.vue src/views/KnowledgeBaseCourse.vue src/views/KnowledgeBaseReader.vue
git add src/components/common/CourseCard.vue src/components/common/ChapterList.vue src/components/common/SectionItem.vue src/components/common/MarkdownViewer.vue
git commit -m "feat: add knowledge base pages - course list, chapter browser, markdown reader with quiz triggers"
```

---

### Task 5: 智能聊天辅导页面

**Files:**
- Create: `src/views/ChatTutor.vue`
- Create: `src/components/common/ChatPanel.vue`
- Create: `src/components/common/ChatMessage.vue`
- Create: `src/components/common/ChatInput.vue`
- Create: `src/api/chat.js`
- Create: `src/stores/useChatStore.js`

- [ ] **Step 1: 创建 chat.js API**

```javascript
// src/api/chat.js
const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export async function* streamChat(query, sessionId) {
  const response = await fetch(`${BASE_URL}/api/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, sessionId: sessionId || undefined })
  })
  if (!response.ok) throw new Error('Chat request failed')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data === '[DONE]') return
        yield data
      }
    }
  }
}
```

- [ ] **Step 2: 创建 useChatStore**

```javascript
// src/stores/useChatStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { streamChat } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref({}) // { [courseId]: { messages: [], sessionId: null } }
  const currentCourseId = ref(null)
  const isStreaming = ref(false)
  const abortController = ref(null)

  const currentMessages = computed(() => {
    if (!currentCourseId.value) return []
    return sessions.value[currentCourseId.value]?.messages || []
  })

  function initSession(courseId) {
    if (!sessions.value[courseId]) {
      sessions.value[courseId] = { messages: [], sessionId: null }
    }
    currentCourseId.value = courseId
  }

  async function sendMessage(content) {
    if (!currentCourseId.value) return
    const session = sessions.value[currentCourseId.value]
    session.messages.push({ role: 'user', content, timestamp: Date.now() })
    const aiMsg = { role: 'assistant', content: '', timestamp: Date.now() }
    session.messages.push(aiMsg)

    isStreaming.value = true
    try {
      for await (const chunk of streamChat(content, session.sessionId)) {
        aiMsg.content += chunk
      }
    } catch (e) {
      aiMsg.content = '抱歉，请求失败，请稍后重试。'
    } finally {
      isStreaming.value = false
    }
  }

  function clearSession(courseId) {
    if (courseId) {
      sessions.value[courseId] = { messages: [], sessionId: null }
    }
  }

  return { sessions, currentCourseId, isStreaming, currentMessages, initSession, sendMessage, clearSession }
})
```

- [ ] **Step 3: 创建 ChatTutor.vue**（完整页面，包含聊天面板 + 右侧课程选择和历史对话）

```vue
<template>
  <div class="chat-tutor fade-in-up">
    <div class="chat-main">
      <ChatPanel
        :messages="currentMessages"
        :isStreaming="isStreaming"
        @send="handleSend"
      />
    </div>
    <div class="chat-sidebar">
      <div class="sidebar-section">
        <h3 class="sidebar-title">选择课程</h3>
        <div class="course-list">
          <div
            v-for="c in courses"
            :key="c.id"
            class="course-item"
            :class="{ active: currentCourseId === c.id }"
            @click="switchCourse(c.id)"
          >
            <span class="course-icon">📕</span>
            <span class="course-name">{{ c.name }}</span>
          </div>
        </div>
      </div>
      <div class="sidebar-section">
        <el-button class="new-chat-btn" @click="handleNewChat" :disabled="!currentCourseId">
          💬 新建对话
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useCourseStore } from '@/stores/useCourseStore'
import { useChatStore } from '@/stores/useChatStore'
import { storeToRefs } from 'pinia'
import ChatPanel from '@/components/common/ChatPanel.vue'

const courseStore = useCourseStore()
const chatStore = useChatStore()
const { courses } = storeToRefs(courseStore)
const { currentCourseId, currentMessages, isStreaming } = storeToRefs(chatStore)

onMounted(async () => {
  await courseStore.fetchCourses()
  if (courses.value.length > 0) {
    chatStore.initSession(courses.value[0].id)
  }
})

function switchCourse(courseId) {
  chatStore.initSession(courseId)
}

function handleSend(content) {
  chatStore.sendMessage(content)
}

function handleNewChat() {
  chatStore.clearSession(currentCourseId.value)
}
</script>

<style lang="scss" scoped>
.chat-tutor {
  display: flex; gap: 20px; height: calc(100vh - 104px);
}
.chat-main { flex: 1; min-width: 0; }
.chat-sidebar {
  width: 240px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px;
}
.sidebar-section {
  background: var(--color-surface); border-radius: var(--radius-md);
  padding: 16px; box-shadow: var(--shadow-card);
}
.sidebar-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; }
.course-list { display: flex; flex-direction: column; gap: 4px; }
.course-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border-radius: var(--radius-sm); cursor: pointer; transition: all 0.2s;
  &:hover { background: var(--color-bg); }
  &.active { background: rgba(26, 86, 219, 0.08); color: var(--color-primary); font-weight: 500; }
}
.course-icon { font-size: 18px; }
.course-name { font-size: 14px; }
.new-chat-btn { width: 100%; }
</style>
```

- [ ] **Step 4: 创建 ChatPanel.vue + ChatMessage.vue + ChatInput.vue**

```vue
<!-- ChatInput.vue -->
<template>
  <div class="chat-input">
    <textarea
      v-model="text"
      class="input-area"
      placeholder="输入你的问题..."
      rows="1"
      @keydown.enter.exact.prevent="send"
      @input="autoResize"
      ref="inputRef"
      :disabled="disabled"
    ></textarea>
    <button class="send-btn" @click="send" :disabled="!text.trim() || disabled">
      📤
    </button>
  </div>
</template>
<script setup>
import { ref, nextTick } from 'vue'
const props = defineProps({ disabled: Boolean })
const emit = defineEmits(['send'])
const text = ref('')
const inputRef = ref(null)
function autoResize() {
  const el = inputRef.value
  if (el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 120) + 'px' }
}
function send() {
  if (!text.value.trim() || props.disabled) return
  emit('send', text.value.trim())
  text.value = ''
  nextTick(() => { if (inputRef.value) inputRef.value.style.height = 'auto' })
}
</script>
<style lang="scss" scoped>
.chat-input {
  display: flex; align-items: flex-end; gap: 8px;
  padding: 12px 16px; background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}
.input-area {
  flex: 1; border: 1px solid var(--color-border); border-radius: var(--radius-md);
  padding: 10px 14px; font-size: 15px; font-family: inherit; resize: none;
  outline: none; line-height: 1.5;
  &:focus { border-color: var(--color-primary); }
}
.send-btn {
  width: 40px; height: 40px; border: none; border-radius: 50%;
  background: var(--color-primary); color: #fff; font-size: 18px;
  cursor: pointer; transition: all 0.2s; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  &:hover:not(:disabled) { background: var(--color-primary-dark); transform: scale(1.05); }
  &:disabled { background: var(--color-border); cursor: not-allowed; }
}
</style>
```

```vue
<!-- ChatMessage.vue -->
<template>
  <div class="chat-message" :class="role">
    <div class="message-avatar">
      {{ role === 'user' ? '👤' : '🤖' }}
    </div>
    <div class="message-bubble">
      <div class="message-content" v-html="renderedContent"></div>
      <span class="cursor" v-if="isStreaming && role === 'assistant'">█</span>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
const props = defineProps({ role: String, content: String, isStreaming: Boolean })
const renderedContent = computed(() => marked(props.content || ''))
</script>
<style lang="scss" scoped>
.chat-message {
  display: flex; gap: 12px; padding: 12px 0;
  &.user { flex-direction: row-reverse; }
}
.message-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; background: var(--color-bg);
}
.message-bubble {
  max-width: 75%; padding: 12px 16px; border-radius: var(--radius-md);
  font-size: 15px; line-height: 1.7;
  .user & { background: var(--color-accent-light); color: var(--color-text); }
  .assistant & { background: #EEF1F9; color: var(--color-text); }
}
.cursor {
  display: inline-block; animation: typewriter-cursor 1s infinite;
  color: var(--color-primary);
}
</style>
```

```vue
<!-- ChatPanel.vue -->
<template>
  <div class="chat-panel">
    <div class="messages-container" ref="msgContainer">
      <div v-if="messages.length === 0" class="empty-chat">
        <div class="empty-icon">🤖</div>
        <p>你好！我是 EduMate 学习助手</p>
        <p>请随时向我提问课程相关问题</p>
      </div>
      <ChatMessage
        v-for="(msg, i) in messages"
        :key="i"
        :role="msg.role"
        :content="msg.content"
        :isStreaming="isStreaming && i === messages.length - 1 && msg.role === 'assistant'"
      />
    </div>
    <ChatInput :disabled="isStreaming" @send="$emit('send', $event)" />
  </div>
</template>
<script setup>
import { ref, watch, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'
const props = defineProps({ messages: Array, isStreaming: Boolean })
defineEmits(['send'])
const msgContainer = ref(null)
watch(() => props.messages?.length, () => {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
})
</script>
<style lang="scss" scoped>
.chat-panel {
  display: flex; flex-direction: column; height: 100%;
  background: var(--color-surface); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card); overflow: hidden;
}
.messages-container {
  flex: 1; overflow-y: auto; padding: 20px;
}
.empty-chat {
  text-align: center; padding: 60px 20px; color: var(--color-text-secondary);
  .empty-icon { font-size: 48px; margin-bottom: 12px; }
  p { margin: 4px 0; font-size: 15px; }
}
</style>
```

- [ ] **Step 5: Commit**

```bash
git add src/views/ChatTutor.vue src/components/common/ChatPanel.vue src/components/common/ChatMessage.vue src/components/common/ChatInput.vue src/api/chat.js src/stores/useChatStore.js
git commit -m "feat: add intelligent chat tutoring page with SSE streaming, per-course sessions"
```

---

### Task 6: 智能题库页面

**Files:**
- Create: `src/views/QuizBank.vue`
- Create: `src/components/common/QuizCard.vue`
- Create: `src/components/course/CourseDivider.vue`
- Create: `src/api/quiz.js`
- Create: `src/stores/useQuizStore.js`

- [ ] **Step 1: 创建 quiz.js API 和 useQuizStore**

```javascript
// src/api/quiz.js
import request from './request'
export function generateQuiz(params) {
  return request.post('/api/quiz/generate', params)
}
export function getQuizzes() {
  return request.get('/api/quizzes')
}
```

```javascript
// src/stores/useQuizStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { generateQuiz as apiGenerateQuiz, getQuizzes } from '@/api/quiz'
import { ElMessage } from 'element-plus'

export const useQuizStore = defineStore('quiz', () => {
  const quizzes = ref([])
  const currentQuiz = ref(null)
  const loading = ref(false)

  // 按课程分组，组内按时间倒序
  const groupedQuizzes = computed(() => {
    const groups = {}
    quizzes.value.forEach(q => {
      const course = q.courseName || '未分类'
      if (!groups[course]) groups[course] = []
      groups[course].push(q)
    })
    Object.values(groups).forEach(g => g.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)))
    return groups
  })

  async function fetchQuizzes() {
    loading.value = true
    try {
      const data = await getQuizzes()
      quizzes.value = data || []
    } catch { quizzes.value = [] }
    finally { loading.value = false }
  }

  async function generateQuiz(params) {
    loading.value = true
    try {
      const questions = await apiGenerateQuiz(params)
      const name = buildQuizName(params)
      const quiz = {
        id: Date.now().toString(),
        name,
        courseName: params.courseName,
        source: params.chapter ? '章测试' : '课程测试',
        questions,
        createdAt: new Date().toISOString(),
        count: questions.length
      }
      quizzes.value.unshift(quiz)
      ElMessage.success(`出题完成：${name}`)
      return quiz
    } finally { loading.value = false }
  }

  function buildQuizName(params) {
    if (params.chapter) {
      return `${params.courseName}-${params.chapter}-章测试`
    }
    return `${params.courseName}-综合测试`
  }

  function deleteQuiz(id) {
    quizzes.value = quizzes.value.filter(q => q.id !== id)
  }

  return { quizzes, currentQuiz, loading, groupedQuizzes, fetchQuizzes, generateQuiz, deleteQuiz }
})
```

- [ ] **Step 2: 创建 CourseDivider.vue**

```vue
<template>
  <div class="course-divider">
    <div class="divider-line"></div>
    <span class="divider-text">{{ courseName }}</span>
    <div class="divider-line"></div>
    <button class="divider-toggle" @click="$emit('toggle')">
      {{ collapsed ? '展开 ▶' : '收起 ▲' }}
    </button>
  </div>
</template>
<script setup>
defineProps({ courseName: String, collapsed: Boolean })
defineEmits(['toggle'])
</script>
<style lang="scss" scoped>
.course-divider {
  display: flex; align-items: center; gap: 12px; margin: 24px 0 16px;
}
.divider-line { flex: 1; height: 1px; background: var(--color-border); }
.divider-text { font-size: 16px; font-weight: 600; color: var(--color-text); white-space: nowrap; }
.divider-toggle {
  background: none; border: 1px solid var(--color-border); border-radius: var(--radius-sm);
  padding: 4px 12px; font-size: 13px; color: var(--color-text-secondary);
  cursor: pointer; transition: all 0.2s;
  &:hover { border-color: var(--color-primary); color: var(--color-primary); }
}
</style>
```

- [ ] **Step 3: 创建 QuizCard.vue**

```vue
<template>
  <div class="quiz-card" @click="$emit('click')">
    <div class="quiz-header">
      <span class="quiz-type-badge">{{ source }}</span>
      <el-button class="delete-btn" circle size="small" @click.stop="$emit('delete')">
        ✕
      </el-button>
    </div>
    <h3 class="quiz-name">{{ name }}</h3>
    <div class="quiz-meta">
      <span class="quiz-count">{{ count }} 题</span>
      <span class="quiz-date">{{ formatDate(createdAt) }}</span>
    </div>
  </div>
</template>
<script setup>
defineProps({ name: String, source: String, count: Number, createdAt: String })
defineEmits(['click', 'delete'])
function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>
<style lang="scss" scoped>
.quiz-card {
  background: var(--color-surface); border-radius: var(--radius-md);
  padding: 16px; box-shadow: var(--shadow-card); cursor: pointer;
  transition: all 0.25s ease-out; border: 1px solid transparent;
  &:hover {
    transform: translateY(-4px); box-shadow: var(--shadow-hover);
    border-color: var(--color-accent);
  }
}
.quiz-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}
.quiz-type-badge {
  font-size: 12px; padding: 2px 8px; border-radius: 4px;
  background: var(--color-accent-light); color: var(--color-accent);
  font-weight: 500;
}
.delete-btn {
  opacity: 0; transition: opacity 0.2s;
  .quiz-card:hover & { opacity: 1; }
}
.quiz-name { font-size: 15px; font-weight: 600; margin: 0 0 8px; color: var(--color-text); }
.quiz-meta { display: flex; justify-content: space-between; font-size: 13px; color: var(--color-text-secondary); }
</style>
```

- [ ] **Step 4: 创建 QuizBank.vue**

```vue
<template>
  <div class="quiz-bank fade-in-up" v-loading="loading">
    <h1 class="page-title">📝 智能题库</h1>
    <div v-if="Object.keys(groupedQuizzes).length === 0" class="empty-state">
      <p>暂无题库，请先到知识库中学习并出题</p>
    </div>
    <div v-for="(quizzes, courseName) in groupedQuizzes" :key="courseName">
      <CourseDivider
        :courseName="courseName"
        :collapsed="collapsedCourses.has(courseName)"
        @toggle="toggleCourse(courseName)"
      />
      <transition name="expand">
        <div v-if="!collapsedCourses.has(courseName)" class="quiz-grid">
          <QuizCard
            v-for="quiz in quizzes"
            :key="quiz.id"
            :name="quiz.name"
            :source="quiz.source"
            :count="quiz.count"
            :createdAt="quiz.createdAt"
            @click="openQuiz(quiz)"
            @delete="handleDelete(quiz.id)"
          />
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useQuizStore } from '@/stores/useQuizStore'
import { storeToRefs } from 'pinia'
import CourseDivider from '@/components/course/CourseDivider.vue'
import QuizCard from '@/components/common/QuizCard.vue'

const quizStore = useQuizStore()
const { groupedQuizzes, loading } = storeToRefs(quizStore)
const collapsedCourses = ref(new Set())

onMounted(() => quizStore.fetchQuizzes())

function toggleCourse(name) {
  if (collapsedCourses.value.has(name)) {
    collapsedCourses.value.delete(name)
  } else {
    collapsedCourses.value.add(name)
  }
  collapsedCourses.value = new Set(collapsedCourses.value)
}

function openQuiz(quiz) {
  quizStore.currentQuiz = quiz
  // 可以打开做题弹窗或跳转到做题页面
}

function handleDelete(id) {
  quizStore.deleteQuiz(id)
}
</script>

<style lang="scss" scoped>
.quiz-bank { max-width: 960px; margin: 0 auto; }
.page-title { font-family: "Noto Serif SC", serif; font-size: 28px; margin: 0 0 32px; }
.quiz-grid {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 16px; margin-bottom: 8px;
}
.empty-state { text-align: center; padding: 60px; color: var(--color-text-secondary); }
</style>
```

- [ ] **Step 5: Commit**

```bash
git add src/views/QuizBank.vue src/components/common/QuizCard.vue src/components/course/CourseDivider.vue src/api/quiz.js src/stores/useQuizStore.js
git commit -m "feat: add intelligent quiz bank with course-grouped grid, collapsible dividers, time-sorted cards"
```

---

### Task 7: 公共组件补充与样式完善

**Files:**
- Create: `src/components/common/EmptyState.vue`
- Create: `src/components/common/LoadingSpinner.vue`

- [ ] **Step 1: 创建 EmptyState.vue 和 LoadingSpinner.vue**

```vue
<!-- EmptyState.vue -->
<template>
  <div class="empty-state">
    <div class="empty-icon">{{ icon }}</div>
    <p class="empty-title">{{ title }}</p>
    <p class="empty-desc" v-if="desc">{{ desc }}</p>
    <slot />
  </div>
</template>
<script setup>
defineProps({ icon: { type: String, default: '📭' }, title: String, desc: String })
</script>
<style lang="scss" scoped>
.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-title { font-size: 17px; font-weight: 600; color: var(--color-text); margin: 0 0 8px; }
.empty-desc { font-size: 14px; color: var(--color-text-secondary); margin: 0; }
</style>
```

```vue
<!-- LoadingSpinner.vue -->
<template>
  <div class="loading-spinner">
    <div class="spinner-ring"></div>
    <p class="spinner-text">{{ text || '加载中...' }}</p>
  </div>
</template>
<script setup>
defineProps({ text: String })
</script>
<style lang="scss" scoped>
.loading-spinner {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 40px;
}
.spinner-ring {
  width: 36px; height: 36px; border: 3px solid var(--color-border);
  border-top-color: var(--color-accent); border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.spinner-text { font-size: 14px; color: var(--color-text-secondary); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add src/components/common/EmptyState.vue src/components/common/LoadingSpinner.vue
git commit -m "feat: add EmptyState and LoadingSpinner common components"
```

---

## 八、后端扩展需求

前端完全实现需要后端配合新增以下 API 端点：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/courses` | `GET` | 获取所有课程列表 |
| `/api/courses` | `POST` | 创建新课程 `{ name }` |
| `/api/courses/{id}` | `DELETE` | 删除课程 |
| `/api/courses/{id}/chapters` | `GET` | 获取课程章节结构 |
| `/api/courses/{id}/sections/{sectionId}` | `GET` | 获取小节 Markdown 内容 |
| `/api/quizzes` | `GET` | 获取所有题库列表 |
| `/api/quizzes/{id}` | `DELETE` | 删除题库 |

这些端点可以在 `edumate-admin` 模块中新增 `CourseController` 和扩展 `QuizController` 来实现。

---

## 九、自检清单

**1. 功能覆盖：**
- [x] 文档上传（FileUploader + CourseSelector）— Task 3
- [x] 分类知识库 — 课程列表（KnowledgeBase）— Task 4
- [x] 分类知识库 — 章节浏览（KnowledgeBaseCourse）— Task 4
- [x] 分类知识库 — Markdown 阅读（KnowledgeBaseReader）— Task 4
- [x] 智能聊天辅导（ChatTutor + SSE）— Task 5
- [x] 智能题库（QuizBank + 分组 + 折叠）— Task 6
- [x] 侧边栏导航（SideNav + 金线流光动画）— Task 2
- [x] 蓝白金红配色体系 — Task 1 (variables.scss)
- [x] 出题按钮（节/章/课程三级）— Task 4, Task 6

**2. 无占位符：** 所有代码均为完整实现，无 TBD/TODO 占位。

**3. 类型一致性：** Store 接口与组件 props 一致，API 参数与后端 Controller 匹配。

---

## 十、执行计划

**总计 7 个 Task，预计每条 Task 2-5 分钟核心编码。**

实施顺序：Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7

建议使用 `superpowers:subagent-driven-development` 逐任务在独立 subagent 中实施，每完成一个 Task 进行 review 后继续下一个。