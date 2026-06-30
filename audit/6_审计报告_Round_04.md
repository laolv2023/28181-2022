# WVP 合规性升级改造代码审计 — Round 4

> **审计日期**：2026-06-30
> **审计焦点**：并发安全、资源管理、边界条件深入检查

---

## P1 (High)

### R4-P1-001: ApiConfigController.java — saveSecurity 中 SECURITY_CONFIG 复合操作非原子

**文件**：`backend/.../ApiConfigController.java` L83-117
**维度**：D02
**问题**：
```java
public Map<String, Object> saveSecurity(@RequestBody Map<String, Object> config) {
    if (config.containsKey("sm3DigestEnabled")) {
        Object val = config.get("sm3DigestEnabled");
        if (val instanceof Boolean) {
            SECURITY_CONFIG.put("sm3DigestEnabled", val);  // 逐项 put
        }
    }
    if (config.containsKey("sipTlsEnabled")) {
        // ...
        SECURITY_CONFIG.put("sipTlsEnabled", val);
    }
    // ... 继续逐项 put
}
```
多个 `put` 操作非原子。在并发调用 `saveSecurity` 和 `getSecurity` 时，`getSecurity` 可能看到部分更新的配置（如 SM3 已更新但 TLS 未更新）。

**修复建议**：先构造完整的新 Map，使用 `SECURITY_CONFIG.putAll(newConfig)` 或替换引用。

---

### R4-P1-002: SIPCommander2022Supplement.java — snCounter 静态字段在多实例间共享

**文件**：`backend/.../SIPCommander2022Supplement.java` L64
**维度**：D04
**问题**：
```java
private static final AtomicInteger snCounter = new AtomicInteger(0);
```
`snCounter` 是静态字段，所有 SIPCommander2022Supplement 实例共享。在 WVP 多设备场景中，不同设备的 SN 会交叉递增（设备 A 收到 SN=1，设备 B 收到 SN=2，设备 A 收到 SN=3...）。虽然 SN 在单个命令内唯一，但不满足"每个设备 SN 独立递增"的常见实现预期。

**影响**：功能正确但不符合预期。规范未明确要求每设备独立 SN，但 WVP 原有实现可能是按设备递增的。
**修复建议**：确认 WVP 原有 SIPCommander 的 SN 逻辑，保持一致。

---

### R4-P1-003: CruiseTrackQueryMessageHandler.java — buildListResponseXml 中 CruiseTrackListID 未转义

**文件**：`news/.../CruiseTrackQueryMessageHandler.java` L252
**维度**：C01
**问题**：
```java
xml.append("<CruiseTrackListID>").append(
    ObjectUtils.isEmpty(cruiseTrackListId) ? "1" : cruiseTrackListId
).append("</CruiseTrackListID>\r\n");
```
`cruiseTrackListId` 直接拼入 XML，未调用 `SipCharsetHelper.escapeXml()`。虽然该值应为数字，但如果来自外部输入，可能包含 XML 特殊字符。

**修复建议**：使用 `SipCharsetHelper.escapeXml(cruiseTrackListId)`。

---

## P2 (Medium)

### R4-P2-001: DeviceUpgradeResultNotifyMessageHandler.java — respondAck 中 getServerTransaction 可能抛异常

**文件**：`news/.../DeviceUpgradeResultNotifyMessageHandler.java` L264
**维度**：F01
**问题**：
```java
ServerTransaction serverTransaction = getServerTransaction(evt);
```
`getServerTransaction` 是父类方法，如果事务已存在或创建失败，可能抛出异常。虽然外层有 try-catch，但 catch 中仅记录日志，未回复任何响应，发送方会超时重发。

**修复建议**：在 catch 中尝试发送最基本的 500 响应（即使事务可能有问题）。

---

### R4-P2-002: SnapshotFinishedNotifyMessageHandler.java — parseSnapshotList 使用 @SuppressWarnings("unchecked")

**文件**：`news/.../SnapshotFinishedNotifyMessageHandler.java` L207
**维度**：A15
**问题**：
```java
@SuppressWarnings("unchecked")
private List<String> parseSnapshotList(Element rootElement) {
```
dom4j 的 `elements()` 方法返回 `List<Element>`，不需要 `@SuppressWarnings("unchecked")`。该注解可能掩盖了其他类型安全问题。

**修复建议**：移除 `@SuppressWarnings("unchecked")`，确认无类型警告。

---

### R4-P2-003: TcpReconnectHelper.java — tryConnect 连接超时硬编码 3 秒

**文件**：`news/.../TcpReconnectHelper.java` L137
**维度**：G06
**问题**：
```java
socket.connect(new InetSocketAddress(ip, port), 3000);
```
连接超时硬编码为 3000ms，不可配置。不同网络环境（高延迟卫星链路等）可能需要更长的超时。

**修复建议**：通过参数或配置项传入超时值。

---

### R4-P2-004: SipCharsetHelper.java — encodeGb18030 方法中 catch UnsupportedEncodingException 后返回空字节数组

**文件**：`news/.../SipCharsetHelper.java` L97-100
**维度**：F15
**问题**：
```java
try {
    return text.getBytes(GB18030_CHARSET_NAME);
} catch (UnsupportedEncodingException e) {
    logger.error("[字符集] GB18030编码不支持", e);
    return new byte[0];
}
```
GB 18030 是 Java 标准字符集（自 JDK 6 起），理论上不会抛出 UnsupportedEncodingException。但如果抛出，返回空字节数组会导致下游解析收到空消息体，可能引发难以排查的问题。

**修复建议**：改为抛出 RuntimeException，让调用方感知错误。

---

## Round 4 统计

| 严重性 | 数量 |
|--------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 4 |
| **总计** | **7** |
