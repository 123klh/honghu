import http from './http'

// ---------- 认证 ----------
export const authApi = {
  loginUser: (userName, password) => http.post('/auth/user/login', { userName, password }),
  loginManager: (userName, password) => http.post('/auth/manager/login', { userName, password }),
  register: (data) => http.post('/auth/user/register', data),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/user/me')
}

// ---------- 练习 ----------
export const practiceApi = {
  startModule: (questionType, count) => http.post('/practice/module/start', { questionType, count }),
  startPersonalized: (mode, count) => http.post('/practice/personalized/start', { mode, count }),
  submit: (questionId, userAnswer, duration) =>
    http.post('/practice/submit', { questionId, userAnswer, duration }),
  wrongList: () => http.get('/practice/wrong'),
  startWrong: (count) => http.post('/practice/wrong/start', { count }),
  markMastered: (questionId) => http.post(`/practice/wrong/${questionId}/mastered`),
  wrongStats: () => http.get('/practice/wrong/stats')
}

// ---------- 考试 ----------
export const examApi = {
  list: () => http.get('/exams/papers'),
  papers: () => http.get('/exams/papers'),
  start: (paperId) => http.post(`/exams/papers/${paperId}/start`),
  submit: (examId, answers, duration) => http.post(`/exams/${examId}/submit`, { answers, duration }),
  history: () => http.get('/exams/history')
}

// ---------- 题目 ----------
export const questionApi = {
  get: (id) => http.get(`/questions/${id}`),
  adminList: (params) => http.get('/admin/questions', { params }),
  adminSearch: (params) => http.get('/admin/questions', { params }),
  adminCreate: (q) => http.post('/admin/questions', q),
  adminUpdate: (id, q) => http.put(`/admin/questions/${id}`, q),
  adminDelete: (id) => http.delete(`/admin/questions/${id}`)
}

// ---------- 申论 ----------
export const shenlunApi = {
  list: (params) => http.get('/shenlun/questions', { params }),
  detail: (id) => http.get(`/shenlun/questions/${id}`),
  score: (id, answer) => http.post(`/shenlun/questions/${id}/score`, { answer })
}

// ---------- 收藏 ----------
export const favoriteApi = {
  addQuestion: (id, body) => http.post(`/favorites/questions/${id}`, body || {}),
  removeQuestion: (id) => http.delete(`/favorites/questions/${id}`),
  questions: () => http.get('/favorites/questions'),
  addPaper: (id, body) => http.post(`/favorites/papers/${id}`, body || {}),
  removePaper: (id) => http.delete(`/favorites/papers/${id}`),
  papers: () => http.get('/favorites/papers'),
  addFr: (id, body) => http.post(`/favorites/frquestions/${id}`, body || {}),
  removeFr: (id) => http.delete(`/favorites/frquestions/${id}`),
  frs: () => http.get('/favorites/frquestions')
}

// ---------- 社区 ----------
export const communityApi = {
  list: (params) => http.get('/community/discussions', { params }),
  adminList: (params) => http.get('/community/discussions', { params }),
  detail: (id) => http.get(`/community/discussions/${id}`),
  create: (data) => http.post('/community/discussions', data),
  remove: (id) => http.delete(`/community/discussions/${id}`),
  reply: (id, content) => http.post(`/community/discussions/${id}/replies`, { content }),
  like: (replyId) => http.post(`/community/replies/${replyId}/like`),
  accept: (replyId) => http.post(`/community/replies/${replyId}/accept`),
  removeReply: (replyId) => http.delete(`/community/replies/${replyId}`),
  adminPin: (id, pinned) => http.put(`/community/admin/discussions/${id}/pin`, null, { params: { pinned } }),
  adminRemove: (id) => http.delete(`/community/admin/discussions/${id}`)
}

// ---------- 统计 ----------
export const statsApi = {
  me: (days = 30) => http.get('/stats/me', { params: { days } }),
  adminOverview: (days = 30) => http.get('/stats/admin/overview', { params: { days } })
}
