import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/UserLayout.vue'),
    meta: { role: 'user' },
    children: [
      { path: '', name: 'home', component: () => import('../views/user/Home.vue') },
      { path: 'practice', name: 'practice', component: () => import('../views/user/Practice.vue') },
      { path: 'practice/session', name: 'practice-session', component: () => import('../views/user/PracticeSession.vue') },
      { path: 'exams', name: 'exams', component: () => import('../views/user/Exams.vue') },
      { path: 'exams/:examId/run', name: 'exam-run', component: () => import('../views/user/ExamRun.vue') },
      { path: 'exams/history', name: 'exam-history', component: () => import('../views/user/Exams.vue'), props: { initialTab: 'history' } },
      { path: 'shenlun', name: 'shenlun', component: () => import('../views/user/Shenlun.vue') },
      { path: 'shenlun/:id', name: 'shenlun-detail', component: () => import('../views/user/ShenlunDetail.vue') },
      { path: 'wrong', name: 'wrong', component: () => import('../views/user/WrongBook.vue') },
      { path: 'favorites', name: 'favorites', component: () => import('../views/user/Favorites.vue') },
      { path: 'community', name: 'community', component: () => import('../views/user/Community.vue') },
      { path: 'community/:id', name: 'community-detail', component: () => import('../views/user/CommunityDetail.vue') },
      { path: 'stats', name: 'stats', component: () => import('../views/user/Stats.vue') }
    ]
  },
  {
    path: '/admin',
    component: () => import('../views/AdminLayout.vue'),
    meta: { role: 'manager' },
    children: [
      { path: '', name: 'admin-home', component: () => import('../views/admin/Overview.vue') },
      { path: 'questions', name: 'admin-questions', component: () => import('../views/admin/Questions.vue') },
      { path: 'community', name: 'admin-community', component: () => import('../views/admin/Community.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

/**
 * 路由守卫：未登录去登录页；user 令牌进不了 /admin；manager 令牌进不了用户页
 * （后端 requireUserId 也会拒绝，这里只是提前拦截给出友好跳转）。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }
  const need = to.matched.find((r) => r.meta.role)?.meta.role
  if (need === 'manager' && !auth.isManager) return { name: 'home' }
  if (need === 'user' && auth.isManager) return { name: 'admin-home' }
  return true
})

export default router
