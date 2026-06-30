# 28181-2022 仓库代码审计报告（第1轮）

> **审计对象**：28181-2022 仓库中新增代码（排除 wvp/ 目录克隆原始代码）
> **审计范围**：news/ (22个Java文件)、backend/ (4个Java文件)、ui/ (9个文件)、patches/ (6个文件)、tests/ (8个Java测试+3个脚本)、scripts/ (1个脚本)
> **审计依据**：《升级开发设计文档》、《WVP合规性核查报告》、《WVP合规性升级改造开发方案》、《WVP前端UI改造方案》
> **审计方法**：100种审计方法（见审计方案）
> **审计原则**：反幻觉——所有发现均引用具体文件和行号，不推测、不补全、不编造；仅记录不修改
> **审计日期**：2026-06-29
> **审计人员**：视频监控系统专家、28181规范专家、系统架构师、资深Java开发人员

---

## 一、审计概述

### 1.1 代码统计

| 目录 | 文件数 | 代码行数 | 说明 |
|------|--------|---------|------|
| news/ | 22 | 5094 | 新增Java核心代码 |
| backend/ | 4 | 1234 | 后端Controller和SIP命令补充 |
| ui/ | 9 | 2370 | 前端Vue组件和API |
| patches/ | 6 | 574 | WVP原始代码补丁 |
| tests/ | 11 | 4713 | Java单元测试+SIPp脚本 |
| scripts/ | 1 | ~50 | 合并脚本 |
| **合计** | **53** | **~14035** | — |

### 1.2 审计结论摘要

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| Critical (P0) | 2 | 安全漏洞：路径遍历、SDP注入 |
| High (P1) | 5 | 生产就绪：未实现方法、无文件大小限制、配置不持久化、SM3回退矛盾 |
| Medium (P2) | 4 | 代码质量：console.error、API无错误处理、静默失败、测试脚本掩盖失败 |
| Low (P3) | 2 | 规范合规：枚举值大小写、测试覆盖率 |
| **合计** | **13** | — |

---

## 二、Critical (P0) — 共2项

### P0-01: 路径遍历漏洞 — deviceId直接用于文件上传路径

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
- **行号**：第199行
- **审计方法**：#51 路径遍历检查
- **代码**：
```java
Path uploadDir = Paths.get(FIRMWARE_UPLOAD_DIR, deviceId);
Files.createDirectories(uploadDir);
```
- **问题**：`deviceId`来自用户请求（`@PathVariable String deviceId`），直接拼接到文件路径中。攻击者可构造 `deviceId="../../../etc"` 等恶意值，导致目录遍历，在任意目录创建文件或覆盖系统文件。
- **依据**：《WVP合规性升级改造开发方案》改造项10要求实现设备软件升级功能，但未提及路径安全校验。
- **影响**：攻击者可在服务器任意位置创建目录和写入文件，可能导致RCE。
- **建议**：对 `deviceId` 进行严格校验（仅允许数字和字母），并使用 `Path.normalize()` + 路径前缀检查防止遍历。

### P0-02: SDP注入漏洞 — ssvcRatio和downloadUrl未转义

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SdpFieldHelper.java`
- **行号**：第207行、第289行
- **审计方法**：#34 字符串拼接安全检查
- **代码**：
```java
// 第207行
sdpBuilder.append("a=ssvcratio:").append(ssvcRatio).append("\r\n");

// 第289行
uField.append(":").append(downloadUrl);
```
- **问题**：`ssvcRatio`和`downloadUrl`参数直接拼接到SDP文本中，未进行CRLF注入防护。攻击者可注入 `\r\n` 字符，伪造额外的SDP行（如 `a=rtpmap:` 或 `m=video`），导致SDP协商劫持。
- **依据**：《WVP合规性升级改造开发方案》改造项20和23要求实现a=ssvcratio和u字段，但未提及输入校验。
- **影响**：SDP协商劫持，可能导致媒体流被重定向到攻击者控制的服务器。
- **建议**：对所有SDP字段值进行CRLF字符过滤（移除 `\r` 和 `\n`），并校验字段值格式。

---

## 三、High (P1) — 共5项

### P1-01: sendSipMessage为骨架方法，9个SIP命令均无法实际发送

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
- **行号**：第394~432行
- **审计方法**：#82 降级策略检查
- **代码**：
```java
private void sendSipMessage(Device device, String channelId, String xml) {
    // 实际集成代码:
    //   SIPRequest request = messageFactory.createRequest(...);
    //   ...
    log.debug("[SIP发送] device={}, channel={}, xml={}", device.getDeviceId(), channelId, xml);
}
```
- **问题**：`sendSipMessage` 方法仅记录日志，未实际发送SIP消息。9个SIP命令方法（PTZ精准控制、存储卡格式化、目标跟踪、设备升级、图像抓拍、看守位查询、巡航轨迹查询、PTZ精准状态查询、存储卡状态查询）全部调用此方法，导致所有命令均无法下发到设备。
- **依据**：《WVP合规性升级改造开发方案》改造项7~15要求实现这些命令的完整流程。
- **影响**：所有2022版新增的设备控制和查询功能均无法工作。
- **建议**：实现完整的SIP MESSAGE发送逻辑，调用WVP现有的 `SipSender` 或 `SIPCommander` 基础设施。

### P1-02: 文件上传无大小限制

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
- **行号**：第197行
- **审计方法**：#75 拒绝服务防护检查
- **代码**：
```java
public String uploadFirmwareFileImpl(String deviceId, MultipartFile file) throws IOException {
```
- **问题**：`uploadFirmwareFileImpl` 方法未检查上传文件大小。攻击者可上传超大文件耗尽服务器磁盘空间。
- **依据**：《WVP前端UI改造方案》第3.4节 F4 提到固件文件类型为 `.bin/.img/.zip`，但未指定大小限制。
- **影响**：磁盘耗尽DoS攻击。
- **建议**：增加文件大小校验（如最大500MB），并在 `application.yml` 中配置 `spring.servlet.multipart.max-file-size`。

### P1-03: 文件上传无后端文件类型校验

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
- **行号**：第200~207行
- **审计方法**：#20 输入验证检查
- **代码**：
```java
String originalName = file.getOriginalFilename();
String ext = "";
if (originalName != null && originalName.contains(".")) {
    ext = originalName.substring(originalName.lastIndexOf("."));
}
```
- **问题**：仅从前端 `accept=".bin,.img,.zip"` 限制文件类型，后端未校验文件扩展名和Content-Type。攻击者可绕过前端限制上传任意类型文件（如 `.jsp`、`.war`）。
- **依据**：《WVP前端UI改造方案》第3.4节 F4 要求固件文件类型为 `.bin/.img/.zip`。
- **影响**：恶意文件上传，可能导致RCE。
- **建议**：后端校验文件扩展名白名单（`.bin`、`.img`、`.zip`）和Content-Type。

### P1-04: 安全配置仅存储在内存中，重启后丢失

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiConfigController.java`
- **行号**：第40行
- **审计方法**：#91 配置外部化检查
- **代码**：
```java
private static final ConcurrentHashMap<String, Object> SECURITY_CONFIG = new ConcurrentHashMap<>();
```
- **问题**：安全配置（SM3开关、TLS开关、字符集、注册重定向、TCP重连）存储在静态内存Map中，服务重启后恢复默认值。代码注释中已承认此问题（"生产环境应改为持久化存储"），但未实际实现。
- **依据**：《WVP前端UI改造方案》第七章 F8 要求安全配置页面支持持久化保存。
- **影响**：运维人员通过UI修改的安全配置在服务重启后丢失，不符合生产级要求。
- **建议**：将配置持久化到数据库或配置文件中。

### P1-05: SM3回退策略矛盾 — 类级别回退SHA-256，方法级别回退MD5

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/SM3DigestHelper.java`
- **行号**：第45行 vs 第243行
- **审计方法**：#32 SM3算法实现合规性
- **代码**：
```java
// 第45行 - 类级别回退
private static final String ALGORITHM_FALLBACK = "SHA-256";

// 第243行 - 方法级别回退
logger.warn("[SM3] SM3不可用, 回退到MD5");
return digestMd5(data);
```
- **问题**：类初始化时，SM3不可用则回退到SHA-256（第45行）。但 `digestWithFallback` 方法在SM3不可用时回退到MD5（第243行）。两个回退策略互相矛盾，可能导致摘要算法不一致。
- **依据**：《WVP合规性升级改造开发方案》改造项2要求"SM3优先，MD5兼容"。设计文档第8.3节要求2022版使用SM3，2016版使用MD5。SHA-256不是任何版本的规范要求算法。
- **影响**：摘要算法不一致可能导致认证失败。
- **建议**：统一回退策略。根据改造方案，应回退到MD5（兼容2016版），移除SHA-256回退逻辑。

---

## 四、Medium (P2) — 共4项

### P2-01: 前端生产代码中使用console.error（8处）

- **文件**：`ui/components/DeviceUpgradeDialog.vue`、`ui/components/QueryResultDialog.vue`、`ui/components/SnapshotConfigDialog.vue`、`ui/components/SecurityConfig.vue`、`ui/components/StorageCardDialog.vue`
- **行号**：共8处（DeviceUpgradeDialog.vue:354、QueryResultDialog.vue:271/304/335、SnapshotConfigDialog.vue:225、SecurityConfig.vue:226、StorageCardDialog.vue:271/302）
- **审计方法**：#59 前端代码质量检查
- **问题**：生产Vue组件中使用 `console.error` 输出错误信息，在生产环境中会暴露内部错误细节到浏览器控制台。
- **影响**：信息泄露，影响代码质量。
- **建议**：在生产构建中移除或替换为统一的错误处理服务。

### P2-02: 前端API层无错误处理

- **文件**：`ui/api/frontEnd.js`
- **审计方法**：#60 API调用错误处理检查
- **问题**：`frontEnd.js` 中所有API函数直接返回 `request()` 调用结果，未在API层添加任何错误处理或重试逻辑。虽然组件层有 `try/catch`，但API层缺乏统一的错误转换和日志记录。
- **影响**：错误处理分散在各组件中，难以维护。
- **建议**：在API层添加统一的错误处理拦截器。

### P2-03: SM3DigestHelper.digest()方法静默返回空字符串

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/SM3DigestHelper.java`
- **行号**：第145~146行
- **审计方法**：#17 空catch块检查
- **代码**：
```java
} catch (NoSuchAlgorithmException e) {
    // 静态初始化已确保不会进入此分支，兜底返回空字符串
    return "";
}
```
- **问题**：`digest()` 方法在捕获 `NoSuchAlgorithmException` 后返回空字符串，调用方无法区分"摘要结果为空"和"算法不可用"的情况。
- **影响**：可能导致认证逻辑使用空字符串作为摘要值进行比较，产生安全漏洞。
- **建议**：抛出运行时异常而非返回空字符串。

### P2-04: 测试脚本使用 `|| true` 掩盖测试失败

- **文件**：`tests/scripts/run_all.sh`
- **行号**：第20行、第24行
- **审计方法**：#90 测试脚本健壮性检查
- **代码**：
```bash
bash "$SCRIPT_DIR/run_java_tests.sh" || true
bash "$SCRIPT_DIR/run_sipp_tests.sh" || true
```
- **问题**：使用 `|| true` 抑制子脚本的非零退出码，即使测试失败也不返回错误。CI/CD流水线中无法检测到测试失败。
- **影响**：测试失败被静默忽略，无法保证代码质量。
- **建议**：移除 `|| true`，或改为记录失败状态并在脚本末尾返回适当的退出码。

---

## 五、Low (P3) — 共2项

### P3-01: DeviceControlType枚举值"FormatsDcard"大小写待核实

- **文件**：`news/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java`
- **行号**：第98行
- **审计方法**：#41 存储卡格式化XML合规性
- **代码**：
```java
FORMAT_SDCARD("FormatsDcard", "存储卡格式化"),
```
- **问题**：枚举值为 `FormatsDcard`（大写F、小写d），需核实是否与2022版规范原文一致。设计文档中未明确给出此CmdType的精确大小写。
- **影响**：若大小写与规范不符，设备可能无法识别命令。
- **建议**：查阅2022版规范原文确认精确大小写。

### P3-02: 测试断言数量偏少

- **文件**：`tests/java/` 目录下8个测试文件
- **审计方法**：#76 测试覆盖率检查
- **问题**：8个测试文件共94个断言，平均每个文件约12个断言。对于38个改造项的测试覆盖，断言密度偏低。
- **影响**：测试覆盖不充分，可能遗漏边界条件。
- **建议**：增加边界条件测试和异常路径测试。

---

## 六、审计汇总

| 严重程度 | 数量 | 状态 |
|---------|------|------|
| Critical (P0) | 2 | 已记录 |
| High (P1) | 5 | 已记录 |
| Medium (P2) | 4 | 已记录 |
| Low (P3) | 2 | 已记录 |
| **合计** | **13** | — |

---

## 七、规范来源说明

本审计报告所有发现均来自以下文档和代码：

| 文档/代码 | 说明 | 位置 |
|------|------|------|
| 《GB/T 28181—2016 升级至 GB/T 28181—2022 开发设计文档》V1.0 | 审计基准 | `doc/GB28181_2016_to_2022_升级开发设计文档.md` |
| 《WVP 合规性核查报告》 | 核查基准 | `doc/WVP合规性核查报告.md` |
| 《WVP 合规性升级改造开发方案》 | 改造方案 | `doc/WVP合规性升级改造开发方案.md` |
| 《WVP 前端 UI 改造方案》 | 前端方案 | `doc/WVP前端UI改造方案.md` |
| 新增代码 | 审计对象 | `news/`、`backend/`、`ui/`、`patches/`、`tests/`、`scripts/` |

所有发现均标注具体文件和行号，可通过引用直接定位进行复核。
