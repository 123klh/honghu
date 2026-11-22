<template>
  <div class="workspace" :class="{ 'is-collapsed': collapsed, 'has-tabbar': tabs.length }">
    <a class="skip-link" href="#workspace-content">跳转至主要内容</a>

    <aside class="workspace-sidebar" aria-label="侧边导航">
      <router-link :to="homePath" class="sidebar-brand" aria-label="波波公考首页">
        <BrandLogo :compact="collapsed" />
      </router-link>
      <span v-if="manager && !collapsed" class="sidebar-role">管理端</span>
      <nav class="sidebar-nav" aria-label="主导航">
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <p v-if="!collapsed" class="group-title">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            :aria-label="item.label"
            :aria-current="activePath === item.to ? 'page' : undefined"
            :title="collapsed ? item.label : undefined"
            class="nav-link"
            :class="{ active: activePath === item.to }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="!collapsed">{{ item.label }}</span>
          </router-link>
        </div>
      </nav>
    </aside>

    <div class="workspace-body">
      <header class="workspace-header">
        <button
          type="button"
          class="icon-button desktop-toggle"
          :aria-label="collapsed ? '展开侧边导航' : '收起侧边导航'"
          :aria-expanded="!collapsed"
          @click="collapsed = !collapsed"
        >
          <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
        </button>
        <button
          type="button"
          class="icon-button mobile-toggle"
          aria-label="打开全部导航"
          :aria-expanded="mobileOpen"
          @click="mobileOpen = true"
        >
          <el-icon><Menu /></el-icon>
        </button>
        <div class="header-location">
          <span class="location-root">{{ manager ? '管理工作台' : '学习工作台' }}</span>
          <span class="location-divider" aria-hidden="true">/</span>
          <span class="location-current">{{ currentTitle }}</span>
        </div>
        <ThemeSwitcher />
        <el-dropdown class="header-account" trigger="click" @command="$emit('command', $event)">
          <button type="button" class="user-trigger" :aria-label="`${displayName}，打开账户菜单`">
            <span class="user-avatar" aria-hidden="true">{{ displayName?.slice(0, 1)?.toUpperCase() || '学' }}</span>
            <span class="user-name">{{ displayName }}</span>
            <el-icon class="user-chevron"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="manager && roleLabel" disabled>管理员 · {{ roleLabel }}</el-dropdown-item>
              <el-dropdown-item v-if="!manager" command="favorites">我的收藏</el-dropdown-item>
              <el-dropdown-item v-if="!manager" command="history">考试记录</el-dropdown-item>
              <el-dropdown-item command="logout" :divided="!manager || !!roleLabel">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main id="workspace-content" class="workspace-main" tabindex="-1"><slot /></main>
    </div>

    <el-drawer
      v-model="mobileOpen"
      direction="ltr"
      size="240px"
      :with-header="false"
      class="hh-nav-drawer"
      aria-label="全部导航"
      append-to-body
    >
      <div class="drawer-heading">
        <router-link :to="homePath" class="drawer-brand" @click="mobileOpen = false"><BrandLogo /></router-link>
        <button type="button" class="icon-button" aria-label="关闭导航" @click="mobileOpen = false">
          <el-icon><Close /></el-icon>
        </button>
      </div>
      <nav class="drawer-nav" aria-label="全部页面">
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <p class="group-title">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            :aria-current="activePath === item.to ? 'page' : undefined"
            class="nav-link"
            :class="{ active: activePath === item.to }"
            @click="mobileOpen = false"
          >
            <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>
    </el-drawer>

    <nav v-if="tabs.length" class="workspace-tabbar" aria-label="快捷导航">
      <router-link
        v-for="tab in tabs"
        :key="tab.to"
        :to="tab.to"
        :aria-current="tabActive(tab.to) ? 'page' : undefined"
        :class="{ active: tabActive(tab.to) }"
      >
        <el-icon><component :is="tab.icon" /></el-icon><span>{{ tab.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, Close, Expand, Fold, Menu } from '@element-plus/icons-vue'
import BrandLogo from './BrandLogo.vue'
import ThemeSwitcher from './ThemeSwitcher.vue'

const props = defineProps({
  groups: { type: Array, required: true },
  tabs: { type: Array, default: () => [] },
  displayName: { type: String, default: '' },
  roleLabel: { type: String, default: '' },
  manager: { type: Boolean, default: false }
})
defineEmits(['command'])

const route = useRoute()
const collapsed = ref(false)
const mobileOpen = ref(false)
const homePath = computed(() => props.manager ? '/admin' : '/')
const items = computed(() => props.groups.flatMap(group => group.items))
const currentItem = computed(() => items.value
  .filter(item => route.path === item.to || (item.to !== '/' && route.path.startsWith(item.to + '/')))
  .sort((a, b) => b.to.length - a.to.length)[0])
const activePath = computed(() => currentItem.value?.to)
const detailTitles = {
  'practice-session': '刷题练习',
  'exam-run': '模拟考试',
  'shenlun-detail': '申论作答',
  'community-detail': '讨论详情'
}
const currentTitle = computed(() => detailTitles[route.name] || currentItem.value?.label || '首页')

function tabActive(path) {
  return route.path === path || (path !== '/' && route.path.startsWith(path + '/'))
}

watch(() => route.fullPath, () => { mobileOpen.value = false })
</script>

<style scoped>
.workspace { --sidebar-width: 232px; min-height: 100vh; background: var(--hh-bg, #f7f9fc); }
.workspace.is-collapsed { --sidebar-width: 80px; }
.skip-link { position: fixed; top: 8px; left: 50%; transform: translate(-50%, -150%); z-index: 3000; padding: 10px 16px; background: #fff; color: #646cff; border-radius: 8px; }
.skip-link:focus { transform: translate(-50%, 0); }
.workspace-sidebar { position: fixed; inset: 0 auto 0 0; width: var(--sidebar-width); z-index: 40; display: flex; flex-direction: column; background: #fff; border-right: 1px solid #edf0f6; transition: width 180ms ease; }
.sidebar-brand { min-height: 72px; display: flex; align-items: center; padding: 0 22px; color: inherit; text-decoration: none; overflow: hidden; }
.sidebar-role { margin: -6px 22px 10px; color: #8491a5; font-size: 12px; }
.sidebar-nav { flex: 1; overflow-y: auto; padding: 6px 12px 22px; }
.nav-group + .nav-group { margin-top: 20px; }
.group-title { margin: 0 12px 9px; font-size: 11px; font-weight: 600; color: #8b96a8; letter-spacing: .08em; }
.nav-link { display: flex; align-items: center; gap: 12px; min-height: 44px; padding: 11px 13px; margin: 3px 0; border-radius: 9px; text-decoration: none; color: #66748a; font-size: 14px; line-height: 22px; transition: background 140ms ease, color 140ms ease; }
.nav-link .el-icon { flex-shrink: 0; font-size: 19px; }
.nav-link:hover { color: #646cff; background: #f6f7ff; }
.nav-link.active { color: #646cff; background: #eeefff; font-weight: 600; }
.nav-link:focus-visible, .icon-button:focus-visible, .user-trigger:focus-visible { outline: 2px solid #646cff; outline-offset: 3px; }
.is-collapsed .sidebar-brand { padding: 0; justify-content: center; }
.is-collapsed .nav-link { justify-content: center; padding: 11px 0; }
.workspace-body { margin-left: var(--sidebar-width); min-width: 0; transition: margin-left 180ms ease; }
.workspace-header { position: sticky; top: 0; height: 64px; z-index: 30; display: flex; align-items: center; gap: 18px; padding: 0 24px; background: rgba(255, 255, 255, .96); border-bottom: 1px solid #edf0f6; }
.icon-button { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 8px; background: transparent; color: #778299; cursor: pointer; }
.icon-button .el-icon { font-size: 20px; }
.icon-button:hover { color: #646cff; background: #eeefff; }
.mobile-toggle { display: none; }
.header-location { display: flex; align-items: center; gap: 12px; min-width: 0; font-size: 13px; }
.location-root { color: #8491a5; white-space: nowrap; }
.location-divider { color: #c3cad5; }
.location-current { color: #273044; font-weight: 500; white-space: nowrap; }
.header-account { margin-left: auto; min-width: 0; }
.user-trigger { display: flex; align-items: center; gap: 10px; padding: 4px 0 4px 6px; border: 0; background: transparent; cursor: pointer; color: #5f6d83; font: inherit; font-size: 13px; border-radius: 8px; }
.user-avatar { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 34px; height: 34px; border-radius: 50%; background: #eeefff; color: #646cff; font-size: 15px; font-weight: 600; }
.user-name { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-chevron { font-size: 12px; color: #99a2b2; }
.workspace-main { max-width: 1560px; min-width: 0; margin: 0 auto; padding: 24px; outline: none; }
.workspace-tabbar { display: none; }
.drawer-heading { display: flex; align-items: center; justify-content: space-between; padding: 20px 14px 18px 20px; border-bottom: 1px solid #edf0f6; }
.drawer-brand { color: inherit; text-decoration: none; }
.drawer-nav { padding: 20px 12px; }
:global(.hh-nav-drawer .el-drawer__body) { padding: 0; }
@media (max-width: 900px) {
  .workspace-sidebar { display: none; }
  .workspace-body { margin-left: 0; }
  .desktop-toggle { display: none; }
  .mobile-toggle { display: inline-flex; }
  .workspace-header { gap: 12px; padding: 0 16px; }
  .workspace-main { padding: 20px 16px; }
}
@media (max-width: 768px) {
  .workspace-header { height: 60px; padding: 0 14px; }
  .location-root, .location-divider { display: none; }
  .location-current { font-size: 14px; }
  .workspace-main { padding: 16px 12px; }
  .has-tabbar .workspace-main { padding-bottom: calc(90px + env(safe-area-inset-bottom)); }
  .user-name { max-width: 110px; }
  .workspace-tabbar { position: fixed; inset: auto 0 0; z-index: 50; display: flex; padding: 9px 6px calc(9px + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid #edf0f6; box-shadow: 0 -4px 18px rgba(44, 60, 92, .03); }
  .workspace-tabbar a { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 4px 0; border-radius: 9px; text-decoration: none; color: #8b96a8; font-size: 11px; line-height: 15px; }
  .workspace-tabbar .el-icon { font-size: 20px; }
  .workspace-tabbar a.active { color: #646cff; }
  .workspace-tabbar a:focus-visible { outline: 2px solid #646cff; outline-offset: -1px; }
}
@media (max-width: 420px) { .user-name { display: none; } .header-location { flex: 1; } }
@media (prefers-reduced-motion: reduce) { .workspace-sidebar, .workspace-body, .nav-link { transition: none; } }
</style>
