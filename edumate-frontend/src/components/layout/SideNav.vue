<template>
  <nav class="side-nav" :class="{ collapsed: sidebarCollapsed }">
    <div class="nav-brand">
      <div class="brand-icon">
        <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="#E8A817" opacity="0.15"/>
          <path d="M8 10h16M8 16h16M8 22h16" stroke="#E8A817" stroke-width="2" stroke-linecap="round"/>
          <path d="M12 10v12M20 10v12" stroke="#E8A817" stroke-width="1.5" stroke-linecap="round" opacity="0.5"/>
        </svg>
      </div>
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
      <div class="user-avatar">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="8" r="4"/>
          <path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/>
        </svg>
      </div>
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
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--sidebar-width);
  background: linear-gradient(180deg, #0F2B5E 0%, #0A1F45 100%);
  display: flex;
  flex-direction: column;
  padding: 20px 0;
  transition: width var(--transition-normal);
  z-index: 100;
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.06);

  &.collapsed {
    width: var(--sidebar-collapsed);
  }
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px 32px;
  .brand-icon {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .brand-text {
    font-size: 22px;
    font-weight: 700;
    font-family: "Noto Serif SC", serif;
    color: var(--color-accent);
    letter-spacing: 1px;
    white-space: nowrap;
  }
}

.nav-items {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 12px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 16px;
  font-weight: 400;
  transition: all var(--transition-bounce);
  overflow: hidden;
  white-space: nowrap;

  &:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.06);
  }

  &.active {
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    background: rgba(255, 255, 255, 0.1);
    transform: scale(1.05);

    .nav-indicator {
      height: 28px;
    }
  }

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(
      90deg,
      transparent 0%,
      var(--color-accent) 50%,
      transparent 100%
    );
    background-size: 200% 100%;
    opacity: 0;
    transition: opacity 0.3s;
  }

  &.active::after {
    opacity: 1;
    animation: goldShimmer 0.6s ease-in-out;
  }
}

.nav-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.nav-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: var(--color-accent);
  border-radius: 0 2px 2px 0;
  transition: height 0.35s var(--transition-bounce);
}

.nav-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin: 0 12px;

  .user-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: rgba(232, 168, 23, 0.2);
    color: var(--color-accent);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .user-name {
    color: rgba(255, 255, 255, 0.6);
    font-size: 14px;
    white-space: nowrap;
  }
}
</style>