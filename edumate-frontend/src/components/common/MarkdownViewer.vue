<template>
  <div class="markdown-viewer" v-html="renderedHtml"></div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

const props = defineProps({
  content: { type: String, default: '' }
})

const renderedHtml = computed(() => {
  if (!props.content) return ''
  return marked(props.content)
})
</script>

<style lang="scss" scoped>
.markdown-viewer {
  line-height: 1.8;

  :deep(h1) {
    font-size: 24px;
    font-weight: 700;
    margin: 24px 0 16px;
    color: var(--color-text);
    padding-bottom: 8px;
    border-bottom: 1px solid var(--color-border);
  }

  :deep(h2) {
    font-size: 20px;
    font-weight: 600;
    margin: 20px 0 12px;
    color: var(--color-text);
  }

  :deep(h3) {
    font-size: 17px;
    font-weight: 600;
    margin: 16px 0 8px;
  }

  :deep(p) {
    margin: 0 0 12px;
  }

  :deep(ul), :deep(ol) {
    padding-left: 24px;
    margin: 0 0 12px;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(code) {
    background: #f0f0f0;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 14px;
    font-family: "JetBrains Mono", "Fira Code", monospace;
    color: #d94040;
  }

  :deep(pre) {
    background: #f6f8fa;
    border-radius: 8px;
    padding: 16px;
    overflow-x: auto;
    margin: 12px 0;
    border: 1px solid var(--color-border);

    code {
      background: none;
      padding: 0;
      color: inherit;
      font-size: 14px;
    }
  }

  :deep(blockquote) {
    border-left: 3px solid var(--color-accent);
    padding-left: 16px;
    margin: 12px 0;
    color: var(--color-text-secondary);
    background: var(--color-accent-light);
    padding: 12px 16px;
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
  }

  :deep(th), :deep(td) {
    border: 1px solid var(--color-border);
    padding: 8px 12px;
    text-align: left;
  }

  :deep(th) {
    background: var(--color-bg);
    font-weight: 600;
  }

  :deep(a) {
    color: var(--color-primary);
    text-decoration: underline;
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--color-border);
    margin: 24px 0;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: var(--radius-sm);
    margin: 12px 0;
  }
}
</style>