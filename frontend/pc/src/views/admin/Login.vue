<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：登录
  界面名称：管理员登录页（输入账号密码，校验通过后进入后台）
  ============================================
-->
<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <span class="logo-icon">支</span>
        <span class="logo-text">MALL-X 平台管理</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="管理员账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="登录密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">演示账号：admin / admin123</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { adminLogin } from '../../api/admin'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await adminLogin({ ...form })
    localStorage.setItem('admin_token', res.token)
    localStorage.setItem('admin_name', res.username || form.username)
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '登录失败，请检查账号密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2b6cff 0%, #6ea8ff 50%, #9dc3ff 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 10px;
  padding: 40px 36px 28px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #1f2329;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #1f2329;
  letter-spacing: 1px;
}

.login-btn {
  width: 100%;
  letter-spacing: 6px;
}

.login-tip {
  text-align: center;
  color: #909399;
  font-size: 12px;
}
</style>
