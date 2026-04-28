<template>
  <div class="mobile-login-page">
    <div class="login-container">
      <!-- 头部 Logo/标题区域 -->
      <div class="header">
        <div class="logo-circle">
          <el-icon :size="30" color="#409EFF"><User /></el-icon>
        </div>
        <h1 class="title">欢迎登录</h1>
        <p class="subtitle">请使用账号密码验证身份</p>
      </div>

      <!-- 表单区域 -->
      <div class="form-area">
        <el-input 
          v-model="username" 
          placeholder="请输入用户名" 
          prefix-icon="User"
          class="custom-input"
          size="large"
          clearable
        />
        
        <el-input 
          v-model="password" 
          type="password" 
          placeholder="请输入密码" 
          prefix-icon="Lock"
          show-password 
          class="custom-input"
          size="large"
          style="margin-top: 16px"
        />

        <el-button 
          type="primary" 
          class="login-btn" 
          size="large"
          :loading="loading" 
          @click="login"
        >
          {{ loading ? '登录中...' : '立即登录' }}
        </el-button>
      </div>
      
      <!-- 底部辅助链接（可选） -->
      <div class="footer-links">
        <span class="link-text">遇到问题？请联系管理员</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import request from '../api'

const route = useRoute()
const router = useRouter()
const username = ref('admin')
const password = ref('123456')
const loading = ref(false)

async function login() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const data = await request.post('/auth/login', { 
      username: username.value, 
      password: password.value 
    })
    
    localStorage.setItem('token', data.token)
    ElMessage.success('登录成功')
    
    const redirect = route.query.redirect || '/mobile/qr-confirm'
    router.replace({ 
      path: redirect, 
      query: { ticket: route.query.ticket } 
    })
  } catch (error) {
    console.error(error)
    ElMessage.error('登录失败，请检查账号密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.mobile-login-page {
  min-height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  box-sizing: border-box;
}

.login-container {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border-radius: 20px;
  padding: 40px 30px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05);
}

/* 头部样式 */
.header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-circle {
  width: 60px;
  height: 60px;
  background: #ecf5ff;
  border-radius: 50%;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 15px;
}

.title {
  font-size: 24px;
  color: #303133;
  margin: 0 0 8px 0;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

/* 表单样式 */
.custom-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 12px 15px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset;
}

.login-btn {
  margin-top: 30px;
  width: 100%;
  border-radius: 10px;
  height: 48px;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 1px;
}

/* 底部链接 */
.footer-links {
  margin-top: 30px;
  text-align: center;
}

.link-text {
  font-size: 12px;
  color: #c0c4cc;
}
</style>