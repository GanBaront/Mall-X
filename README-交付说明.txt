============================================================
Mall-X 多商家电商平台 · 项目交付说明
（Spring Boot 3 微服务 + uni-app/Vue3 前后端分离）
============================================================

一、项目简介
  多商家电商平台，支持三种角色：
  - 平台管理员（admin）：PC 管理后台，商户审核/订单/支付配置等
  - 入驻商户（merchant）：商家工作台（移动端内），商品/订单/售后/资金
  - 普通用户（user）：移动端购物（浏览/下单/支付/卡券/售后）

  后端：Maven 多模块 —— mall-gateway(网关 10010) / mall-mgr(业务 10011) / mall-alipay(模拟支付宝 10012)
  前端：frontend/pc（Vue3+Element Plus 管理后台 5173）
        frontend/uniapp/mobile（uni-app 用户购物端+商家工作台，H5 预览 5175）
        frontend/uniapp/mobile-hbx（HBuilderX 真机版，由 sync-mobile.ps1 从 mobile 同步）

二、环境要求
  1. JDK 21（keytool 需在 PATH 上，用于生成 HTTPS 证书）
  2. Maven 3.9+
  3. Node.js 18+（含 npm）
  4. MySQL 8.0
  5. HBuilderX（仅真机/模拟器调试 mobile-hbx 时需要，H5 预览不需要）

三、数据库导入（二选一）
  方式一（Navicat）：右键连接 -> 运行 SQL 文件 -> 选择 backend/sql/mall-x-full.sql -> 编码选 65001 (UTF-8)
  方式二（命令行）：mysql -uroot -p < backend/sql/mall-x-full.sql
  脚本自带 CREATE DATABASE，无需手动建库；重复导入先 DROP 再重建（幂等）。
  包含：全部表结构 + 基础数据（账号/商户/100 个商品/支付渠道），无订单演示数据
  （如需近 7 天演示订单，改 backend/mall-mgr/src/main/resources/application.yml
    中 mall.init.demo-orders 为 true 再首次启动即可自动生成）。

四、首次配置（换一台机器部署时必做）
  1. 复制 .env.example 为项目根 .env，按需填写。.env 已被 .gitignore 排除，绝不入库：
       MALL_JWT_SECRET   JWT 签名密钥（48 字节以上随机串，三个模块必须一致）
       MALL_SIGN_KEY     业务接口 MD5 签名密钥
       MALL_DB_PASSWORD  本机 MySQL 口令（默认 123456）
       MALL_SSL_PASSWORD 网关证书库口令（需与 gen-ssl-cert.bat 中的 PASS 一致）
       ALIPAY_*          支付宝沙箱凭据，可全部留空（留空即用内置模拟收银台）
     生成随机串：python -c "import secrets;print(secrets.token_urlsafe(48))"
  2. 生成 HTTPS 自签证书：双击 backend/gen-ssl-cert.bat
     会生成 mall-gateway/src/main/resources/ssl.p12，该文件不入库。
  3. 三个 application.yml 里只有占位默认值，真实值一律来自 .env / 环境变量。
     不配置 .env 也能启动（用演示占位值），但仅限本机演示。

五、构建与启动
  1. 生成证书（见四-2，每台机器只需一次）
  2. 构建后端（首次必须）：
       cd backend
       mvn package -DskipTests
  3. 启动后端：双击 backend/启动后端.bat（自动读取 .env 并拉起 10010/10011/10012）
     或手动：java -jar mall-mgr\target\mall-mgr-1.0.0.jar 等（见 项目使用方式.txt）
  4. 启动 PC 管理后台：双击 frontend/启动PC管理后台.bat，浏览器开 http://localhost:5173
  5. 启动移动端 H5：双击 frontend/启动移动端H5预览.bat，浏览器开 http://localhost:5175
  6. 验证：netstat -ano | findstr "10010 10011 10012 5173 5175"
  另：也可双击根目录 启动Docker.bat 用 docker compose 一键启动（compose 会自动读 .env）。

六、测试账号（仅限本地演示）
  角色          账号       密码       用途
  平台管理员    admin      admin123   PC 管理后台 (5173)
  入驻商户      merchant   123456     商家工作台（5175 登录后自动路由）
  普通用户      user       123456     移动端购物 (5175)
  其他          yunshang/yueji       123456     待审核商户/第二商户
  MySQL         root       123456
  ※ 演示账号与默认口令是课程演示的一部分，故保留在代码/SQL/文档中。
    部署到公网前必须修改，否则等同无认证。

七、目录结构
  backend/            后端源码（Maven 父子模块）+ sql 建库脚本 + uploads 头像图片
  backend/gen-ssl-cert.bat  HTTPS 自签证书生成脚本（生成物不入库）
  frontend/pc/        PC 管理后台源码（Vue3 + Element Plus + ECharts）
  frontend/uniapp/mobile/      移动端源码（uni-app Vue3，含 pages/merchant 商家工作台）
  frontend/uniapp/mobile-hbx/  HBuilderX 真机版（与 mobile 同步，勿直接改，改完跑 sync-mobile.ps1）
  .env.example        本地配置模板（可入库）；.env 为实际配置（不入库）
  项目使用方式.txt     快速启动速查

八、常见问题
  - 启动后端报端口占用：先关闭旧的 java 进程（taskkill /f /im java.exe）再启动。
  - PC 页面数据全 0：后端未全部启动或 MySQL 未启动，先看 5 个端口是否在线。
  - 网关报 keystore 找不到 / 口令错误：先跑 backend/gen-ssl-cert.bat，
    并确认 .env 的 MALL_SSL_PASSWORD 与脚本中的 PASS 一致。
  - 支付宝支付：默认走内置模拟收银台（下单后 payUrl 指向本地 mock/cashier）。
    如需真实沙箱支付，把 APPID/应用私钥/支付宝公钥填进 .env 的 ALIPAY_* 后重启，
    不要写回 application.yml，也不要提交这些值。

九、公开仓库安全说明
  本项目按「可安全推送到公开仓库」整理，请遵守：
  - 一律不入库：.env、*.p12/*.pem/*.key 等私钥与证书库、target/ 构建产物、
    node_modules/、uploads/、output/、.workbuddy/（含本地测试 token 与调试记录）。
    以上规则均已写进 .gitignore，请勿删除或绕过。
  - 配置文件中只剩公开的本地演示占位值；真实密钥通过 .env / 环境变量注入。
  - 支付宝沙箱凭据、沙箱买家账号一律不写入仓库。
  - 若本项目曾以明文密钥推送过公开仓库，那些值应视为已泄露：本项目已轮换
    JWT 密钥、MD5 签名密钥与 TLS 证书；支付宝沙箱密钥请到控制台重置，
    沙箱买家账号/支付密码也建议一并修改。
  - 明文演示口令、SQL 中的假渠道密钥、CORS 允许任意来源、沙箱模式下
    「信任所有证书」均为教学演示实现，仅限本机/内网运行，不可用于生产或公网部署。
============================================================
