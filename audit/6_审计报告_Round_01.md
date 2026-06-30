# WVP 合规性升级改造代码审计 — Round 1

> **审计日期**：2026-06-30
> **审计范围**：backend/、news/、patches/、ui/、tests/、scripts/ 全部代码
> **审计方法**：105 条检查项逐项执行

---

## P0 (Critical) — 编译错误/安全漏洞

### R1-P0-001: CertAuthHelper.java 存在编译错误 — Javadoc 外部 stray `*` 行

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/CertAuthHelper.java` L33
**维度**：A02
**问题**：
```java
 * @author wvp-upgrade
 */
 * 审计修复P1-06: 此接口为可选功能(宜支持), 生产环境按需提供实现类
public interface CertAuthHelper {
```
第 33 行 `* 审计修复P1-06:...` 位于 Javadoc `*/` 结束符之后、`public interface` 之前，不是一个合法的 Java 语句。这是一个**编译错误**，该文件无法通过编译。

**同样的问题还出现在**：
- `DataIntegrityHelper.java` L26：`* 审计修复P1-06:...` 在 `*/` 之后
- `GB35114Helper.java` L34：`* 审计修复P1-06:...` 在 `*/` 之后

**影响**：3 个文件无法编译，导致整个 auth 包无法构建。
**修复建议**：将 stray `*` 行移到 Javadoc 内部（`*/` 之前），或改为普通注释 `//`。
**规范依据**：Java 语言规范 §3.7 注释。

---

### R1-P0-002: SIPCommander2022Supplement.java — ptzPreciseCmdImpl XML 元素名大小写与规范不一致

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java` L108
**维度**：E14
**问题**：
```java
xml.append("<Pan>").append(String.format("%.2f", pan)).append("</Pan>\r\n");
```
XML 元素名为 `<Pan>`（大写 P），但根据开发方案注释（L85-86）和 2022 规范 A.2.3.1.12，PTZ精准控制中水平角度元素名应为 `<pan>`（全小写 p）。

**文件**中注释明确写道（L85-86）：
```
 * XML子元素：pan（小写）、Tilt（大写T）、zoom（小写）
```
但代码实现使用了 `<Pan>`，与注释和规范不一致。

**影响**：设备端解析 XML 时可能无法识别 `<Pan>` 元素，导致 PTZ 精准控制功能失效。
**规范依据**：2022 版 A.2.3.1.11，设计文档第 10.1 节。
**修复建议**：将 `<Pan>` 改为 `<pan>`，`</Pan>` 改为 `</pan>`。

---

### R1-P0-003: SIPCommander2022Supplement.java — 存储卡格式化 XML 元素名大小写不一致

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java` L140
**维度**：E16
**问题**：
```java
xml.append("<FormatSdcard/>\r\n");
```
XML 元素名为 `<FormatSdcard>`，但 `DeviceControlType` 枚举中（L98）定义为 `"FormatsDcard"`（注意 `s`）：
```java
FORMAT_SDCARD("FormatsDcard", "存储卡格式化"),
```
两者不一致。方法注释（L126）写道：`XML 元素名: FormatSdcard`，但枚举值为 `FormatsDcard`。

需查证 2022 规范原文确定正确名称。根据核查报告引用的 spec_2022.txt，元素名应为 `FormatsDcard`（带 s）。

**影响**：XML 元素名与枚举定义不一致，可能导致设备端解析失败。
**规范依据**：2022 版 A.2.3.1.13，核查报告改造项 8。
**修复建议**：统一为 `FormatsDcard`。

---

### R1-P0-004: SnapshotConfigMessageHandler.java — cmdType 字段未定义

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/SnapshotConfigMessageHandler.java` L172
**维度**：A01
**问题**：
```java
controlMessageHandler.addHandler(cmdType, this);
log.info("[图像抓拍配置] 处理器注册成功, CmdType={}", cmdType);
```
引用了变量 `cmdType`，但类中定义的常量名为 `CMD_TYPE`（L88，`private static final String CMD_TYPE = "SnapConfig";`）。Java 区分大小写，`cmdType`（小写 c）未定义，将导致编译错误。

**影响**：文件无法编译，图像抓拍配置命令处理器无法注册。
**修复建议**：将 `cmdType` 改为 `CMD_TYPE`。

---

### R1-P0-005: CruiseTrackQueryMessageHandler.java — cmdType 字段未定义

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/CruiseTrackQueryMessageHandler.java` L140, L141, L248, L289
**维度**：A01
**问题**：
```java
queryMessageHandler.addHandler(cmdType, this);
log.info("[巡航轨迹查询] 处理器注册成功, CmdType={}", cmdType);
```
同样引用了未定义的 `cmdType`，类中定义的是 `CMD_TYPE`。多处引用（L140, L141, L248, L289）均使用小写 `cmdType`。

**影响**：文件无法编译。
**修复建议**：将所有 `cmdType` 改为 `CMD_TYPE`。

---

### R1-P0-006: SIPCommander2022Supplement.java — 固件上传缺少文件类型和路径遍历校验

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java` L219-243
**维度**：C03
**问题**：`uploadFirmwareFileImpl` 方法注释（L70）声称已修复 P1-13（校验文件大小、类型、路径遍历），但实际代码**未实现任何校验**：
1. 文件大小未校验（Controller 层有 200MB 限制，但 Impl 层没有）
2. 文件类型未校验（注释提到 .bin/.img/.zip，但代码未检查扩展名）
3. 路径遍历未校验（`originalName` 直接用于提取扩展名，`deviceId` 直接拼接到路径中）

```java
// 注释声称修复了，但代码无校验
// 审计修复P1-13: 固件上传需校验文件大小(≤100MB)、类型(.bin/.img/.zip)、路径遍历
String originalName = file.getOriginalFilename();
String ext = "";
if (originalName != null && originalName.contains(".")) {
    ext = originalName.substring(originalName.lastIndexOf("."));
}
String savedName = UUID.randomUUID().toString() + ext;
Path targetPath = uploadDir.resolve(savedName);
```

`deviceId` 可包含 `../` 路径遍历字符，导致文件被写到 `/tmp/wvp/firmware` 之外。

**影响**：攻击者可上传任意文件类型，或通过 deviceId 路径遍历写入任意目录。
**修复建议**：
1. 校验文件扩展名白名单（.bin/.img/.zip）
2. 校验 deviceId 仅包含数字和字母
3. 规范化路径后验证仍在预期目录内

---

### R1-P0-007: ApiDeviceControlController.java — fileUrl 参数未校验 SSRF

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java` L275, L289-290
**维度**：C17
**问题**：设备升级 API 的 `fileUrl` 参数直接传入 `cmder.deviceUpgradeCmd()`，代码中有大量重复注释 `审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址`（L243, L245, L263, L264, L274, L287, L289, L290, L291, L293, L294, L467, L471, L473, L478, L481），但**实际未实现任何校验**。

攻击者可传入 `fileUrl=http://169.254.169.254/latest/meta-data/` 或 `fileUrl=file:///etc/passwd`，导致设备去访问内网资源或本地文件。

**影响**：SSRF 漏洞，可探测内网或访问敏感资源。
**修复建议**：在 `deviceUpgradeCmdImpl` 中校验 fileUrl：
1. 仅允许 http/https 协议
2. 拒绝内网地址（10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16）

---

## P1 (High)

### R1-P1-001: ApiConfigController.java — 安全配置仅存储在内存中，重启丢失

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiConfigController.java` L45
**维度**：F10
**问题**：`SECURITY_CONFIG` 是 `static final ConcurrentHashMap`，数据仅在内存中。服务重启后所有安全配置恢复默认值。注释已承认此问题（L20-23, L32-33），但未修复。

**影响**：生产环境中，运维人员通过 API 修改的安全配置（如 SM3 开关、TLS 开关）在服务重启后丢失，可能导致安全降级。
**修复建议**：持久化到数据库或配置文件。

---

### R1-P1-002: ApiConfigController.java — 配置修改后不通知组件重新初始化

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiConfigController.java` L33
**维度**：F15
**问题**：注释声称 `审计修复P1-05: 配置修改后需通知组件重新初始化`，但代码未实现。修改 SM3/TLS/字符集配置后，实际运行的组件不会感知到变化。

**影响**：配置修改不生效，运维人员误以为已切换安全配置。
**修复建议**：实现配置变更事件通知机制。

---

### R1-P1-003: ApiDeviceControlController.java — 审计修复注释大量重复且未实现

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**维度**：G03
**问题**：`审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址` 这条注释在文件中出现了 **16 次**（L243, L245, L263, L264, L274, L287, L289, L290, L291, L293, L294, L467, L471, L473, L478, L481），但实际校验逻辑完全未实现。

这严重影响了代码可读性和可维护性，且制造了"已修复"的假象。

**影响**：代码质量极差，虚假修复标记误导后续开发者。
**修复建议**：删除重复注释，实现实际校验逻辑。

---

### R1-P1-004: SipTlsProperties.java — setServerPort 的 Javadoc 与方法不匹配

**文件**：`news/main/java/com/genersoft/iot/vmp/conf/SipTlsProperties.java` L244-254
**维度**：G03
**问题**：
```java
// 审计修复R4-P2-04: 端口范围校验
public void setServerPort(int serverPort) {
    if (serverPort < 1 || serverPort > 65535) {
        throw new IllegalArgumentException("TLS端口必须在1-65535范围内");
    }
    this.serverPort = serverPort;
}

public int getServerPort() {
    return serverPort;
}

/**
 * 设置 TLS 监听端口
 *
 * @param serverPort TLS 监听端口
 */
```
`getServerPort()` 方法的 Javadoc 注释（L256-260）位于 getter 之后，实际上是为 setter 写的注释，但 setter 在 getter 之前。注释与方法不匹配。

**修复建议**：重新排列方法顺序和注释。

---

### R1-P1-005: GBProtocolVersionHelper.java — isVersion2022 判断过于严格

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/GBProtocolVersionHelper.java` L78
**维度**：E02
**问题**：
```java
return version.trim().equals("2.0");
```
仅精确匹配 `"2.0"`。但 2022 版后续可能有补丁版本如 `"2.1"` 或 `"2.0.1"`。根据设计文档第 4 节，版本号格式为"主版本.次版本"，主版本号为 2 即表示 2022 版。

注释（L75）写道：`若为主版本号 2 返回 true`，但实现是精确匹配而非前缀匹配。

**影响**：未来 2022 版补丁版本无法被识别。
**修复建议**：改为 `version.trim().startsWith("2.")`。

---

### R1-P1-006: RegisterRedirectHelper.java — handle302Response 未更新设备注册地址

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/RegisterRedirectHelper.java` L162-173
**维度**：F04
**问题**：方法声称"更新设备本地保存的注册地址"，但实际仅更新了 `transport` 字段，未更新 `host` 和 `port`。注释解释（L163-165）：
```java
// 审计修复: Device 类有 transport 字段但无 registerPort 字段
// 仅更新 transport，registerPort 通过设备重新注册时自动获取
// 审计修复P1-03: Device类无setHost方法, 通过重新注册自动获取
```
但这意味着 302 重定向后，设备仍向原地址注册，重定向功能实际不生效。

**影响**：注册重定向功能不完整，无法实现 2022 版 7.3 的重定向要求。
**修复建议**：需要扩展 Device 类或通过其他机制传递新地址。

---

### R1-P1-007: SIPCommander2022Supplement.java — SN 计数器仅内存递增，重启回零

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java` L64
**维度**：F12
**问题**：
```java
private static final java.util.concurrent.atomic.AtomicInteger snCounter = new java.util.concurrent.atomic.AtomicInteger(0);
```
SN 序号从 0 开始，服务重启后回零。GB/T 28181 规范要求 SN 在设备会话内递增，但如果设备已收到 SN=100 的命令，服务重启后再次发送 SN=1，设备可能拒绝（SN 回退）。

注释承认：`SN 序号（简单递增，生产环境应考虑线程安全和持久化）`，但线程安全已通过 AtomicInteger 解决，持久化未实现。

**影响**：服务重启后 SN 回退，设备可能拒绝命令。
**修复建议**：将 SN 持久化到数据库或从设备当前 SN 继续。

---

### R1-P1-008: SnapshotFinishedNotifyMessageHandler.java — 异常时回复 200 OK 掩盖错误

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/SnapshotFinishedNotifyMessageHandler.java` L191-194
**维度**：F01
**问题**：
```java
} catch (Exception e) {
    logger.error("[抓图完成通知] 处理异常", e);
    respondAck(evt, Response.OK);  // 异常时回复 200 OK
}
```
处理异常时回复 200 OK，而 `DeviceUpgradeResultNotifyMessageHandler` 在相同场景回复 500（L205）。两个 Handler 行为不一致。

**影响**：异常被掩盖，发送方认为处理成功。
**修复建议**：统一为回复 500 SERVER_INTERNAL_ERROR。

---

### R1-P1-009: ApiDeviceControlController.java — PTZ精准控制参数未校验范围

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java` L72-95
**维度**：B07
**问题**：`ptzPrecise` 方法接受 `pan`、`tilt`、`zoom` 参数，但未校验范围。根据方法注释（L67-69）：
- pan 范围 -180.00 ~ 180.00
- tilt 范围 -90.00 ~ 90.00
- zoom 范围 0 ~ 20

但代码无任何范围校验，可传入任意值。

**影响**：非法参数可能导致设备异常行为。
**修复建议**：添加参数范围校验。

---

### R1-P1-010: ApiDeviceControlController.java — 错误消息暴露内部信息

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java` L93, L135, L171, L205, L249, L294, L330, L363, L395, L455
**维度**：C08
**问题**：错误消息格式为 `"XXX命令下发失败: " + "内部服务器错误"`，虽然实际未暴露异常详情（字符串拼接是硬编码的"内部服务器错误"），但 `log.error` 中记录了完整异常堆栈（包含 deviceId、channelId），这在生产环境中可能通过日志系统泄露。

**修复建议**：确保日志不记录敏感参数值，错误消息保持当前格式（不暴露详情）。

---

## P2 (Medium)

### R1-P2-001: escapeXml 方法重复实现

**文件**：
- `SipCharsetHelper.java` L232-249
- `SIPCommander2022Supplement.java` L397-412
**维度**：G02
**问题**：两处独立实现了完全相同的 `escapeXml` 方法，功能完全一致。`SIPCommander2022Supplement` 应调用 `SipCharsetHelper.escapeXml()` 而非自己实现。

**修复建议**：删除 `SIPCommander2022Supplement` 中的 `escapeXml`，改为调用 `SipCharsetHelper.escapeXml()`。

---

### R1-P2-002: SipMessageFilter.java — X-GB-ver 头部记录使用 warn 级别

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java` L168
**维度**：F09
**问题**：
```java
logger.warn("[SIP过滤] X-GB-ver: {}", gbVerHeader);
```
X-GB-ver 头部存在是正常情况（2022 版设备），不应使用 warn 级别。大量 2022 版设备注册时会刷屏日志。

**修复建议**：改为 debug 级别。

---

### R1-P2-003: CruiseTrackQueryMessageHandler.java — 模拟数据硬编码

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/CruiseTrackQueryMessageHandler.java` L241-242, L280-283
**维度**：F14
**问题**：巡航轨迹查询响应中使用硬编码的模拟数据：
```java
tracks.add(new CruiseTrack(1, "白天巡航", null));
tracks.add(new CruiseTrack(2, "夜间巡航", null));
```
和
```java
presetIds.add(1);
presetIds.add(2);
presetIds.add(3);
```
这不是生产级实现。实际应从设备配置缓存或数据库中查询。

**影响**：功能不可用，仅返回假数据。
**修复建议**：对接实际数据源，或明确标注为 stub。

---

### R1-P2-004: GBProtocolVersionHelper.java — addGbVerHeader 中 createHeader 后直接 addHeader

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/GBProtocolVersionHelper.java` L118
**维度**：G07
**问题**：
```java
Header gbVerHeader = SipFactory.getInstance().createHeaderFactory().createHeader(HEADER_X_GB_VER, GB_PROTOCOL_VERSION); request.addHeader(gbVerHeader);
```
两条语句挤在一行，可读性差。且每次调用都通过 `SipFactory.getInstance().createHeaderFactory()` 创建新的 HeaderFactory，效率低。

**修复建议**：拆分为两行，缓存 HeaderFactory。

---

### R1-P2-005: ApiConfigController.java — getSecurity 返回不可修改副本但 saveSecurity 直接修改静态 Map

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiConfigController.java` L134-136
**维度**：D05
**问题**：
```java
public Map<String, Object> getSecurity() {
    return Map.copyOf(SECURITY_CONFIG);
}
```
`getSecurity` 返回不可修改副本（好的做法），但 `saveSecurity` 直接修改 `SECURITY_CONFIG` 静态 Map。在 `saveSecurity` 执行期间调用 `getSecurity` 可能看到部分更新的状态。

**修复建议**：saveSecurity 中先构造完整的新 Map，再一次性替换。

---

### R1-P2-006: ApiDeviceControlController.java — 存储卡格式化使用 POST 但无二次确认

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java` L154
**维度**：F12
**问题**：存储卡格式化是破坏性操作，注释（L148）提到"在生产环境中应增加权限校验"，但仅有 `@PreAuthorize("hasRole('ADMIN')")` 的类级权限控制，无操作级别的二次确认或审计日志。

**修复建议**：增加操作审计日志（记录操作者、时间、目标设备），考虑增加二次确认机制。

---

### R1-P2-007: DeviceControlType.java — typeOf 方法先检查 element 再检查 elements

**文件**：`news/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java` L181-182
**维度**：B04
**问题**：
```java
if (!ObjectUtils.isEmpty(rootElement.element(item.val))
        || !ObjectUtils.isEmpty(rootElement.elements(item.val))) {
```
`element()` 和 `elements()` 都会遍历子节点，两次调用效率低。且如果 `element()` 返回 null，`elements()` 仍会再次遍历。

**修复建议**：仅使用 `elements()` 一次，检查返回列表是否为空。

---

### R1-P2-008: SipTlsProperties.java — toString 中 keyStoreType 和 trustStoreType 未脱敏路径

**文件**：`news/main/java/com/genersoft/iot/vmp/conf/SipTlsProperties.java` L308-318
**维度**：C07
**问题**：toString 已对 keyStore 和 trustStore 路径脱敏（显示 `[已配置]`），但 keyStoreType 和 trustStoreType 直接输出值。虽然类型信息（如 PKCS12）不属于高敏感信息，但为一致性建议也脱敏。

**影响**：低风险，但不符合最小信息暴露原则。
**修复建议**：可选脱敏或不修改（低风险）。

---

### R1-P2-009: TcpReconnectHelper.java — tryConnect 连接成功后立即关闭 Socket

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/TcpReconnectHelper.java` L138-157
**维度**：F05
**问题**：`tryConnect` 创建 Socket 连接成功后立即关闭，仅用于通道探活。但实际媒体传输场景中，这个 Socket 应该被复用于媒体流传输，而非关闭。

注释（L141-142）说明：`连接成功，立即关闭，仅用于通道探活`。如果是探活场景则合理，但如果用于实际媒体重连则不正确。

**影响**：如果用于 TCP 媒体通道重连，连接后关闭会导致媒体流无法传输。
**修复建议**：明确使用场景，如需复用连接则返回 Socket 而非关闭。

---

### R1-P2-010: GbCode2022.java — isValidGbCode 采集位置码取 2 位导致后续字段偏移

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/GbCode2022.java` L286
**维度**：E17
**问题**：
```java
String capturePositionCode = gbCode.substring(13, 15);
```
取 2 位（位 14-15）作为采集位置码。但根据编码结构定义（L31-34）：
```
14:     采集位置 (capturePositionCode) — 1 位
15-17:  设备序号 (deviceSerial) — 3 位
```
采集位置码在 20 位编码中占 1 位（第 14 位），取 2 位会导致设备序号首位被吞。

注释解释（L285）：`采集位置码取 2 位以支持 1~10 范围`，但位置 10 需要两位数字。这与编码结构定义矛盾——如果采集位置码可以是 10（两位），则编码结构应标注为 2 位。

**影响**：20 位编码校验时，采集位置码为 10 时正常，但为 1-9 时会吞掉设备序号首位。
**规范依据**：设计文档第 13.5 节，2022 版附录 E。
**修复建议**：需查证规范原文确认采集位置码在 20 位编码中占 1 位还是 2 位。如果占 1 位，则 10 无法表示，需用十六进制或其他编码方式。

---

## P3 (Low)

### R1-P3-001: ExtensionApplicationHandler.java — Javadoc 未正确关闭

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/ExtensionApplicationHandler.java` L5-13
**维度**：A02
**问题**：类级 Javadoc 以 `/**` 开头，但中间的 `<p>` 标签未正确关闭，且注释格式不统一（部分用 `*`，部分不用）。

**修复建议**：规范 Javadoc 格式。

---

### R1-P3-002: SnapshotConfigMessageHandler.java — 构造的 XML 未实际发送

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/SnapshotConfigMessageHandler.java` L261-266
**维度**：F13
**问题**：`handleSnapConfig` 方法构造了 `deviceControlXml`，但仅记录日志，未实际通过 SIP 下发到设备。注释（L264-266）解释：
```java
// 审计修复P1-14: 通过SIPCommander2022Supplement下发抓拍配置命令到设备
// 通过SIPCommander2022Supplement下发抓拍配置命令到设备
// 审计修复P2-04: 抓拍配置通过SIP消息下发给设备
```
但下方没有实际调用代码。

**影响**：抓拍配置命令不会下发到设备。
**修复建议**：注入 SIPCommander2022Supplement 并调用下发方法。

---

### R1-P3-003: SIPCommander2022Supplement.java — 固件上传目录硬编码

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java` L71
**维度**：G06
**问题**：
```java
private static final String FIRMWARE_UPLOAD_DIR = "/tmp/wvp/firmware";
```
上传目录硬编码为 `/tmp/wvp/firmware`，不可配置。在生产环境中：
1. `/tmp` 可能被清理（tmpwatch）
2. 不同操作系统路径不同
3. 可能无写入权限

**修复建议**：通过 `@Value` 注入配置项。

---

### R1-P3-004: ApiDeviceControlController.java — deviceUpgrade 方法参数 Javadoc 格式错误

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java` L263-264
**维度**：G03
**问题**：
```java
     * @param firmware     固件文件名
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
     * @param fileUrl      固件文件在服务器上的可访问 URL
```
Javadoc 参数说明中插入了 Java 行注释，格式混乱。

**修复建议**：清理 Javadoc，移除插入的行注释。

---

## Round 1 统计

| 严重性 | 数量 |
|--------|------|
| P0 (Critical) | 7 |
| P1 (High) | 10 |
| P2 (Medium) | 10 |
| P3 (Low) | 4 |
| **总计** | **31** |

## 待审计文件

以下文件需在 Round 2 中详细审计：
- `ui/` 目录下全部前端 Vue 组件和 JS 文件
- `patches/` 目录下全部补丁文件
- `tests/` 目录下全部测试文件
- `scripts/merge.sh`
- 前端 UI 改造方案文档中提到的内容与代码的一致性
