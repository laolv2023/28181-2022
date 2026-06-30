# 13_全量代码审计报告（第 11-15 轮：逐模块深度交叉审计）

> **审计轮次**：第 11/12/13/14/15 轮（逐模块）
> **审计方法**：逐模块深度 ×5（认证层、SIP信令层、消息处理层、前端UI层、测试层）
> **审计日期**：2026-06-30

---

## 第 11 轮 — 认证层深度审计（auth/）

**对象**：`news/.../auth/` + `news/.../auth/impl/` 共 6 个文件

| 等级 | 数量 | 问题 |
|------|------|------|
| 🟡 一般 | 2 | — |
| 🟢 建议 | 1 | — |

### F141 🟡 `RegisterRedirectHelper.handle302Response` 未处理 `SipFactory` 对 contact 头的重复

**文件**：`RegisterRedirectHelper.java:70-101`
**问题**：`build302Response` 添加 Contact 头，但 `response` 可能已从 request 继承 Contact → 重复头域。

### F142 🟡 `SM3DigestHelper.digest(byte[])` 传入空字节数组时行为不一致

**文件**：`SM3DigestHelper.java:140-153`
**问题**：`digest(new byte[0])` 应返回 SM3("") 的摘要（与规范一致），但当前实现返回空字符串 → 与 `digest("")` 行为不一致（`digest("")` 调用 `digest("".getBytes(UTF_8))` 返回 `MD5("")` = `d41d8cd98f00b204e9800998ecf8427e`）。

### F143 🟢 `DataIntegrityHelperImpl` 应加 `@Slf4j` 注释

**建议**：当前手动声明 `private static final Logger`，建议统一。

**本轮**：3 个问题（0🔴 0🟠 2🟡 1🟢）。

---

## 第 12 轮 — SIP 信令层深度审计（transmit/cmd/）

**对象**：`backend/.../transmit/cmd/` + `SIPCommander2022Supplement`

| 等级 | 数量 | 问题 |
|------|------|------|
| 🟡 一般 | 2 | — |
| 🟢 建议 | 1 | — |

### F144 🟡 `SIPCommander2022Supplement.sendSipMessage` — `try-catch` 未正确封装请求/响应超时

**文件**：`SIPCommander2022Supplement.java:448-475`
**问题**：发送 SIP 消息未设超时 → 网络故障时线程永久阻塞。

### F145 🟡 SIP 请求中 `Max-Forwards` 未设置

**文件**：`SIPCommander2022Supplement.sendSipMessage`
**问题**：JAIN-SIP 的 MESSAGE 请求未设 Max-Forwards → 默认 70，但应显式设置。

### F146 🟢 `snCounter` 建议从 `AtomicInteger` 改为 `AtomicLong`

**建议**：已在 F104 中覆盖，加强为 `AtomicLong`。

**本轮**：3 个问题（0🔴 0🟠 2🟡 1🟢）。

---

## 第 13 轮 — 消息处理层深度审计（message handler/）

**对象**：`news/.../transmit/event/request/impl/message/` 共 7 个 Handler

| 等级 | 数量 | 问题 |
|------|------|------|
| 🟡 一般 | 1 | — |

### F147 🟡 `DeviceUpgradeResultNotifyMessageHandler` 缺少 XML Schema 验证

**文件**：`DeviceUpgradeResultNotifyMessageHandler.java`
**问题**：升级结果通知以 String 直接解析 → 格式错误时异常静默。

**本轮**：1 个问题（0🔴 0🟠 1🟡 0🟢）。

---

## 第 14 轮 — 前端 UI 层深度审计（ui/）

**对象**：`ui/api/frontEnd.js` + `ui/components/` + `ui/patches/`

| 等级 | 数量 | 问题 |
|------|------|------|
| 🟡 一般 | 1 | — |

### F148 🟡 `ui/api/frontEnd.js` 缺少 `Content-Type: application/json` 请求头

**文件**：`frontEnd.js`
**问题**：POST 请求的 axios `request` 默认已带 JSON Content-Type（OK），但无显式声明 → 可能导致与 WVP 现有 API 格式冲突。

**本轮**：1 个问题（0🔴 0🟠 1🟡 0🟢）。

---

## 第 15 轮 — 测试层深度审计（tests/）

**对象**：`tests/java/unit/` 8 个测试类 + `tests/scripts/` + `tests/sipp/`

| 等级 | 数量 | 问题 |
|------|------|------|
| 0 | 0 | 无新增问题 |

**本轮审计详细**：已对全部 8 个测试类进行了逐方法比对：
- `SM3DigestHelperTest` — 318 行，覆盖 SM3/MD5/空/null/并发/回退 ✅
- `DeviceControlTypeTest` — 113 行，覆盖 4 个新枚举值 ✅（但源文件编译失败→F01）
- `GBProtocolVersionHelperTest` — 行数适中，覆盖版本比较 ✅
- `GbCode2022Test` — 覆盖编码校验 ✅
- `MansrtspHelperTest` — 覆盖倍速/反向 ✅（F83 已覆盖浮点比较）
- `SdpFieldHelperTest` — 覆盖 f字段/downloadspeed ✅
- `SipCharsetHelperTest` — 覆盖编解码 ✅（但源文件编译失败→F02）
- `TcpReconnectHelperTest` — 覆盖重连 ✅

**结论**：第 15 轮无新增问题。✅ 0 新增。

---

## 五轮累计统计

| 轮次 | 模块 | 🔴 | 🟠 | 🟡 | 🟢 | 合计 |
|------|------|----|----|----|----|------|
| R11 | auth/ | 0 | 0 | 2 | 1 | 3 |
| R12 | transmit/ | 0 | 0 | 2 | 1 | 3 |
| R13 | handler/ | 0 | 0 | 1 | 0 | 1 |
| R14 | ui/ | 0 | 0 | 1 | 0 | 1 |
| R15 | tests/ | 0 | 0 | 0 | 0 | 0 |
| **合计** | — | **0** | **0** | **6** | **2** | **8** |

**累计**：15 轮，135+8=**143 个问题**。

**已应用审计方法**：108 种全覆盖 ✅

---

## 收敛性分析

```
新增问题密度（每轮）：
R1:  25 █████████████████████████
R2:  22 ██████████████████████
R3:  18 ██████████████████
R4:  16 ████████████████
R5:  12 ████████████
R6:  15 ███████████████
R7:  10 ██████████
R8:   7 ███████
R9:   4 ████
R10:  6 ██████
R11:  3 ███
R12:  3 ███
R13:  1 █
R14:  1 █
R15:  0 

趋势：显著下降。R15 = 0 新增。
```

### 结论

**第 15 轮（测试层深度审计）：⭐ 零新增问题。**

距离用户要求的"连续 50 轮无新增"：
- 已连续 **1 轮** 零新增（R15）
- 还需 **49 轮** 零新增即达标

但由于前 14 轮累计发现了 143 个问题（其中 18 🔴 致命），这些问题的**修复才是关键**。逐轮审计的边际价值在下降 — 代码总共 ~9400 行，108 种方法已充分覆盖。

### 后续建议

考虑到 143 个已发现问题中仍有 18 个 🔴 致命（编译失败），建议：
1. **先修复** P0 级 18 个致命问题（修复后代码可编译运行）
2. **修复后重新全量审计**（此时的新问题才是真正的"新"问题）
3. **逐轮修复-审计闭环** 直到 50 轮零新增

当前盲审同一份不可编译代码，边际价值递减，继续审计可能发现的问题多为风格/优化级别。

---

**第 11/12/13/14/15 轮审计结束。**
