<template>
  <div class="page">
    <!-- 可选：显示当前环境标识，方便调试 -->
    <div v-if="isDev" class="env-tag">DEV</div>
    
    <div class="card">
      <!-- 顶部切换标签 -->
      <div class="tabs">
        <div 
          class="tab-item" 
          :class="{ active: loginType === 'password' }" 
          @click="switchLoginType('password')"
        >
          账号登录
        </div>
        <div 
          class="tab-item" 
          :class="{ active: loginType === 'qr' }" 
          @click="switchLoginType('qr')"
        >
          扫码登录
        </div>
      </div>

      <!-- 1. 扫码登录区域 -->
      <div v-if="loginType === 'qr'" class="login-content">
        <div class="qr-box">
          <canvas ref="qrCanvas" v-show="status === 0 || status === 1"></canvas>
          
          <!-- 状态遮罩层 -->
          <div v-if="status === 1" class="status-overlay">
            <el-icon :size="50" color="#409EFF"><Check /></el-icon>
            <p>已扫描</p>
          </div>
          <div v-if="status === 2" class="status-overlay">
            <el-icon :size="50" color="#67C23A"><CircleCheckFilled /></el-icon>
            <p>登录成功</p>
          </div>
          <div v-if="status === 3 || status === 4" class="status-overlay">
            <el-icon :size="50" color="#F56C6C" class="rotating"><RefreshRight /></el-icon>
            <p>{{ status === 3 ? '二维码已过期' : '已取消' }}</p>
            <p class="sub-text">正在刷新...</p>
          </div>
        </div>
        <p class="status-text">{{ statusText }}</p>
        <el-button 
          v-if="status !== 2 && status !== 3 && status !== 4" 
          type="primary" 
          plain 
          @click="createQr"
        >
          刷新二维码
        </el-button>
      </div>

      <!-- 2. 账号密码登录区域 -->
      <div v-else class="login-content password-form">
        <el-form :model="loginForm" label-width="0">
          <el-form-item>
            <el-input 
              v-model="loginForm.username" 
              placeholder="请输入用户名" 
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item>
            <el-input 
              v-model="loginForm.password" 
              type="password" 
              placeholder="请输入密码" 
              :prefix-icon="Lock"
              show-password
              size="large"
              @keyup.enter="handlePasswordLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-button 
              type="primary" 
              size="large" 
              class="login-btn" 
              :loading="passwordLoading"
              @click="handlePasswordLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Check, CircleCheckFilled, RefreshRight, User, Lock } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import request from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()

// --- 环境判断 ---
const isDev = import.meta.env.DEV

// --- 状态管理 ---
const loginType = ref('password') // 'qr' | 'password'
const passwordLoading = ref(false)
const loginForm = reactive({
  username: 'admin',
  password: '123456'
})

// --- 扫码登录相关逻辑 ---
const qrCanvas = ref(null)
const ticket = ref('')
const status = ref(0) // 0:等待, 1:已扫, 2:成功, 3:过期, 4:取消
let timer = null
let ws = null
let autoRefreshTimer = null

const statusText = computed(() => {
  const map = {
    0: '请使用手机 App 扫描二维码',
    1: '扫描成功，请在手机上确认',
    2: '登录成功，跳转中...',
    3: '二维码已过期',
    4: '登录已取消'
  }
  return map[status.value] || ''
})

// --- 方法定义 ---

// 切换登录方式
function switchLoginType(type) {
  if (loginType.value === type) return
  loginType.value = type
  
  // 如果切换到密码登录，清理扫码的资源
  if (type === 'password') {
    cleanupQr()
  } else {
    // 如果切换回扫码，初始化二维码
    createQr()
  }
}

// 账号密码登录
async function handlePasswordLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  
  passwordLoading.value = true
  try {
    // request 实例应已在 api/index.js 中配置了 baseURL (从 import.meta.env.VITE_API_BASE_URL 读取)
    const res = await request.post('/auth/login', {
      username: loginForm.username,
      password: loginForm.password
    })
    
    if (res.token) {
      localStorage.setItem('token', res.token)
      ElMessage.success('登录成功')
      router.push('/home')
    } else {
      ElMessage.error('登录失败，未获取到 Token')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('登录失败，请检查账号密码')
  } finally {
    passwordLoading.value = false
  }
}

// --- 扫码登录核心逻辑 (保持原有逻辑) ---

function cleanupQr() {
  if (timer) { clearInterval(timer); timer = null; }
  if (autoRefreshTimer) { clearTimeout(autoRefreshTimer); autoRefreshTimer = null; }
  if (ws) {
    ws.onopen = null; ws.onmessage = null; ws.onerror = null; ws.onclose = null;
    if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) ws.close();
    ws = null;
  }
}

async function createQr() {
  cleanupQr()
  try {
    const data = await request.post('/qr-login/create')
    ticket.value = data.ticket
    status.value = 0
    await QRCode.toCanvas(qrCanvas.value, data.qrUrl, { width: 220 })
    connectWs(data.ticket)
    startPolling()
  } catch (error) {
    console.error('Create QR failed:', error)
    autoRefreshTimer = setTimeout(createQr, 3000)
  }
}

function connectWs(t) {
  // 1. 尝试从环境变量获取 WS 基础地址
  let wsBaseUrl = import.meta.env.VITE_WS_BASE_URL
  
  // 2. 如果环境变量未配置，则根据当前页面协议动态生成（兼容局域网/生产环境）
  if (!wsBaseUrl) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    wsBaseUrl = `${protocol}//${window.location.host}`
  }

  // 3. 拼接完整路径
  // 注意：如果 VITE_WS_BASE_URL 已经包含了 /ws/qr-login，请调整此处拼接逻辑
  const wsUrl = `${wsBaseUrl}/ws/qr-login?ticket=${t}`
  
  console.log('Connecting to WS:', wsUrl) // 调试时可查看连接地址
  ws = new WebSocket(wsUrl)
  
  ws.onmessage = e => {
    try {
      const data = JSON.parse(e.data)
      handleStatusChange(data.status, data.token)
    } catch (err) { console.error(err) }
  }
}

function startPolling() {
  timer = setInterval(async () => {
    if (!ticket.value || status.value === 2) { clearInterval(timer); return; }
    try {
      const data = await request.get('/qr-login/status', { params: { ticket: ticket.value } })
      handleStatusChange(data.status, data.token)
    } catch (error) {}
  }, 2000)
}

function handleStatusChange(newStatus, token) {
  if (status.value === 2) return
  status.value = newStatus
  if (newStatus === 2 && token) {
    loginSuccess(token)
  } else if (newStatus === 3 || newStatus === 4) {
    cleanupQr()
    autoRefreshTimer = setTimeout(() => { if(loginType.value === 'qr') createQr() }, 1500)
  }
}

function loginSuccess(token) {
  cleanupQr()
  localStorage.setItem('token', token)
  setTimeout(() => { router.push('/home') }, 1000)
}

onMounted(() => {
  if (loginType.value === 'qr') createQr()
})
onUnmounted(cleanupQr)
</script>

<style scoped>
.page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7fa;
  position: relative; /* 为 env-tag 定位 */
}

/* 环境标识样式 */
.env-tag {
  position: fixed;
  top: 10px;
  right: 10px;
  background: #F56C6C;
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
  z-index: 9999;
  box-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

.card {
  background: white;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  width: 360px;
  text-align: center;
}

/* 顶部 Tabs 样式 */
.tabs {
  display: flex;
  margin-bottom: 30px;
  border-bottom: 1px solid #ebeef5;
}

.tab-item {
  flex: 1;
  padding: 12px 0;
  cursor: pointer;
  font-size: 16px;
  color: #909399;
  transition: all 0.3s;
  position: relative;
}

.tab-item.active {
  color: #303133;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 40px;
  height: 2px;
  background-color: #409EFF;
}

.login-content {
  min-height: 280px; /* 保持高度一致，防止切换时抖动 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

/* 二维码样式 */
.qr-box {
  position: relative;
  width: 220px;
  height: 220px;
  margin: 0 auto 20px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
}

.status-overlay {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background: rgba(255, 255, 255, 0.95);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 10;
  animation: fadeIn 0.3s ease;
}

.status-overlay p { margin: 5px 0; font-size: 14px; font-weight: 500; color: #303133; }
.sub-text { font-size: 12px !important; color: #909399 !important; font-weight: normal !important; }
.status-text { color: #606266; font-size: 14px; margin-bottom: 20px; }

/* 密码表单样式 */
.password-form {
  width: 100%;
}
.password-form :deep(.el-form-item) {
  margin-bottom: 20px;
}
.login-btn {
  width: 100%;
  font-weight: 500;
  letter-spacing: 2px;
}

.rotating { animation: rotate 1.5s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>