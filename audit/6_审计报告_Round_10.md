# WVP 合规性升级改造代码审计 — Round 10

> **审计焦点**：最终复查、遗漏检查

---

## P3 (Low)

### R10-P3-001: MansrtspHelper.java — ALLOWED_SCALES 使用 double 数组而非 BigDecimal

**文件**：`news/.../MansrtspHelper.java` L45
**维度**：F07
**问题**：`ALLOWED_SCALES` 为 `double[]`，浮点数精度问题可能导致 `isValidScale(0.25)` 在某些 JVM 下返回 false。虽然 `Double.compare` 可以处理大部分情况，但不是绝对可靠。

**修复建议**：可接受。如需绝对精度，使用 `BigDecimal`。

---

### R10-P3-002: 多个 Handler 类中 responseAck 方法重复实现

**文件**：`SnapshotConfigMessageHandler.java` L357, `CruiseTrackQueryMessageHandler.java` L325, `DeviceUpgradeResultNotifyMessageHandler.java` L262, `SnapshotFinishedNotifyMessageHandler.java` L259
**维度**：G02
**问题**：4 个 Handler 类中都有几乎相同的 `responseOk`/`respondAck` 方法，代码重复。

**修复建议**：提取为公共基类或工具方法。

---

## Round 10 统计

| 严重性 | 数量 |
|--------|------|
| P0 | 0 |
| P1 | 0 |
| P2 | 0 |
| P3 | 2 |
| **总计** | **2** |
