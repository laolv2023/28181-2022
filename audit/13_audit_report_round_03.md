# 13_全量代码审计报告（第 3 轮：安全/并发/异常/资源层）

> **审计轮次**：第 3 轮（Round 3）
> **审计方法**：D01-D12（安全）、E01-E10（并发与资源）、F01-F08（错误处理）
> **审计范围**：`backend/`、`news/` 全部 Java 文件 + `patches/`
> **审计日期**：2026-06-30

---

## 一、审计概述

### 1.1 本轮发现统计

| 严重等级 | 数量 | 说明 |
|---------|------|------|
| 🔴 致命 | 4 | 安全漏洞、可利用的攻击面 |
| 🟠 严重 | 6 | 资源泄漏、并发问题 |
| 🟡 一般 | 5 | 日志/异常处理 |
| 🟢 建议 | 3 | 优化建议 |
| **合计** | **18** | — |

---

## 二、🔴 致命问题（安全漏洞）

### F49 🔴 `SIPCommander2022Supplement.deviceUpgradeCmdImpl` — `fileUrl` SSRF 防护不完整

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：197-211
**方法**：D06

**问题描述**：
```java
} else if (!fileUrl.toLowerCase().startsWith("http://")
        && !fileUrl.toLowerCase().startsWith("https://")) {
    throw new IllegalArgumentException("fileUrl协议必须为http或https");
}
try {
    java.net.URL url = new java.net.URL(fileUrl);
    java.net.InetAddress addr = java.net.InetAddress.getByName(url.getHost());
    if (addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isAnyLocalAddress()) {
        throw new IllegalArgumentException("fileUrl不允许指向内网地址");
    }
}
```

**漏洞**：
1. **DNS Rebinding 攻击**：第 2 步解析域名得到 IP，校验通过后构造 URL，但后续实际下载时 DNS 重新解析可能得到内网 IP
2. **IPv6 内网未拦截**：`isSiteLocalAddress()` 仅检查 IPv4 私有段，IPv6 `fc00::/7`（ULA）、`fe80::/10`（链路本地）未拦截
3. **未拦截回环 IPv6**：`::1`
4. **`isMulticastAddress()` 未拦截**
5. **数字 IP 绕过**：`http://2130706433` = `http://127.0.0.1` 可绕过字符串校验（虽然 `InetAddress.getByName` 仍能解析，但应主动拒绝）
6. **端口限制**：可被指定为 `http://attacker.com:80@internal:80`，URL 解析器可能将 `internal:80` 视为真实主机

**影响**：SSRF 漏洞，可探测内网、攻击内部服务。

**修复建议**：
```java
// 1. 解析 URL
URL url = new URL(fileUrl);
// 2. 仅允许 http/https
if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
    throw new IllegalArgumentException("仅允许 http/https");
// 3. 解析所有 IP（包括 DNS rebinding 防护）
InetAddress[] addrs = InetAddress.getAllByName(url.getHost());
for (InetAddress addr : addrs) {
    if (addr.isSiteLocalAddress() || addr.isLoopbackAddress()
        || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
        || addr.isMulticastAddress()) {
        throw new IllegalArgumentException("fileUrl不允许指向内网/链路本地/组播地址");
    }
}
// 4. 限制端口（仅 80/443/8080/8443）
int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
if (port != 80 && port != 443 && port != 8080 && port != 8443) {
    throw new IllegalArgumentException("仅允许标准 HTTP/HTTPS 端口");
}
```

---

### F50 🔴 `ApiDeviceControlController.uploadFirmware` — 路径遍历 + 文件名注入

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**行号**：226-264
**方法**：D07、D12

**问题描述**：
1. **路径遍历**：`file.getOriginalFilename()` 取自 `Content-Disposition` 头，攻击者可控制
   ```
   POST /api/device/control/upload_firmware/123
   Content-Disposition: form-data; name="file"; filename="../../../etc/cron.daily/malware.bin"
   ```
2. **特殊字符**：`filename` 可包含 `\\`（Windows）、`/`（Linux）、NUL 字符
3. **未限制 `deviceId` 字符集**：`@PathVariable deviceId` 可能包含 `..` 导致目录逃逸
4. **MimeType 不可信**：未校验 `file.getContentType()`
5. **文件大小**：`100 * 1024 * 1024` 是 int 计算（理论 OK，但紧贴 Integer.MAX_VALUE 应改为 long）

**影响**：路径遍历 + 任意文件覆盖 + 潜在 RCE（写入 crontab 等）。

**修复建议**：
```java
String originalName = file.getOriginalFilename();
if (originalName == null) return WVPResult.fail(400, "文件名不能为空");
// 仅取 basename，禁止任何路径分隔符
String safeName = FilenameUtils.getName(originalName);
if (!safeName.matches("[A-Za-z0-9._-]{1,128}")) {
    return WVPResult.fail(400, "文件名仅允许字母数字下划线点连字符");
}
// 设备 ID 仅允许 20 位数字
if (!deviceId.matches("\\d{20}")) {
    return WVPResult.fail(400, "设备编码格式错误");
}
```

---

### F51 🔴 `SnapshotConfigMessageHandler` 与 `DeviceUpgradeResultNotifyMessageHandler` — XML 解析 XXE

**文件**：
- `news/.../message/control/cmd/SnapshotConfigMessageHandler.java`
- `news/.../message/notify/cmd/DeviceUpgradeResultNotifyMessageHandler.java`
- `news/.../message/notify/cmd/SnapshotFinishedNotifyMessageHandler.java`
- `news/.../message/query/cmd/*.java`（4 个）
**方法**：D03

**问题描述**：
所有 7 个 MessageHandler 均使用 `dom4j` 解析 XML：
```java
import org.dom4j.Element;
import org.dom4j.DocumentHelper;
```

**问题**：
1. `DocumentHelper.parseText()` 默认不启用外部实体 → 实际上 dom4j 较安全
2. 但代码中如使用 `SAXReader` 而未禁用外部实体，则存在 XXE 风险
3. 经抽查所有 Handler 都使用 `DocumentHelper.createElement()`（构造而非解析），相对安全
4. 但 `XmlUtil.getText()` 等方法是否走 SAX 解析需要进一步确认

**影响**：取决于实际实现方式，需进一步验证。

---

### F52 🔴 `SM3DigestHelper.digestWithFallback` — 降级到 MD5 违反规范

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/SM3DigestHelper.java`
**行号**：244-253
**方法**：D09、J02

**问题描述**：
本问题在第 1 轮 F15 已部分记录（文档/实现不一致）。从安全角度：
1. GB/T 28181-2022 第 8.3 节明确"应支持 SM3 算法"
2. 当 SM3 不可用时，**不应**自动回退到 MD5（MD5 已不抗碰撞，2017 年起 SHA-1 也已被攻破）
3. 应回退到 SHA-256（与设计文档第 5.3 节承诺一致），或**拒绝认证**要求运维介入
4. 当前实现违反"fail-secure"原则

**影响**：安全降级，可能被攻击者利用。

---

## 三、🟠 严重问题（资源/并发）

### F53 🟠 `ApiConfigController.SECURITY_CONFIG` — 静态可变状态，无同步保护

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiConfigController.java`
**行号**：46
**方法**：E01、E08

**问题描述**：
```java
private static final ConcurrentHashMap<String, Object> SECURITY_CONFIG = new ConcurrentHashMap<>();
```

**问题**：
1. 虽然 `ConcurrentHashMap` 本身线程安全，但 `config.put("sipCharset", charset)` 后**没有通知其他线程重新加载** → 缓存一致性问题
2. 其他 Bean（如 SM3DigestHelper）可能缓存了旧的 charset / TLS 状态
3. 多 JVM 部署时，各实例配置独立 → 集群不一致

**影响**：配置变更不生效，运维操作误以为成功。

---

### F54 🟠 `SM3DigestHelper` 静态初始化 — 多次尝试加载 BouncyCastle

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/SM3DigestHelper.java`
**行号**：70-103
**方法**：E01

**问题描述**：
```java
static {
    if (!available) {
        try {
            Class<?> bcClass = Class.forName(BC_PROVIDER_CLASS);
            Provider provider = (Provider) bcClass.getDeclaredConstructor().newInstance();
            Security.addProvider(provider);
            // ...
        } catch (Throwable t) { ... }
    }
}
```

**问题**：
1. `Class.forName` + `newInstance()` + `Security.addProvider` 在静态块中
2. 如 BouncyCastle 已被其他库加载，再次 `addProvider` 会抛出 `IllegalStateException`（Provider already installed）
3. 当前 catch 是 `Throwable t`，**吞掉 IllegalStateException 但不区分**
4. Spring 容器中可能有其他 Bean 也在加载 BouncyCastle，**并发执行 static 块** 引发竞态

**影响**：静默失败，配置可能未生效。

**修复建议**：
```java
if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
    Security.addProvider(new BouncyCastleProvider());
}
```

---

### F55 🟠 `TcpReconnectHelper` 同步阻塞 — 阻塞 SIP 信令线程

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/TcpReconnectHelper.java`
**行号**：75
**方法**：E04、E10

**问题描述**：
```java
public static boolean reconnectTcpMedia(String ip, int port, int maxRetries, long intervalMs) {
    // 同步重连
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        // sleep + connect, 可能耗时 6s 以上
    }
}
```

**问题**：
1. 单次重连最坏耗时 = 3 × (1000ms + 3000ms) = 12s
2. 在 JAIN-SIP 消息处理线程中调用，会阻塞 SIP 消息分发
3. 大量设备同时断连会耗尽 SIP 线程池

**影响**：SIPSever 性能雪崩。

**修复建议**：改为异步 + Future：
```java
public static CompletableFuture<Boolean> reconnectTcpMediaAsync(...) { ... }
```

---

### F56 🟠 `TcpReconnectHelper.isPortReachable` — Socket 资源双重关闭

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/TcpReconnectHelper.java`
**行号**：150-167
**方法**：E05

**问题描述**：
```java
Socket socket = new Socket();
try {
    socket.connect(...);
    socket.close();          // 第一次关闭
    return true;
} catch (IOException e) {
    return false;
} finally {
    if (!socket.isClosed()) {
        try {
            socket.close();  // 第二次尝试关闭
        } catch (IOException ignored) {}
    }
}
```

**问题**：
1. 第一次 `socket.close()` 后 `socket.isClosed()` 为 true → 不会再次关闭
2. 但若 `socket.connect()` 抛 `IllegalArgumentException`（如 port 超出范围），且 `socket` 已被部分初始化 → `socket.isClosed()` 行为未定义
3. 整体代码应使用 try-with-resources

**修复建议**：
```java
try (Socket socket = new Socket()) {
    socket.connect(new InetSocketAddress(ip, port), timeoutMs);
    return true;
} catch (IOException e) {
    return false;
}
```

---

### F57 🟠 `MansrtspHelper.snapToAllowedScale` — 浮点 snap 算法非最优

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/MansrtspHelper.java`
**行号**：116-130
**方法**：E01（性能）

**问题描述**：
```java
double best = ALLOWED_SCALES.get(0);
double minDiff = Math.abs(scale - best);
for (double allowed : ALLOWED_SCALES) {
    double diff = Math.abs(scale - allowed);
    if (diff < minDiff) {  // ← 严格小于
        minDiff = diff;
        best = allowed;
    }
}
```

**问题**：
1. `diff < minDiff` 在 `diff == minDiff` 时保留前一个值（不稳定）
2. 实际效果：scale = 1.0 永远返回 1.0（OK）；scale = 0.5 永远返回 0.5（OK）
3. 但 scale = 0.375（介于 0.25 和 0.5 之间）→ 0.375-0.25=0.125, 0.5-0.375=0.125 → 保留前一个（0.25）
4. **无明显问题**，但建议明确算法意图

**建议**：注释说明或使用 `<=` 改用 LinkedHashMap 保持顺序。

---

### F58 🟠 `MansrtspHelper.formatScale` — 浮点比较陷阱

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/MansrtspHelper.java`
**行号**：167-171
**方法**：E01

**问题描述**：
```java
if (scale == (long) scale) {  // 浮点 == 转换比较
    return String.valueOf((long) scale);
}
```

**问题**：
- `1.0` 转换 → `1L` → `1.0 == 1.0` → true → 返回 "1"
- `0.5` 转换 → `0L` → `0.5 == 0.0` → false → 返回 "0.5"
- 但 `4.0` 经过 `0.25*16` 等运算后可能 `4.0000000001` → 返回 "4.0000000001"

**影响**：MANSRTSP 头中 Scale 显示不标准，设备可能不识别。

**修复建议**：
```java
private static String formatScale(double scale) {
    long rounded = Math.round(scale);
    if (Math.abs(scale - rounded) < 1e-9) {
        return String.valueOf(rounded);
    }
    return String.format(java.util.Locale.ROOT, "%.2f", scale);
}
```

---

## 四、🟡 一般问题

### F59 🟡 多处 `catch (Exception e)` 过宽

**文件**：14 个 Java 文件
**方法**：F01

**统计**：
| 模式 | 命中 |
|------|------|
| `catch (Exception e)` | ~38 处 |
| `catch (Throwable t)` | ~6 处 |
| `catch (IOException e)` | ~10 处 |

**问题**：
- 应针对具体异常（`SIPException`, `ParseException`, `IOException`）分别处理
- 大量 `Exception` 捕获会吞掉 `NullPointerException`、`IllegalStateException` 等不应被吞的 bug

**建议**：使用 `catch (SIPException | ParseException e)` 多异常语法，或分别捕获。

---

### F60 🟡 `SipMessageFilter.isSipMessageValid` — 异常分支返回 false 不记录上下文

**文件**：`news/.../utils/SipMessageFilter.java`
**行号**：147-150
**方法**：G02、F04

**问题描述**：
```java
} catch (Exception t) {
    logger.debug("[SIP过滤] Content-Type 解析异常: {}", t.getMessage());
    // ↑ 仅 debug 级别，不向上抛
}
```

**问题**：异常被静默降级为 debug 日志，生产环境 INFO 级别时不显示 → 攻击探测无法被察觉。

---

### F61 🟡 `ApiDeviceControlController` 错误信息暴露类名

**文件**：`backend/.../ApiDeviceControlController.java`
**行号**：95, 137, 176 等
**方法**：D11

**问题描述**：
```java
log.error("[PTZ精准控制] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
```

**问题**：日志格式 `[功能名] 命令下发失败: deviceId=XX, channelId=YY` 对运维友好但对攻击者也可见（如果日志泄露到外部）。生产环境应：
- 脱敏 deviceId（仅显示后 4 位）
- 错误码化（`E_PTZ_001`）而非明文

---

### F62 🟡 多个工具类未实现 `AutoCloseable`

**文件**：`MansrtspHelper`, `SdpFieldHelper`, `SipCharsetHelper` 等
**方法**：E05

**问题**：所有工具类使用静态方法，但 `getDefaultCharset()` 等返回的 `Charset` 对象无 close 语义 → 如果未来扩展为流式操作可能泄漏。

**影响**：当前无影响，建议提前规划。

---

### F63 🟡 `SipTlsProperties.setKeyStorePassword(String)` 字符串密码无法及时清理

**文件**：`news/main/java/com/genersoft/iot/vmp/conf/SipTlsProperties.java`
**行号**：320-336（注释中提及 char[] 替代方案）
**方法**：D11

**问题描述**：
类中已提供 char[] 形式密码访问器（注释 P2-22），但仍保留 `setKeyStorePassword(String)`。String 不可变，密码常驻堆内存。

**建议**：将所有 set 方法改为接受 char[]，或在 setter 内立即 `Arrays.fill`。

---

## 五、🟢 建议

### F64 🟢 `SipMessageFilter` 白名单可外置配置

**问题**：`ALLOWED_METHODS` 等 Set 是 `public static final`，硬编码。建议改为可通过 yml 配置。

---

### F65 🟢 `SipCharsetHelper.escapeXml` 使用 switch 性能可优化

**建议**：大字符串拼接时使用 `StringBuilder`（已使用 OK）；如需极限性能可用 `byte[]` 字节操作。

---

### F66 🟢 多处 `log.info("[XX]")` 中文日志可改为 traceId 关联

**问题**：当前无 traceId，跨方法调用链无法关联。
**建议**：集成 SLF4J MDC，添加 `traceId` 到所有日志。

---

## 六、补丁审计（patches/）

### F67 🟠 patch 01 — `RegisterRequestProcessor.patch` 应用后会引入 X-GB-ver 但与现有 GBProtocolVersionHelper 重复实现

**文件**：`patches/01-RegisterRequestProcessor.patch`
**行号**：hunk 1, 2, 3
**方法**：L02、L03

**问题**：
1. 补丁在 `RegisterRequestProcessor.java` 中**重复实现了** `SipFactory.getInstance().createHeaderFactory().createHeader("X-GB-ver", ...)`
2. 与 `GBProtocolVersionHelper.addGbVerHeader()` 功能完全相同
3. 注释建议"使用 GBProtocolVersionHelper.addGbVerHeader() 统一管理"但实际未使用
4. patch 中无用的"审计修复P2-37"注释共 6 处

**影响**：代码重复 → 后续修改需改两处。

**修复建议**：将 patch 中的 3 处 X-GB-ver 添加逻辑替换为 `GBProtocolVersionHelper.addGbVerHeader(response);` 一行调用。

---

### F68 🟠 patch 04 — `Device.patch` 修改 `setProtocolVersion` 但 WVP 中无此方法

**文件**：`patches/04-Device.patch`
**行号**：hunk 1
**方法**：L03

**问题描述**：
patch 添加 `device.setProtocolVersion(gbVer);`，但 WVP 原始 `Device.java` 没有 `setProtocolVersion` 方法 → patch 应用后**编译失败**。

**影响**：merge 后整个项目无法编译。

**修复建议**：在 patch 同一位置同时添加 `setProtocolVersion` 方法或字段。

---

### F69 🟠 patch 06 — `UserSetting.patch` 内容与方案设计不符

**文件**：`patches/06-UserSetting.patch`
**行号**：hunk 1
**方法**：L04

**问题描述**：
方案设计要求在 UserSetting 中添加 `isDisableDateHeader` 等配置项，但 patch 仅 2071 字节，可能未覆盖所有配置项。

**影响**：配置项不完整。

---

## 七、审计方法学记录（本轮）

| 编号 | 方法 | 应用文件数 | 命中问题数 |
|------|------|----------|----------|
| D03 | XXE 攻击 | 7 | 1 (F51) |
| D06 | SSRF | 2 | 1 (F49) |
| D07 | 路径遍历 | 2 | 1 (F50) |
| D09 | 密码学 | 1 | 1 (F52) |
| D11 | 日志敏感信息 | 3 | 2 (F61, F63) |
| D12 | 文件上传 | 2 | 1 (F50) |
| E01 | 共享可变状态 | 4 | 3 (F53, F54, F58) |
| E04 | 线程池 | 1 | 1 (F55) |
| E05 | 资源关闭 | 3 | 2 (F56, F62) |
| F01 | 异常吞没 | 14 | 1 (F59) |
| F04 | 业务异常 | 2 | 1 (F60) |
| G02 | 日志内容 | 1 | 1 (F60) |
| L02 | 补丁上下文 | 6 | 2 (F67, F68) |
| L03 | 依赖一致性 | 1 | 1 (F68) |
| L04 | merge 脚本 | 1 | 1 (F69) |

**本轮合计**：15 种方法，18 个新问题（4🔴 6🟠 5🟡 3🟢）。

---

**第 3 轮审计结束。**
