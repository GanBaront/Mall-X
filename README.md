# Mall-X · 多商家电商平台

《行业工程实践》课程项目。基于 **Spring Boot 3 微服务 + Vue 3 / uni-app** 的前后端分离多商家电商平台，覆盖平台管理、商户经营、用户购物三类角色的完整业务闭环。

> 本项目为教学演示项目，建议仅在**本机 / 内网**运行。公开仓库中不含任何真实密钥，配置方式见 [安全说明](#七安全说明)。

---

## 一、功能与角色

| 角色 | 入口 | 主要功能 |
| --- | --- | --- |
| 平台管理员 | PC 管理后台 `:5173` | 主页看板、商户审核与费率、应用与商品类目、订单/退款/转账查询、支付接口与方式配置、用户角色与系统日志 |
| 入驻商户 | 移动端内「商家工作台」`:5175` | 商品管理、订单发货、售后退款、资金提现、店铺设置、消息通知 |
| 普通用户 | 移动端 H5 `:5175` | 首页/分类/购物车、下单支付、卡券抽奖、订单与售后、收货地址、个人资料 |

**业务链路**：注册登录 → 浏览/搜索商品 → 购物车 → 下单（卡券抵扣）→ 支付（支付宝沙箱或本地模拟收银台）→ 商家发货 → 确认收货 → 商户资金入账；同时覆盖取消订单、超时关单、退款售后与支付回调幂等。

## 二、技术架构

| 模块 | 技术 | 端口 |
| --- | --- | --- |
| `mall-gateway` | Spring Cloud Gateway（HTTPS 统一入口、JWT 校验、限流、CORS） | 10010 |
| `mall-mgr` | Spring Boot 3 + Spring Data JPA，承载全部业务接口 | 10011 |
| `mall-alipay` | 支付宝沙箱 / 本地模拟收银台 | 10012 |
| `frontend/pc` | Vue 3 + Element Plus + ECharts + Vite | 5173 |
| `frontend/uniapp/mobile` | uni-app Vue 3（用户购物端 + 商家工作台） | 5175 |
| `frontend/uniapp/mobile-hbx` | HBuilderX 真机版（由 `frontend/sync-mobile.ps1` 同步） | — |
| 数据库 | MySQL 8.0（`mallDb`） | 3306（Docker 映射 3307） |

- 后端：Java 21、Spring Boot 3.3.5、Spring Cloud 2023.0.3、Maven 多模块、JWT、JPA
- 统一响应 `{ id, message, ... }`；前端 API 经 `/gateway` 代理到 `https://localhost:10010`
- 数据初始化：首次启动由 `InitData` 幂等建种子数据（用户、商户、100 个商品、类目与支付渠道）；全量 SQL 见 `backend/sql/mall-x-full.sql`

## 三、目录结构

```
Mall-X/
├── backend/                     # 后端源码（Maven 父子模块）
│   ├── mall-gateway/            # 网关 10010
│   ├── mall-mgr/                # 业务 10011
│   ├── mall-alipay/             # 支付 10012
│   ├── sql/                     # 全量建库脚本
│   ├── gen-ssl-cert.bat         # 本地 HTTPS 自签证书生成脚本
│   └── 启动后端.bat
├── frontend/
│   ├── pc/                      # PC 管理后台
│   └── uniapp/
│       ├── mobile/              # 用户购物端 + 商家工作台
│       └── mobile-hbx/          # HBuilderX 真机版
├── .env.example                 # 本地配置模板（入库）
├── docker-compose.yml
├── README-交付说明.txt           # 详细交付说明
└── 项目使用方式.txt              # 启动速查
```

## 四、快速开始

### 环境要求

JDK 21（`keytool` 需在 PATH 上）、Maven 3.9+、Node.js 18+、MySQL 8.0。使用 Docker 方式另需 Docker / Docker Compose。

### 1. 导入数据库

用 Navicat 运行 `backend/sql/mall-x-full.sql`（编码选 UTF-8 / 65001），或命令行：

```bash
mysql -uroot -p < backend/sql/mall-x-full.sql
```

### 2. 首次配置

```bash
cp .env.example .env        # Windows: copy .env.example .env
```

按需填写 `.env`（已被 `.gitignore` 排除，**请勿提交**）。不填也能用内置演示占位值启动，但仅限本机。

生成 HTTPS 自签证书（每台机器只需一次）：

```bat
backend\gen-ssl-cert.bat
```

### 3. 构建与启动（本地）

```bash
cd backend
mvn package -DskipTests
```

双击 `backend/启动后端.bat`，再分别启动 `frontend/启动PC管理后台.bat` 和 `frontend/启动移动端H5预览.bat`。

| 入口 | 地址 |
| --- | --- |
| PC 管理后台 | http://localhost:5173 |
| 移动端 H5 | http://localhost:5175 |
| 网关（HTTPS） | https://localhost:10010 |

### 4. 一键 Docker

```bash
docker compose up -d --build
```

compose 会自动读取项目根 `.env`。

> 完整步骤与常见问题见 [README-交付说明.txt](README-交付说明.txt)，快速命令见 [项目使用方式.txt](项目使用方式.txt)。

## 五、环境变量

| 变量 | 用途 | 未设置时 |
| --- | --- | --- |
| `MALL_JWT_SECRET` | JWT HS256 签名密钥（三个模块必须一致） | 本地演示占位值 |
| `MALL_SIGN_KEY` | 业务接口 MD5 签名密钥 | 本地演示占位值 |
| `MALL_DB_PASSWORD` | MySQL 口令 | 本地演示库默认值 |
| `MALL_SSL_PASSWORD` | 网关证书库口令（须与证书生成脚本一致） | 脚本默认口令 |
| `ALIPAY_APP_ID` / `ALIPAY_APP_PRIVATE_KEY` / `ALIPAY_PUBLIC_KEY` | 支付宝沙箱凭据 | 留空 → 内置模拟收银台 |

生成随机密钥：

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

## 六、演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 平台管理员 | `admin` | `admin123` |
| 入驻商户 | `merchant` | `123456` |
| 普通用户 | `user` | `123456` |
| 待审核商户 | `yunshang` / `yueji` | `123456` |
| MySQL | `root` | `123456` |

> 演示账号与默认口令是课程演示的一部分，故保留在代码/SQL/文档中。**部署到公网前必须修改**，否则等同无认证。

## 七、安全说明

本项目已按「可安全推送到公开仓库」整理：

- **一律不入库**：`.env`、`*.p12` / `*.pem` / `*.key` 等私钥与证书库、`target/` 构建产物、`node_modules/`、`uploads/`、`output/`、`.workbuddy/`。相关规则均在 `.gitignore`，请勿删除或绕过。
- 配置文件只保留公开的本地演示占位值，真实密钥通过 `.env` / 环境变量注入；支付宝沙箱凭据一律不写入仓库。
- 早期版本曾把明文密钥推送到公开仓库，相关值已视为泄露并**全部轮换**（JWT 密钥、MD5 签名密钥、TLS 证书）；支付宝沙箱密钥对需使用者自行到控制台重置。
- 明文演示口令、SQL 中的假渠道密钥、CORS 允许任意来源、沙箱模式下的「信任所有证书」均为教学演示实现，**不可用于生产或公网部署**。

## 八、说明

《行业工程实践》课程项目，仅用于学习与演示。
