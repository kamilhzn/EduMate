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