import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import MobileQrConfirm from './views/MobileQrConfirm.vue'
import MobileLogin from './views/MobileLogin.vue'
import Home from './views/Home.vue'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  { path: '/mobile/login', component: MobileLogin },
  { path: '/mobile/qr-confirm', component: MobileQrConfirm },
  { path: '/home', component: Home }
]

export default createRouter({ history: createWebHistory(), routes })
