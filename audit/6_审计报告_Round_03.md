# WVP 合规性升级改造代码审计 — Round 3

> **审计日期**：2026-06-30
> **审计焦点**：文档一致性、协议合规性深度检查、边界条件

---

## P0 (Critical)

### R3-P0-001: SIPCommander2022Supplement.java — PTZ精准控制 zoom 格式化与规范不一致

**文件**：`backend/.../SIPCommander2022Supplement.java` L110
**维度**：E14
**问题**：
```java
xml.append("<Zoom>").append(String.format("%.1f", zoom)).append("</Zoom>\r\n");
```
1. 元素名 `<Zoom>` 大写 Z。但开发方案注释（L88）写道：`zoom（小写）`。应改为 `<zoom>`。
2. `String.format("%.1f", zoom)` 使用默认 Locale，在某些 Locale（如德语）下小数点为逗号（`1,5`），导致 XML 解析错误。

**影响**：设备端解析失败；国际化环境下数值格式错误。
**规范依据**：2022 版 A.2.3.1.11。
**修复建议**：改为 `<zoom>`；使用 `String.format(Locale.US, "%.1f", zoom)`。

---

### R3-P0-002: SIPCommander2022Supplement.java — pan/Tilt 格式化未指定 Locale

**文件**：`backend/.../SIPCommander2022Supplement.java` L108-109
**维度**：F15
**问题**：
```java
xml.append("<Pan>").append(String.format("%.2f", pan)).append("</Pan>\r\n");
xml.append("<Tilt>").append(String.format("%.2f", tilt)).append("</Tilt>\r\n");
```
`String.format("%.2f", ...)` 未指定 Locale，在非英语 Locale 下可能输出逗号小数点。

**修复建议**：统一使用 `String.format(Locale.US, "%.2f", ...)`。

---

## P1 (High)

### R3-P1-001: ApiDeviceControlController.java — 设备升级 API 使用 POST 但 fileUrl 通过 @RequestParam 传参

**文件**：`backend/.../ApiDeviceControlController.java` L269-277
**维度**：C17
**问题**：设备升级使用 `@PostMapping` + `@RequestParam`，意味着参数通过 URL query string 传递。`fileUrl` 可能是很长的 URL，放在 query string 中可能超过 URL 长度限制（通常 2048 字符）。

此外，`fileUrl` 中如果包含 `&` 等特殊字符，会导致 query string 解析错误。

**修复建议**：改为 `@RequestBody` 接收 JSON 体。

---

### R3-P1-002: GbCode2022.java — isValidAdminCode 省级码段范围与设计文档不一致

**文件**：`news/.../GbCode2022.java` L91-95
**维度**：E17
**问题**：
```java
private static final Set<String> VALID_PROVINCE_CODES = new HashSet<>(Arrays.asList(
    "11", "12", "13", "14", "15", "21", "22", "23", "31", "32", "33", "34", "35", "36", "37",
    "41", "42", "43", "44", "45", "46", "50", "51", "52", "53", "54", "61", "62", "63", "64", "65"
));
```
设计文档第 13.5 节说"省级码段范围 11~65"，但代码使用枚举值列表。枚举列表中缺少 `71`（台湾）和 `81`/`82`（港澳），但这些在 2016 版中也不存在。

需查证 2022 规范附录 E 确认完整码段。当前实现与设计文档描述一致（11~65），但枚举方式 vs 范围方式不同。

**影响**：低风险，但需确认是否遗漏新增码段。
**修复建议**：查证规范原文确认。

---

### R3-P1-003: SdpFieldHelper.java — buildDownloadSField 返回 "DoWnload" 拼写需确认

**文件**：`news/.../SdpFieldHelper.java` L72
**维度**：E05
**问题**：
```java
public static final String SDP_S_DOWNLOAD = "DoWnload";
```
设计文档第 7.1 节明确指出 2022 版将 s 字段从 "Download" 修正为 "DoWnload"（大写 W）。但需查证规范原文确认。

核查报告改造项4 也记录了此问题。当前实现与设计文档一致。

**状态**：已确认，无需修改。

---

### R3-P1-004: MansrtspHelper.java — ALLOWED_SCALES 缺少 0 值

**文件**：`news/.../MansrtspHelper.java` L48-56
**维度**：E09
**问题**：
```java
private static final List<Double> ALLOWED_SCALES = Collections.unmodifiableList(
    Arrays.asList(-4.0, -2.0, -1.0, -0.5, 0.25, 0.5, 1.0, 2.0, 4.0)
);
```
Scale=0 在 RTSP 中表示暂停（PAUSE）。但 GB/T 28181 使用 MANSRTSP 协议，暂停可能通过单独的 PAUSE 命令处理，而非 Scale=0。

需查证 2022 版 A.2.4 确认 Scale=0 是否合法。当前实现不允许 Scale=0。

**影响**：如果规范允许 Scale=0，则暂停功能不可用。
**修复建议**：查证规范原文。

---

### R3-P1-005: SM3DigestHelper.java — isSm3Available 通过异常判断可用性

**文件**：`news/.../SM3DigestHelper.java` L60-90
**维度**：F01
**问题**：
```java
public static boolean isSm3Available() {
    try {
        MessageDigest.getInstance("SM3");
        return true;
    } catch (NoSuchAlgorithmException e) {
        // ...
    }
    // 尝试 BouncyCastle
    try {
        Class<?> bcClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
        // ...
    } catch (ClassNotFoundException e) {
        // ...
    }
    return false;
}
```
每次调用 `isSm3Available()` 都会尝试创建 MessageDigest 实例，性能差。应在静态初始化块中判断一次并缓存。

**影响**：如果 `digestWithFallback` 频繁调用，性能下降。
**修复建议**：使用静态 final boolean 缓存判断结果。

---

## P2 (Medium)

### R3-P2-001: SipCharsetHelper.java — escapeXml 未处理控制字符

**文件**：`news/.../SipCharsetHelper.java` L232-249
**维度**：C01
**问题**：`escapeXml` 仅转义 5 个 XML 特殊字符（`& < > " '`），未处理控制字符（如 `\0`、`\r`、`\n` 等）。如果用户输入包含控制字符，可能导致 XML 解析异常。

**修复建议**：过滤或转义控制字符（除 `\t \r \n` 外）。

---

### R3-P2-002: SipMessageFilter.java — ALLOWED_METHODS 缺少 SUBSCRIBE

**文件**：`news/.../SipMessageFilter.java` L40-50
**维度**：E12
**问题**：
```java
private static final Set<String> ALLOWED_METHODS = new HashSet<>(Arrays.asList(
    "REGISTER", "INVITE", "ACK", "BYE", "CANCEL", "MESSAGE",
    "INFO", "OPTIONS", "NOTIFY", "PRACK"
));
```
GB/T 28181 事件订阅使用 SUBSCRIBE 方法（2022 版 9.11）。当前白名单缺少 SUBSCRIBE，将导致事件订阅请求被拒绝。

**规范依据**：2022 版 9.11，设计文档第 9.3 节。
**修复建议**：添加 "SUBSCRIBE" 到白名单。

---

### R3-P2-003: GBProtocolVersionHelper.java — addMonitorUserIdentity 头域值硬编码

**文件**：`news/.../GBProtocolVersionHelper.java` L196-207
**维度**：G06
**问题**：
```java
public static void addMonitorUserIdentity(Request request, String identity) {
    // ...
    Header header = SipFactory.getInstance().createHeaderFactory()
        .createHeader("Monitor-User-Identity", identity != null ? identity : "0");
    request.addHeader(header);
}
```
默认值 `"0"` 硬编码。根据设计文档第 13.9 节，Monitor-User-Identity 取值应为操作员 ID 或 "0"（表示匿名监控）。默认值合理但应定义为常量。

**修复建议**：提取为 `private static final String DEFAULT_MONITOR_IDENTITY = "0"`。

---

### R3-P2-004: SipTlsProperties.java — keyStorePassword 和 trustStorePassword 以 String 存储

**文件**：`news/.../SipTlsProperties.java` L183, L189
**维度**：C04
**问题**：虽然提供了 `getKeyStorePasswordChars()` 返回 `char[]`（L329），但密码本身以 `String` 字段存储。Java String 不可变，创建后无法被 GC 清除，密码会常驻内存直到字符串池回收。

虽然 `@ConfigurationProperties` 绑定需要 setter（接受 String），但可以在 setter 中立即转为 `char[]` 存储。

**修复建议**：内部存储改为 `char[]`，setter 接受 String 后立即转存并清除原 String。

---

### R3-P2-005: ApiDeviceControlController.java — PTZ精准状态查询使用 POST 方法

**文件**：`backend/.../ApiDeviceControlController.java` L378
**维度**：G09
**问题**：
```java
@PostMapping("/ptz_precise_status_query/{deviceId}/{channelId}")
```
查询操作应使用 GET 方法（幂等、可缓存）。但这里使用了 POST。

**修复建议**：改为 `@GetMapping`。

---

## Round 3 统计

| 严重性 | 数量 |
|--------|------|
| P0 | 2 |
| P1 | 5 |
| P2 | 5 |
| **总计** | **12** |
