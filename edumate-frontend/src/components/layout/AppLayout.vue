<template>
  <div class="app-layout">
    <SideNav />
    <div class="main-area" :class="{ collapsed: appStore.sidebarCollapsed }">
      <TopBar />
      <main class="main-content">
        <router-view v-slot="{ Component, route }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
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
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.main-area {
  margin-left: var(--sidebar-width);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  transition: margin-left var(--transition-normal);

  &.collapsed {
    margin-left: var(--sidebar-collapsed);
  }
}

.main-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  background: var(--color-bg);
}

/* 页面切换动画 */
.page-enter-active {
  animation: fadeInUp 0.35s ease-out;
}

.page-leave-active {
  animation: fadeIn 0.2s ease-in reverse;
}
</style>