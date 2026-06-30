# Jist — AI 通知摘要助手（Android）

## 概述

**Jist** 是一款开源的、完全可配置的 AI 通知摘要工具，运行在 Android 系统上。它拦截来自 WhatsApp、Telegram、Gmail、Slack 等应用的通知，按联系人/群组/话题聚合，再调用 LLM API（Gemini、OpenAI、Claude、OpenRouter 或任意兼容 OpenAI 协议的端点）生成简洁摘要。

支持**自动摘要**（全自动）和**手动模式**（通知上显示"摘要"操作按钮）。

---

## 主要功能

- 🔔 **通知捕获** — 通过 `NotificationListenerService` 监听所有应用通知
- 🤖 **多模型 LLM 支持** — OpenAI、Gemini、Claude、OpenRouter 及任意兼容 OpenAI 协议的 API
- ⚙️ **完全可配置** — 按应用规则、自定义提示词、批处理窗口、模型选择
- 📋 **自动与手动模式** — 后台自动摘要，或点击"摘要"按钮手动触发
- 📱 **按应用规则** — 为不同应用设置不同行为
- 🔑 **自带密钥** — 用户提供自己的 API Key，无需后端服务
- 🔒 **隐私优先** — 所有处理在设备本地完成，仅通知文本发往 LLM
- 🌐 **开源** — Apache 2.0

### NAF（Notification Action Framework）

- 🎯 **通知远程操作** — 通过 `system_server` Binder API 对通知执行点击、回复、关闭，效果等同于用户手动操作
- ⚡ **绕过 30 秒 BAL 限制** — 合入 Framework 补丁后，通知被清除后仍可触发 contentIntent
- 🧩 **Shadow Cache** — 通知取消后自动缓存 `NotificationRecord`（容量 50），扩展操作窗口期
- 🔄 **回退机制** — NAF 不可用时自动降级到 `getLaunchIntentForPackage()`
- 🔧 **基于 LineageOS Android 16 实现** — 需要 ROM 集成 NAF Framework 补丁（详见 [NAF 设计文档](./NAF.md) 与 [NAF 集成指南](./NAF_INTEGRATION_GUIDE.md)）

---

## 文档

| 文档 | 说明 |
|---|---|
| [架构设计](./ARCHITECTURE.md) | 系统架构、组件及数据流 |
| [实现路线图](./IMPLEMENTATION_PLAN.md) | 分阶段任务计划 |
| [数据库设计](./DATABASE_SCHEMA.md) | Room 数据库表及关系 |
| [LLM 集成](./LLM_INTEGRATION.md) | 多模型 LLM 支持实现 |
| [文件结构](./FILE_STRUCTURE.md) | 项目目录组织 |
| [Material 3 设计](./MATERIAL3_EXPRESSIVE.md) | UI 设计系统与组件规范 |
| [功能构想](./FEATURE_IDEAS.md) | 未来功能与社区建议 |
| [NAF 设计文档](./NAF.md) | Notification Action Framework 完整设计方案 |
| [NAF 集成指南](./NAF_INTEGRATION_GUIDE.md) | AI Agent / 第三方 App 集成 NAF 的指引 |
| [Framework 补丁报告](./FRAMEWORK_PATCH_REPORT.md) | AOSP 修改汇总与编译验证 |

---

## 技术栈

| 组件 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose、Material Design 3、Compose Material Icons Extended |
| UI 增强 | Haze（模糊/毛玻璃效果） |
| 导航 | Navigation Compose |
| 数据库 | Room + KSP |
| 偏好存储 | DataStore Preferences |
| 网络 | OkHttp |
| 后台任务 | WorkManager |
| 序列化 | kotlinx.serialization、Gson |
| 最低 SDK | 29（Android 10） |
| 目标 SDK | 36（Android 16） |
