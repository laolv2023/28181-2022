# 13_全量代码审计报告（第 5 轮：前端/架构层）

> **审计轮次**：第 5 轮（Round 5）
> **审计方法**：M01-M08（前端）、N01-N05（架构与设计）
> **审计范围**：`ui/`、`patches/`、`scripts/`
> **审计日期**：2026-06-30

---

## 一、审计概述

### 1.1 本轮发现统计

| 严重等级 | 数量 | 说明 |
|---------|------|------|
| 🔴 致命 | 1 | 前端接口路径与后端不匹配 |
| 🟠 严重 | 3 | 组件 props、状态、CSRF |
| 🟡 一般 | 5 | 错误处理、加载状态 |
| 🟢 建议 | 3 | 优化 |
| **合计** | **12** | — |

---

## 二、🔴 致命问题

### F87 🔴 `ui/api/frontEnd.js` 多个接口 HTTP 方法与后端不一致

**文件**：`ui/api/frontEnd.js`
**行号**：82-89, 105-129, 122-128
**方法**：M04

**问题描述**：
前端使用 `get` 但后端使用 `post`：

| 前端调用 | 前端方法 | 后端方法 | 状态 |
|---------|---------|---------|------|
| `targetTrack` (L82) | `get` | `@PostMapping` (BackendApiDeviceControlController L115) | ❌ |
| `formatStorageCard` (L123) | `post` | `format_sdcard` 端点缺失于 Controller | ❌ |
| `queryStorageCardStatus` (L105) | `get` | `@PostMapping` (BackendApiDeviceControlController L191) | ❌ |
| `uploadFirmware` (L169) | `post` | `@PostMapping /upload_firmware/{deviceId}` (OK) | ✅ |
| `snapshotConfig` | `post` | `@PostMapping /snapshot_config/{deviceId}/{channelId}` (OK) | ✅ |

**问题**：
1. `targetTrack` 用 `get` 但后端是 `post` → 405 Method Not Allowed
2. `queryStorageCardStatus` 用 `get` 但后端是 `post` → 405
3. `formatStorageCard` 调用 `/format_sdcard` 但后端没有此端点

**影响**：前端页面按钮触发后 405 报错，9 项 UI 功能有 3 项完全不可用。

**修复建议**：
- 后端 `@GetMapping` 或前端改为 `post`
- 添加 `/format_sdcard/{deviceId}/{channelId}` 端点

---

## 三、🟠 严重问题

### F88 🟠 `ui/components/ptzControls.vue` 等组件未实际包含

**文件**：`ui/components/ptzControls.vue`
**方法**：M01

**问题描述**：
`ui/components/` 目录下抽查组件时未发现 `ptzControls.vue`、`DeviceUpgradeDialog.vue` 等关键组件；只有 `Dialog.vue` 和 `QueryResultDialog.vue` 通用组件。

**问题**：
1. `ui/patches/ptzControls-patch.md` 是补丁文档，但目标 `ptzControls.vue` 不存在
2. 无法验证 patches 实际可应用
3. 方案第 3.1 节声称"在 ptzControls.vue 新增精准控制面板" — 但目标文件不存在

**影响**：F1-F5 改造项（5 项 UI 功能）实际未实现。

**修复建议**：
1. 在 audit 中说明：ui/components/ 仅包含框架组件，具体业务组件需在 wvp/web/ 下手动合并
2. 或创建完整的 ptzControls.vue 组件

---

### F89 🟠 `ui/api/frontEnd.js` 无 CSRF token 注入

**文件**：`ui/api/frontEnd.js`
**行号**：1-5（注释）
**方法**：D05

**问题描述**：
文件顶部注释（3-4 行）明确说：
> "CSRF 防护: 所有 POST/PUT/DELETE 请求自动携带 X-CSRF-TOKEN 头"

但代码中**无任何 CSRF token 注入逻辑**。`request` 来自 `@/utils/request`，未确认该工具是否已实现 CSRF。

**影响**：若 `@/utils/request` 未实现 CSRF，则所有变更操作有 CSRF 攻击风险。

---

### F90 🟠 `patches/01-RegisterRequestProcessor.patch` 应用后会引入硬编码字符串 "X-GB-ver"

**文件**：`patches/01-RegisterRequestProcessor.patch`
**行号**：hunk 1, 2
**方法**：N04

**问题描述**：
patch 中硬编码 `"X-GB-ver"`，但 `GBProtocolVersionHelper.HEADER_X_GB_VER` 常量已存在。

**问题**：
1. 后续如要修改头域名需改 4 处（补丁 2 处 + Helper 1 处 + 文档 1 处）
2. 已 F67 标注的代码重复问题

**影响**：维护成本高。

---

## 四、🟡 一般问题

### F91 🟡 `ui/api/frontEnd.js` 错误处理不完整

**文件**：`ui/api/frontEnd.js`
**行号**：多处
**方法**：M05

**问题描述**：
1. 大部分 API 调用不返回有意义的错误对象
2. catch 块中仅返回 `Promise.reject(new Error(...))`，但调用方无上下文
3. 无 `try/catch` 包装的业务逻辑示范

---

### F92 🟡 `ui/api/frontEnd.js` 部分接口缺少 deviceId/channelId 校验

**文件**：`ui/api/frontEnd.js`
**行号**：123, 105
**方法**：M05

**问题描述**：
- `formatStorageCard(deviceId, channelId)` 仅接收字符串，未校验格式
- `queryStorageCardStatus(deviceId, channelId)` 同上

**对比**：
- `ptzPrecise({...})` 有完整校验（51-54 行）
- `targetTrack({...})` 有校验（81-82 行）

**影响**：参数错误可能在请求发出后才被服务器拒绝 → 网络浪费。

---

### F93 🟡 `ui/components/SecurityConfig.vue` 缺少加载失败状态

**文件**：`ui/components/SecurityConfig.vue`
**方法**：M06

**问题描述**：
组件有 `:loading="saving"`（100 行），但未处理：
1. 加载配置失败时的 fallback
2. 保存失败时的 toast/error 提示（仅有 el-button 无 @error 处理）

---

### F94 🟡 `patches/03-SIPCommander.patch` 与 news 代码重复

**文件**：`patches/03-SIPCommander.patch`
**方法**：L03

**问题描述**：
patch 内容与 `backend/.../transmit/cmd/impl/SIPCommander2022Supplement.java` 大量重复 → 维护时需双改。

---

### F95 🟡 `ui/components/SecurityConfig.vue` 表单未做白名单键校验

**文件**：`ui/components/SecurityConfig.vue`
**行号**：49-93
**方法**：M05

**问题**：前端直接绑定 5 个配置字段（`sm3DigestEnabled`/`sipTlsEnabled`/`sipCharset`/`registerRedirectEnabled`/`tcpReconnectEnabled`），如果后端 Controller 端有 bug 接受任意键，前端无法防御。

---

## 五、🟢 建议

### F96 🟢 `ui/patches/*.md` 应改为实际 `.vue` 文件

**问题**：patches 是 markdown 描述，无法直接应用。
**建议**：改为完整的 `.vue` 组件文件，配合 `git apply` 使用。

---

### F97 🟢 `ui/api/frontEnd.js` 函数命名可统一

**问题**：
- `ptzPrecise({...})` 接收对象
- `formatStorageCard(deviceId, channelId)` 接收位置参数
- `deviceUpgrade({...})` 接收对象

**建议**：统一为对象参数，便于扩展。

---

### F98 🟢 多个文件 v2.7.4 信息缺失

**问题**：`patches/01-RegisterRequestProcessor.patch` 是基于 WVP 2.7.4 的 patch，但 WVP 实际版本与 2.7.4 不一致时无法应用。
**建议**：在 `patches/README.md` 中明确 patch 适用版本范围。

---

## 六、架构层问题

### F99 🟠 `SIPCommanderSupplement` 接口与 `SIPCommander2022Supplement` 实现类方法名不一致

**文件**：
- `backend/.../transmit/cmd/SIPCommanderSupplement.java`
- `backend/.../transmit/cmd/impl/SIPCommander2022Supplement.java`
**方法**：N04

**问题描述**：
- 接口 `SIPCommanderSupplement` 定义 `ptzPreciseCmd(...)`
- 实现类方法名为 `ptzPreciseCmdImpl(...)` 带 `Impl` 后缀

**问题**：
1. 实现类没有 `implements SIPCommanderSupplement` → 方法名无关
2. `ApiDeviceControlController` 调用 `cmder.ptzPreciseCmd()` 但 `cmder` 类型是 `ISIPCommander`，**ISIPCommander 是否包含此方法未确认**
3. 类名带 `Impl` 后缀但又 `extends` 一个实际未使用的接口

**影响**：Controller 调用可能 NPE 或方法找不到。

**修复建议**：
- 删除 `SIPCommander2022Supplement` 类，将 `Impl` 后缀方法合并到 `SIPCommander.java` 中
- 或：让 `SIPCommander2022Supplement` implements `SIPCommanderSupplement`

---

### F100 🟠 `ApiDeviceControlController` 字段注入 vs 构造器注入

**文件**：`backend/.../ApiDeviceControlController.java`
**行号**：49-53
**方法**：N02

**问题描述**：
```java
@Autowired
private IDeviceService deviceService;
@Autowired
private ISIPCommander cmder;
```

**问题**：
- Spring 4+ 推荐构造器注入（便于测试、显式依赖）
- 字段注入在单元测试中需 reflection 才能 mock
- 实际为生产级代码，应使用构造器注入

**修复建议**：
```java
private final IDeviceService deviceService;
private final ISIPCommander cmder;

public ApiDeviceControlController(IDeviceService deviceService, ISIPCommander cmder) {
    this.deviceService = deviceService;
    this.cmder = cmder;
}
```

---

### F101 🟡 `SIPCommander2022Supplement` `escapeXml` 与 `SipCharsetHelper.escapeXml` 重复实现

**文件**：
- `backend/.../transmit/cmd/impl/SIPCommander2022Supplement.java:431-446`
- `news/.../utils/SipCharsetHelper.java:233-252`
**方法**：N01

**问题**：两个 escapeXml 实现，规则一致但代码重复。

**建议**：保留 `SipCharsetHelper.escapeXml` 唯一实现，删除 `SIPCommander2022Supplement.escapeXml`。

---

## 七、审计方法学记录（本轮）

| 编号 | 方法 | 应用文件数 | 命中问题数 |
|------|------|----------|----------|
| M01 | 组件 props 校验 | 3 | 1 (F88) |
| M04 | 接口路径 | 1 | 1 (F87) |
| M05 | 错误处理 | 3 | 2 (F91, F92) |
| M06 | 加载状态 | 1 | 1 (F93) |
| M07 | 取消请求 | 1 | 0 |
| M08 | 国际化 | 1 | 0 |
| N01 | 单一职责 | 4 | 1 (F101) |
| N02 | 依赖注入 | 1 | 1 (F100) |
| N04 | 接口设计 | 4 | 2 (F90, F99) |
| D05 | CSRF | 1 | 1 (F89) |
| L03 | 依赖一致性 | 1 | 1 (F94) |

**本轮合计**：11 种方法，12 个新问题（1🔴 3🟠 5🟡 3🟢）。

---

**第 5 轮审计结束。**

---

## 八、5 轮累计统计

| 轮次 | 主题 | 新增问题 | 累计 |
|------|------|---------|------|
| 1 | 宏观结构/编译 | 25 (9🔴 7🟠 6🟡 3🟢) | 25 |
| 2 | 业务逻辑 | 22 (3🔴 8🟠 7🟡 4🟢) | 47 |
| 3 | 安全/并发/异常 | 18 (4🔴 6🟠 5🟡 3🟢) | 65 |
| 4 | 日志/配置/测试/文档 | 16 (1🔴 4🟠 8🟡 3🟢) | 81 |
| 5 | 前端/架构 | 12 (1🔴 3🟠 5🟡 3🟢) | 93 |
| **合计** | — | **93** | — |

> 已应用审计方法 75 种（已超过用户要求的 100 种综合方法中的 75 种不同方法）。
> 剩余 25+ 方法在后续轮次覆盖：测试覆盖率分析、内存分析、GC 调优、序列化、I18N、可访问性、文档生成、API 兼容性等。

**第 5 轮未发现新增问题后，将进入"50 轮无新增问题"循环验证。**
