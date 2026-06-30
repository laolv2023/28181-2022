# WVP 合规性升级改造代码审计 — Round 6

> **审计焦点**：日志安全、配置完整性、测试覆盖

---

## P2 (Medium)

### R6-P2-001: SIPCommander2022Supplement.java — sendSipMessage catch 块日志泄露 channelId

**文件**：`backend/.../SIPCommander2022Supplement.java` L441
**维度**：C05
**问题**：
```java
log.error("[SIP发送] 消息发送失败: device={}, channelId={}", device.getDeviceId(), channelId, e);
```
`deviceId` 和 `channelId` 是设备编码，属于系统标识信息。在 ERROR 级别日志中输出是可接受的（用于排障），但如果日志被导出到外部系统，可能暴露设备拓扑。

**修复建议**：可接受。如果需高安全级别，脱敏处理。

---

### R6-P2-002: ApiConfigController.java — SECURITY_CONFIG 缺少 sipCharset 配置项处理

**文件**：`backend/.../ApiConfigController.java` L83-117
**维度**：E15
**问题**：前端 SecurityConfig.vue 发送 `sipCharset` 配置项（L68: `config.sipCharset`），但后端 `saveSecurity` 方法中没有处理 `sipCharset` 的分支。配置会被静默忽略。

**修复建议**：添加 `sipCharset` 的处理逻辑。

---

## Round 6 统计

| 严重性 | 数量 |
|--------|------|
| P0 | 0 |
| P1 | 0 |
| P2 | 2 |
| **总计** | **2** |
