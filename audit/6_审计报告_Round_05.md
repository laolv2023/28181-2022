# WVP 合规性升级改造代码审计 — Round 5

> **审计焦点**：边界条件、异常路径、测试代码质量

---

## P1 (High)

### R5-P1-001: ApiDeviceControlController.java — targetTrack 方法缺少参数校验

**文件**：`backend/.../ApiDeviceControlController.java` L120-140
**维度**：B07
**问题**：`targetTrack` 接受 `action` 参数但后端未校验取值范围。前端 API（frontEnd.js L67）校验了 `['Auto', 'Manual']`，但后端无校验，可直接传入任意字符串。

**修复建议**：后端添加 `action` 取值白名单校验。

---

## P2 (Medium)

### R5-P2-001: SnapshotConfigMessageHandler.java — snapNum 参数未校验范围

**文件**：`news/.../SnapshotConfigMessageHandler.java` L200
**维度**：B07
**问题**：从 XML 解析的 `snapNum` 未校验范围（1~10）。恶意设备可发送 snapNum=999999 导致异常行为。

**修复建议**：添加范围校验。

---

### R5-P2-002: SdpFieldHelper.java — formatSpeed 中浮点数比较精度问题

**文件**：`news/.../SdpFieldHelper.java` L360-364
**维度**：F07
**问题**：
```java
if (speed == (long) speed) {
    return String.valueOf((long) speed);
}
```
浮点数 `==` 比较可能因精度问题导致判断错误。例如 `1.5` 在某些运算后可能变成 `1.4999999...`，`(long) 1.4999... = 1`，`1.4999... == 1` 为 false（正确），但 `4.000000001` 的 `(long)` 是 4，`4.000000001 == 4` 为 false（也正确）。

实际风险低，但应使用 `Math.abs(speed - Math.round(speed)) < 1e-9` 替代。

**修复建议**：使用 epsilon 比较或 BigDecimal。

---

### R5-P2-003: DeviceControlType.java — typeOf 方法返回 null 而非 Optional

**文件**：`news/.../DeviceControlType.java` L186
**维度**：B01
**问题**：`typeOf` 返回 null 表示未匹配，调用方需做 null 检查。使用 `Optional<DeviceControlType>` 更安全。

**修复建议**：改为返回 `Optional<DeviceControlType>`（可选改进，不强制）。

---

## Round 5 统计

| 严重性 | 数量 |
|--------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 3 |
| **总计** | **4** |
