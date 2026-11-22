<template>
  <AuthLayout title="注册账号" description="创建考生账号，开始你的备考学习。">
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item prop="userName" label="用户名">
          <el-input v-model="form.userName" placeholder="3–50 个字符" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password" label="密码">
          <el-input v-model="form.password" type="password" placeholder="至少 6 个字符"
                    size="large" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item prop="email" label="邮箱（选填）">
          <el-input v-model="form.email" placeholder="请输入邮箱" size="large" autocomplete="email" />
        </el-form-item>
        <el-form-item label="性别（选填）">
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
  </AuthLayout>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { authApi } from '../api'
import AuthLayout from '../components/AuthLayout.vue'

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
.btn { width: 100%; height: 46px; border-radius: 9px; font-weight: 600; }
.foot { margin-top: 22px; color: #738097; font-size: 13px; text-align: center; }
.foot a { margin-left: 6px; color: var(--hh-primary, #646cff); font-weight: 600; text-decoration: none; }
.foot a:hover { text-decoration: underline; }
.foot a:focus-visible { outline: 2px solid var(--hh-primary, #646cff); outline-offset: 4px; }
</style>
