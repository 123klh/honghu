<template>
  <div class="wrap">
    <el-card class="card">
      <h1 class="title">注册账号</h1>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="userName">
          <el-input v-model="form.userName" placeholder="用户名（3-50 字符）" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（至少 6 位）"
                    size="large" show-password />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" size="large" />
        </el-form-item>
        <el-form-item>
          <el-radio-group v-model="form.gender">
            <el-radio label="男">男</el-radio>
            <el-radio label="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="submit">
          注册
        </el-button>
      </el-form>
      <div class="foot">已有账号？<router-link to="/login">去登录</router-link></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { authApi } from '../api'

const formRef = ref()
const form = reactive({ userName: '', password: '', email: '', gender: '' })
const loading = ref(false)
const router = useRouter()

const rules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '长度 3-50 字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '长度 6-50 字符', trigger: 'blur' }
  ]
}

async function submit() {
  await formRef.value.validate(async (ok) => {
    if (!ok) return
    loading.value = true
    try {
      await authApi.register({ ...form, gender: form.gender || undefined })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1f5fbf 0%, #4b8be0 100%); padding: 16px; }
.card { width: 100%; max-width: 380px; }
.title { margin: 0 0 16px; font-size: 22px; text-align: center; color: var(--hh-primary); }
.btn { width: 100%; }
.foot { text-align: center; margin-top: 14px; font-size: 13px; color: #909399; }
</style>
