<template>
  <div class="mobile-confirm-page">
    <div class="confirm-container">
      <!-- 头部图标区域 -->
      <div class="header-icon">
        <el-icon v-if="status === 'confirming'" :size="40" color="#409EFF"><Monitor /></el-icon>
        <el-icon v-else-if="status === 'success'" :size="60" color="#67C23A"><CircleCheckFilled /></el-icon>
        <el-icon v-else :size="60" color="#F56C6C"><CircleCloseFilled /></el-icon>
      </div>

      <!-- 确认状态 -->
      <div v-if="status === 'confirming'" class="content-area">
        <h1 class="title">确认登录请求</h1>
        <p class="desc">
          正在尝试从 PC 端登录您的账号。<br/>
          请确认是您本人的操作。
        </p>
        
        <div class="action-area">
          <el-button 
            type="primary" 
            class="action-btn confirm-btn" 
            size="large"
            :loading="loading" 
            @click="confirm"
          >
            {{ loading ? '处理中...' : '确认登录' }}
          </el-button>
          
          <el-button 
            type="default" 
            class="action-btn cancel-btn" 
            size="large"
            :loading="loading"
            @click="handleCancel"
          >
            拒绝 / 取消
          </el-button>
        </div>
      </div>

      <!-- 成功状态 -->
      <div v-else-if="status === 'success'" class="content-area success-area">
        <h1 class="title success-title">登录已确认</h1>
        <p class="desc">PC 端已自动登录成功。<br/>您可以安全地关闭此页面。</p>
        
        <div class="tips-box">
          <el-icon><InfoFilled /></el-icon>
          <span>若页面未自动关闭，请点击右上角关闭或返回微信</span>
        </div>

        <el-button 
          type="primary" 
          plain
          class="action-btn close-btn" 
          size="large"
          @click="tryCloseOrBack"
        >
          完成 / 关闭
        </el-button>
      </div>

      <!-- 取消/失败状态 -->
      <div v-else-if="status === 'cancelled'" class="content-area success-area">
        <h1 class="title" style="color: #F56C6C">已取消登录</h1>
        <p class="desc">您已拒绝本次登录请求。<br/>PC 端将不会登录该账号。</p>
        
        <el-button 
          type="info" 
          plain
          class="action-btn close-btn" 
          size="large"
          @click="tryCloseOrBack"
        >
          关闭页面
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Monitor, CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'
import request from '../api'

const route = useRoute()
const router = useRouter()
const ticket = route.query.ticket
const loading = ref(false)
const status = ref('confirming') // 'confirming' | 'success' | 'cancelled'

onMounted(async () => {
  if (!localStorage.getItem('token')) {
    router.replace({ path: '/mobile/login', query: { ticket, redirect: '/mobile/qr-confirm' } })
    return
  }
  try {
    await request.post('/qr-login/scan', null, { params: { ticket } })
  } catch (error) {
    console.error('Scan init error:', error)
    ElMessage.error('初始化失败，请刷新重试')
  }
})

async function confirm() {
  if (loading.value) return
  loading.value = true
  
  try {
    await request.post('/qr-login/confirm', { ticket })
    
    status.value = 'success'
    ElMessage.success('已确认，PC端即将登录')
    
  } catch (error) {
    console.error('Confirm error:', error)
    ElMessage.error('确认失败，请重试')
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  if (loading.value) return
  loading.value = true

  try {
    // 1. 通知后端取消登录，这样 PC 端可以立即收到反馈
    // 假设后端有一个取消接口，如果没有，可以注释掉这行，直接跳转
    await request.post('/qr-login/cancel', { ticket }).catch(() => {}) 
    
    status.value = 'cancelled'
    ElMessage.info('已取消登录请求')
  } catch (error) {
    console.error('Cancel error:', error)
    // 即使通知后端失败，前端也应当显示取消状态
    status.value = 'cancelled'
  } finally {
    loading.value = false
  }
}

function tryCloseOrBack() {
  // 尝试微信特有的关闭方法 (仅在某些旧版本或特定配置下有效)
  if (typeof WeixinJSBridge !== "undefined") {
    WeixinJSBridge.call('closeWindow');
  } else {
    // 标准关闭尝试
    window.close();
  }

  // 延迟后检查，如果没关掉，则提示用户或返回
  setTimeout(() => {
    if (!window.closed) {
      // 在微信中，通常最好的做法是留在当前页，或者返回到公众号会话
      // 这里我们尝试返回上一页
      router.back().catch(() => {
         ElMessage.info('请点击右上角关闭页面')
      })
    }
  }, 500)
}
</script>

<style scoped>
.mobile-confirm-page {
  min-height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  box-sizing: border-box;
}

.confirm-container {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border-radius: 20px;
  padding: 40px 30px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.header-icon {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 80px;
  height: 80px;
  background: #ecf5ff;
  border-radius: 50%;
  transition: all 0.3s;
}

/* 动态改变图标背景色 */
.header-icon:has(.el-icon[style*="color: rgb(103, 194, 58)"]) {
  background: #f0f9eb;
}
.header-icon:has(.el-icon[style*="color: rgb(245, 108, 108)"]) {
  background: #fef0f0;
}

.content-area {
  width: 100%;
}

.title {
  font-size: 22px;
  color: #303133;
  margin: 0 0 12px 0;
  font-weight: 600;
}

.success-title {
  color: #67C23A;
}

.desc {
  font-size: 15px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 20px;
}

.tips-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  background-color: #f4f4f5;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 25px;
}

.action-area {
  display: flex;
  flex-direction: column;
  gap: 15px;
  width: 100%;
}

.action-btn {
  width: 100% !important;
  height: 48px !important;
  border-radius: 10px !important;
  font-size: 16px !important;
  font-weight: 500 !important;
  margin: 0 !important;
  box-sizing: border-box;
}

.cancel-btn {
  border-color: #dcdfe6;
  color: #606266;
}

.cancel-btn:hover {
  border-color: #409eff;
  color: #409eff;
}

.success-area {
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>