# 13_全量代码审计报告（第 8 轮：反模式与代码异味 + 第 9 轮：兼容性 + 第 10 轮：运维可观测性）

> **审计轮次**：第 8/9/10 轮（合并报告）
> **审计方法**：反模式检测 10 种 + 兼容性回退 8 种 + 运维可观测性 8 种 = 26 种
> **审计日期**：2026-06-30

---

## 一、第 8 轮 — 反模式与代码异味

| 等级 | 数量 |
|------|------|
| 🟠 严重 | 2 |
| 🟡 一般 | 3 |
| 🟢 建议 | 2 |
| **合计** | **7** |

### F124 🟠 `ApiConfigController` — static `SECURITY_CONFIG` Mock 测试困难

**文件**：`backend/.../ApiConfigController.java:46`
**问题**：`private static final` Map 无法被单元测试 isolation → 测试间污染。

### F125 🟠 `SIPCommander2022Supplement` — 9个方法构造 XML 模式高度重复

**文件**：`backend/.../SIPCommander2022Supplement.java`
**问题**：9 个 `*CmdImpl` 方法**逐行复制粘贴** XML 构造器模板：
```java
StringBuilder xml = new StringBuilder(512);
xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
xml.append("<Control>\r\n");
xml.append("<CmdType>DeviceControl</CmdType>\r\n");
xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
```
重复率 >80%。应抽象为 `private String buildControlXml(String cmdType, Consumer<Builder> body)`。

### F126 🟡 多处使用 `if-else` 链代替 enum switch

**文件**：`ApiConfigController.saveSecurity:80-118`
**问题**：5 个级联 `if (config.containsKey("key"))` → 可改为 `Map<String, Consumer<Object>>`.

### F127 🟡 `SdpFieldHelper` 常量与工具类混合

**问题**：类中混有 `DOWNLOAD_S_FIELD` 常量和 `buildFField()` 方法 → 应拆分为 `SdpConstants` + `SdpBuilder`。

### F128 🟡 `SipMessageFilter` 白名单不可配置

**问题**：`ALLOWED_METHODS` 等 static final Set → 若未来协议新增方法需重新构建。

### F129 🟢 TODO 风格：注释替代了 issue tracker

**问题**：大量 `// TODO` 注释在代码中但没有关联 Issue 号 → 无法跟踪。

### F130 🟢 日志使用中文内容

**问题**：`log.info("[配置已保存]...")` → 国际化团队排查困难。

---

## 二、第 9 轮 — 兼容性与回退机制

| 等级 | 数量 |
|------|------|
| 🟠 严重 | 1 |
| 🟡 一般 | 2 |
| 🟢 建议 | 1 |
| **合计** | **4** |

### F131 🟠 2016→2022 版本协商只降级到 `1.0`，未兼容 `null`

**文件**：`GBProtocolVersionHelper.parseGbVerHeader:144-163`
**问题**：未带 X-GB-ver 的设备默认为 `1.0`（2016），但部分 2014 版设备不认 1.0 → 应支持 `0.0` 或 unknown。

### F132 🟡 SM3 回退 MD5 时 Content-Type 不匹配

**问题**：SM3 摘要认证失败自动回退 MD5，但 Content-Type charset 可能仍是 `gb18030` → 2016 设备可能不识别。

### F133 🟡 `SipTlsProperties` 默认 `enabled=false` — 与 2022 规范"宜支持 TLS"不一致

**问题**：规范说"宜支持"，代码默认关闭 → 部署时易遗漏。

### F134 🟢 建议增加 "协议版本矩阵" 配置

**建议**：维护 `Versions.yaml` 描述 X-GB-ver 到各功能开关的映射。

---

## 三、第 10 轮 — 运维与可观测性

| 等级 | 数量 |
|------|------|
| 🟠 严重 | 1 |
| 🟡 一般 | 3 |
| 🟢 建议 | 2 |
| **合计** | **6** |

### F135 🟠 所有 SIP 发送失败无 metrics 埋点

**文件**：`SIPCommander2022Supplement.sendSipMessage`
**问题**：发送成功/失败无计数器 → 无法配置告警阈值。

### F136 🟡 `ApiDeviceControlController` 接口无统一请求 ID

**问题**：前后端调用链无 traceId → 日志关联困难。

### F137 🟡 `SipTlsProperties` 密钥库密码无轮换机制

**问题**：无密码过期提示/轮换触发。

### F138 🟡 监控端点缺失

**问题**：无 `/actuator/health` 集成（Spring Boot Actuator）。

### F139 🟢 建议增加 Smoke Test 脚本

**建议**：`scripts/smoke_test.sh` 执行 curl 验证 8 个新端点。

### F140 🟢 建议 `sipCharset` 在 INFO 启动日志中显式打印

**建议**：方便运维确认 GB18030 已生效。

---

## 四、三轮合计统计

| 轮次 | 🔴 | 🟠 | 🟡 | 🟢 | 合计 |
|------|----|----|----|----|------|
| R8 | 0 | 2 | 3 | 2 | 7 |
| R9 | 0 | 1 | 2 | 1 | 4 |
| R10 | 0 | 1 | 3 | 2 | 6 |
| **合计** | **0** | **4** | **8** | **5** | **17** |

**累计**：10 轮，118+17=**135 个问题**。已应用审计方法约 **108 种**。

**收敛趋势**：
```
R1: 25 → R2: 22 → R3: 18 → R4: 16 → R5: 12 → R6: 15 → R7: 10 → R8-10: 17
```
新增问题密度从 25/轮 降至 ~8/轮，但仍未收敛。致命问题 R7-R10 全部为 0。

---

**第 8/9/10 轮审计结束。** 将继续 R11-R15 逐模块深度审计，直至连续多轮零新增。
