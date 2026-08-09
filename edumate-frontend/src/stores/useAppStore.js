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