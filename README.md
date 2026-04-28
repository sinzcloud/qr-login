# QR Login — 扫码登录系统

> Spring Boot 3 + Vue 3 实现的跨设备扫码登录解决方案

## 📚 目录

- [项目介绍](#项目介绍)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [功能演示](#功能演示)
- [API 接口](#api-接口)
- [安全设计](#安全设计)
- [配置说明](#配置说明)

---

## 项目介绍

本项目实现了一套完整的**扫码登录**功能，支持：

- PC 端生成二维码并通过 WebSocket 实时接收登录结果
- 手机端扫描二维码并在移动端确认授权
- JWT 身份认证 + Redis 状态管理
- 完整的登录审计日志

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Spring Boot 3.2.5、Java 17、MyBatis Plus、JWT、WebSocket |
| **前端** | Vue 3、Vite、Element Plus、Axios、Pinia、qrcode |
| **数据库** | MySQL 5.7、Redis |
| **部署** | Docker Compose |

---

## 项目结构

```
qr-login-production/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/
│   │   └── com/example/qrlogin/
│   │       ├── controller/  # REST API 控制器
│   │       ├── service/    # 业务逻辑层
│   │       ├── mapper/     # 数据访问层
│   │       ├── entity/     # 实体类
│   │       ├── dto/        # 数据传输对象
│   │       ├── vo/         # 视图对象
│   │       ├── ws/         # WebSocket 处理器
│   │       ├── config/     # 配置类
│   │       ├── common/     # 公共组件
│   │       └── enums/      # 枚举定义
│   └── src/main/resources/
│       └── application.yml # 配置文件
│
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── api.js          # Axios 封装
│   │   ├── router.js       # 路由配置
│   │   ├── views/          # 页面组件
│   │   │   ├── Home.vue    # 首页
│   │   │   ├── Login.vue   # PC 登录
│   │   │   ├── MobileLogin.vue    # 手机登录
│   │   │   └── MobileQrConfirm.vue # 手机扫码确认
│   │   └── main.js         # 入口文件
│   └── package.json
│
├── docker/                 # Docker 配置
│   └── docker-compose.yml  # MySQL + Redis 一键启动
│
└── sql/                    # 数据库脚本
    └── init.sql            # 初始化表结构和数据
```

---

## 快速开始

### 1. 启动依赖服务（MySQL + Redis）

```bash
cd docker
docker compose up -d
```

### 2. 导入数据库

数据库初始化脚本会在容器启动时自动执行。如需手动导入：

```bash
docker exec -i qr_mysql mysql -uroot -proot123 qr_login < ../sql/init.sql
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端地址：`http://localhost:8080`

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端地址：`http://localhost:5173`

---

## 功能演示

### 扫码登录流程

```
┌─────────────────┐         ┌─────────────────┐
│   PC 浏览器     │         │  手机浏览器     │
│                 │         │                 │
│  1. 访问 /login │         │                 │
│        ↓        │         │                 │
│  2. 生成二维码   │         │                 │
│        ↓        │         │                 │
│  3. WebSocket   │         │                 │
│     等待结果    │         │                 │
│        ↑        │         │                 │
│  6. 收到 token  │         │                 │
│     自动跳转    │         │                 │
└─────────────────┘         └─────────────────┘
        ↑                           ↓
        │    ┌─────────────────────────┘
        │    │
        │    ▼
┌─────────────────┐
│   手机扫码      │
│                 │
│ 4. 扫描二维码   │
│        ↓        │
│ 5. 确认登录     │
└─────────────────┘
```

### 页面路由

| 路径 | 说明 |
|------|------|
| `/` | 首页（需登录） |
| `/login` | PC 端登录/扫码页面 |
| `/mobile/login` | 手机端登录页面 |
| `/mobile/qr-confirm?ticket=xxx` | 手机端扫码确认页面 |

---

## API 接口

### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/login` | 用户名密码登录 |
| `POST` | `/api/auth/logout` | 登出 |

### 扫码登录接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/qr-login/create` | 创建二维码 ticket |
| `GET` | `/api/qr-login/status?ticket=xxx` | 查询二维码状态 |
| `POST` | `/api/qr-login/scan?ticket=xxx` | 手机扫码回调 |
| `POST` | `/api/qr-login/confirm` | 手机确认登录 |
| `POST` | `/api/qr-login/cancel` | 取消扫码 |

### WebSocket

| 路径 | 说明 |
|------|------|
| `/ws/qr-login?ticket=xxx` | PC 端监听扫码结果 |

### 响应格式

```json
{
  "code": 200,
  "data": { ... },
  "message": "success"
}
```

---

## 安全设计

- **Ticket 机制**：使用 UUID 随机生成，每次登录独立
- **时效控制**：Ticket 默认 120 秒过期
- **双重存储**：Redis 存储状态（快速查询），MySQL 存储审计日志
- **身份验证**：Confirm 接口必须携带手机端 JWT Token
- **防伪造**：PC 端不能提交 userId，所有 token 由后端生成
- **防重放**：二维码确认后立即失效，不可重复使用

---

## 配置说明

### 后端配置（application.yml）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/qr_login
    username: root
    password: root123
  data:
    redis:
      host: localhost
      port: 6379

app:
  jwt:
    secret: your-secret-key
    expire-seconds: 86400
  qr-login:
    expire-seconds: 120
```

### 前端环境变量

在 `.env` 或 `.env.development` 中配置：

```
VITE_API_BASE_URL=/api
```

---

## 测试账号

| 用户名 | 密码 |
|--------|------|
| admin | 123456 |

---

## 许可证

MIT License
