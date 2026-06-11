<p align="center">
  <h1 align="center">API 开放平台</h1>
  <p align="center">一个类阿里云 API 市场的开发者基础设施平台，提供统一的 API 鉴权、限流、计费与全生命周期管理。</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/JDK-17-orange" alt="JDK 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen" alt="Spring Boot 3.2.4">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2023.0.1-blue" alt="Spring Cloud 2023.0.1">
  <img src="https://img.shields.io/badge/Apache%20Dubbo-3.2.12-orange" alt="Dubbo 3.2.12">
  <img src="https://img.shields.io/badge/MySQL-8.0-blue" alt="MySQL 8.0">
</p>

---

## 目录

- [项目简介](#项目简介)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [模块说明](#模块说明)
- [已实现功能](#已实现功能)
- [快速开始](#快速开始)
- [数据库设计](#数据库设计)
- [请求链路](#请求链路)
- [安全机制](#安全机制)
- [前端配套](#前端配套)
- [后续规划](#后续规划)
- [关于项目](#关于项目)

---

## 项目简介

**API 开放平台**为开发者提供一站式的 API 接入体验：

- **管理员**在后台发布、管理各类 API 接口（如天气查询、翻译、AI 问答等）
- **开发者**注册后获取专属 AccessKey / SecretKey，引入官方 SDK 即可一行代码调用
- **平台**统一提供身份认证、流量控制、配额计费、调用日志等基础设施

核心价值在于：**接口服务的开发者只需关心业务逻辑，鉴权、限流、计费、文档、SDK 全部由平台透明承载**。

---

## 系统架构

```
┌──────────────┐     ┌─────────────────┐     ┌────────────────┐
│   开发者 SDK   │────▶│  API Gateway    │────▶│  Interface 服务  │
│ (client-sdk) │     │   (8090 端口)     │     │   (8102 端口)    │
└──────────────┘     └───────┬─────────┘     └────────────────┘
                             │ RPC (Dubbo)
                             ▼
                      ┌──────────────┐
                      │   Backend     │
                      │ (8101 端口)    │
                      │  ┌──────────┐ │
                      │  │  MySQL    │ │
                      │  └──────────┘ │
                      └──────────────┘
                             ▲
                             │ 注册/发现
                      ┌──────┴──────┐
                      │    Nacos    │
                      │ (8848 端口)  │
                      └─────────────┘
```

- **Gateway**：统一入口，所有 API 调用必须经过网关。完成签名验证、路由转发、调用次数统计
- **Backend**：管理后台，提供 RESTful API 供前端使用。通过 Dubbo 向 Gateway 暴露内部查询和计费接口
- **Interface**：模拟第三方接口服务，独立部署，可根据业务需要替换为真实的业务服务
- **Nacos**：注册中心，Gateway、Backend、Interface 均注册于此，实现服务发现

---

## 技术栈

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Spring Boot | 3.2.4 | 应用基础框架 |
| 微服务 | Spring Cloud Gateway | 2023.0.1 | API 网关（WebFlux + Netty） |
| RPC | Apache Dubbo | 3.2.12 | 跨服务远程调用 |
| 注册中心 | Nacos | 2.x | 服务注册与发现 |
| ORM | MyBatis-Plus | 3.5.5 | 数据库持久层 |
| 数据库 | MySQL | 8.0 | 核心业务数据存储 |
| 工具库 | Hutool | 5.8.16 | HTTP 客户端、加密摘要、JSON |
| 认证 | jjwt | 0.11.x | JWT Token 签发与解析 |
| 构建 | Maven | - | 多模块依赖管理与打包 |

> 后续版本计划引入 Redis（缓存 + 限流）、RocketMQ（异步日志）、Elasticsearch（日志检索），详见[后续规划](#后续规划)。

---

## 模块说明

```
api-open-platform/
├── api-platform-model        # 数据模型层（Entity、DTO、VO）
├── api-platform-common        # 公共组件层（工具类、Dubbo RPC 接口定义、签名算法）
├── api-platform-backend       # 管控后台（REST Controller、Dubbo 提供方）
├── api-platform-gateway       # API 网关（GlobalFilter 鉴权、路由转发）
├── api-platform-interface     # 模拟接口服务（独立部署的第三方 API 服务）
└── api-platform-client-sdk    # 开发者 SDK（Spring Boot Starter 自动装配）
```

| 模块 | 端口 | 职责 | 关键约束 |
|------|------|------|---------|
| backend | 8101 | 管理后台 REST API + Dubbo 暴露内部服务 | 连主数据库 |
| gateway | 8090 | 签名验证、路由转发、次数统计 | **禁止引入 WebMVC**（WebFlux 冲突） |
| interface | 8102 | 提供可调用测试接口 | 不连主数据库，不写鉴权逻辑 |
| client-sdk | - | 为开发者封装签名和 HTTP 调用 | 通过 Maven install 安装到本地仓库 |
| common | - | 签名工具、JWT、Dubbo RPC 接口定义 | 不依赖 Spring Web |
| model | - | 实体类、DTO、VO | 不放业务逻辑 |

---

## 已实现功能

### 用户与认证
- 用户注册时自动颁发 AccessKey / SecretKey
- JWT Token 登录，支持 Token 过期自动刷新
- `@AuthCheck` 注解 + AOP 切面实现角色权限控制（admin / user）
- `GET /user/current` 实时获取当前登录用户信息

### 接口管理
- 管理员发布、修改、删除、下线/上线 API 接口
- 分页查询接口列表，支持按名称和描述模糊搜索
- 接口发布前自动执行连通性测试，测试通过才允许上线
- `POST /interfaceInfo/invoke` 在线调用沙箱，管理员可实时测试接口

### 签名认证
- AK/SK 签名机制，签名公式：`MD5(body.SK.nonce.timestamp)`
- SK 全程不在网络中传输
- nonce + timestamp 双重防重放攻击（5 分钟窗口）

### API 网关
- Spring Cloud Gateway 全局过滤器（`GlobalFilter`），优先级最高
- 拦截请求 → 提取 AK/nonce/timestamp/sign → 签名验证 → 路由转发
- 调用成功后异步扣减配额次数

### 开发者 SDK
- 基于 Spring Boot Starter 自动装配
- 开发者只需引入依赖、配置 AK/SK，即可 `@Autowired ApiClient` 直接调用
- 签名计算、时间戳生成、请求头拼接全部由 SDK 透明处理

### 计费统计
- `user_interface_invoke` 表记录每个用户对每个接口的配额
- `total_num` 累计总调用次数，`left_num` 剩余可用次数
- 网关收到接口成功响应后扣减，支持并发条件下的防超卖（`WHERE left_num > 0`）

---

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Nacos 2.x ([下载](https://github.com/alibaba/nacos/releases))

### 1. 初始化数据库

执行 `api-open-platform/docs/db-init.sql`（如不存在，按下方数据库设计手动建表）：

```sql
CREATE DATABASE IF NOT EXISTS api_open_platform
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 编译安装 SDK

```bash
cd api-platform-client-sdk
mvn clean install
```

### 3. 启动服务（按顺序）

```bash
# 1. 启动 Nacos（默认 8848 端口）
# Windows: startup.cmd -m standalone
# Linux/Mac: sh startup.sh -m standalone

# 2. 启动 Backend（8101）
cd api-platform-backend
mvn spring-boot:run

# 3. 启动 Interface（8102）
cd api-platform-interface
mvn spring-boot:run

# 4. 启动 Gateway（8090）
cd api-platform-gateway
mvn spring-boot:run
```

### 4. 验证

```bash
# 不带签名直接访问网关 → 应返回 403
curl http://localhost:8090/api/name/get?name=test

# 确认 Nacos 控制台服务列表中有 api-platform-backend、api-platform-gateway
# 访问 http://localhost:8848/nacos
```

---

## 数据库设计

### 核心表关系

```
user ──────┐                            interface_info
  │        │                                   │
  │   user_interface_invoke (用户-接口配额) ────┘
  │
  └── api_call_log (调用日志，计划中)
```

### user（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_account | VARCHAR(256) | 登录账号（唯一索引） |
| user_password | VARCHAR(512) | MD5 + Salt 加密 |
| access_key | VARCHAR(512) | API 调用公钥（唯一索引） |
| secret_key | VARCHAR(512) | API 调用私钥 |
| user_role | VARCHAR(256) | admin / user |
| is_delete | TINYINT | 逻辑删除 |

### interface_info（接口信息表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(256) | 接口名称 |
| url | VARCHAR(512) | 接口调用地址 |
| method | VARCHAR(256) | 请求方法（GET/POST 等） |
| request_params | TEXT | 请求参数说明（JSON） |
| status | TINYINT | 0-关闭 / 1-上线 |
| user_id | BIGINT | 创建者 ID |
| is_delete | TINYINT | 逻辑删除 |

### user_interface_invoke（用户调用关系表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 调用者 ID |
| interface_info_id | BIGINT | 接口 ID |
| total_num | INT | 历史总调用次数 |
| left_num | INT | 剩余可调用次数 |
| status | TINYINT | 0-正常 / 1-禁用 |
| is_delete | TINYINT | 逻辑删除 |

---

## 请求链路

一次完整的 API 调用经过以下步骤：

```
开发者引入 SDK
    │
    ├── 1. ApiClient 自动计算签名（body.SK.nonce.timestamp → MD5）
    ├── 2. 请求头携带 AK、nonce、timestamp、sign、body
    │
    ▼
Gateway (8090)
    │
    ├── 3. CustomGlobalFilter 拦截请求
    ├── 4. 根据 AK 通过 Dubbo RPC 查询 Backend 获取真实 SK
    ├── 5. 校验 nonce（防重放）、timestamp（防过期）
    ├── 6. 用真实 SK 重新计算签名，比对客户端传来的 sign
    ├── 7. 签名通过 → 路由转发到对应的 Interface 服务
    │
    ▼
Interface (8102)
    │
    ├── 8. 执行业务逻辑（例如返回天气数据）
    ├── 9. 返回响应体
    │
    ▼
Gateway 后置回调
    │
    ├── 10. 检查 Interface 返回状态码
    ├── 11. 若 2xx → Dubbo RPC 调用 Backend 扣减调用次数（left_num - 1）
    │
    ▼
开发者收到响应结果
```

---

## 安全机制

| 安全措施 | 实现方式 | 防护目标 |
|---------|---------|---------|
| AK/SK 签名 | 客户端用 SK 对 `body.nonce.timestamp` 做 MD5，网关用数据库中的 SK 重算比对 | 防止请求参数被篡改，防止伪造调用者身份 |
| SK 不传输 | SK 仅存储在数据库和客户端配置中，只参与签名计算，不出现在网络传输中 | 防止 SK 泄漏 |
| nonce 防重放 | 每次请求生成随机数，网关短时内去重 | 防止黑客录制请求后重放 |
| timestamp 防过期 | 请求超过 5 分钟即失效 | 防止旧请求被重放 |
| 角色权限 | `@AuthCheck(mustRole = "admin")` + AOP 拦截 | 普通用户无法操作管理类接口 |
| 超卖防护 | `UPDATE ... SET left_num = left_num - 1 WHERE left_num > 0` | 并发场景下配额不被超额使用 |

---

## 前端配套

前端管理控制台：**[api-platform-frontend](https://github.com/AccyCx/api-platform-frontend)**

基于 Vue3 + TypeScript + Element Plus + ECharts，提供：

- 首页数据大盘（接口统计 + 调用排行图表）
- 接口资产管理（发布、编辑、上下线、删除）
- 在线调试沙箱（JSON 参数实时发送带签名请求）
- 用户登录与权限管理

---

## 后续规划

- [ ] **Redis** 三层缓存（鉴权缓存、接口元数据缓存、配额缓存），替代当前同步 Dubbo RPC 查库
- [ ] **Lua 令牌桶限流** 替代当前直接操作数据库的扣费方式
- [ ] **RocketMQ 异步日志** 将日志投递从主链路剥离
- [ ] **Elasticsearch 全文检索** 提供百万级日志秒级查询
- [ ] 用户管理后台（列表、角色、状态、AK/SK 重置）
- [ ] 调用配额管理（管理员为用户分配接口次数）

---

## 关于项目

本项目源于对微服务全链路开发的实践，通过从零构建一个 API 开放平台，深入理解了 Gateway 路由、RPC 通信、签名认证、安全防护和生产级计费系统的实现。

- 后端：**[api-open-platform](https://github.com/AccyCx/api-open-platform)**
- 前端：**[api-platform-frontend](https://github.com/AccyCx/api-platform-frontend)**

