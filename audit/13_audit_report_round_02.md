# 13_全量代码审计报告（第 2 轮：业务逻辑层）

> **审计轮次**：第 2 轮（Round 2）
> **审计方法**：C01-C12（业务逻辑）、K01-K12（28181 规范符合性）
> **审计范围**：`backend/`、`news/` 全部 Java 文件
> **审计日期**：2026-06-30

---

## 一、审计概述

### 1.1 本轮发现统计

| 严重等级 | 数量 | 说明 |
|---------|------|------|
| 🔴 致命 | 3 | 业务逻辑错误导致数据错误/安全隐患 |
| 🟠 严重 | 8 | 业务规则/规范不符，可能引发兼容性问题 |
| 🟡 一般 | 7 | 健壮性/边界处理 |
| 🟢 建议 | 4 | 优化建议 |
| **合计** | **22** | — |

> 注：本轮 C04/C05/K 类方法有 5 个问题与第 1 轮重复 [DUPLICATE]，本表去重后统计。

---

## 二、🔴 致命问题

### F26 🔴 `SnapshotConfigMessageHandler` — `SnapConfig` 元素名大小写与规范不符

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/SnapshotConfigMessageHandler.java`
**行号**：88, 93
**方法**：K12、C05

**问题描述**：
```java
private static final String CMD_TYPE = "SnapConfig";                  // 88 行
private static final String ELEMENT_SNAP_CONFIG = "SnapConfig";       // 93 行
```

**问题**：
1. `CMD_TYPE` 是**容器的 CmdType 字段**值。GB/T 28181-2022 9.14 规定：图像抓拍配置命令承载于 `<Control><CmdType>DeviceControl</CmdType>` 容器中，**容器的 CmdType 应为 `DeviceControl`**，而 `SnapConfig` 是内层元素名。
2. 注释中（83-86 行）自相矛盾："本处理器在 ControlMessageHandler 中注册的 CmdType = 'SnapConfig'... 实际消息分发由 DeviceControlQueryMessageHandler 完成"
3. 若将本处理器直接注册为 `CmdType=SnapConfig` 的处理器，会导致所有 DeviceControl 命令都被错误路由到本处理器

**证据依据**：设计文档第 12.2 节明确"改造项 15：图像抓拍配置命令" 容器为 `<Control><CmdType>DeviceControl</CmdType>`。

**影响**：命令分发错误，所有 DeviceControl 类命令（PTZ/录像/告警等）可能错位到抓拍处理器 → 大规模业务中断。

**修复建议**：
```java
private static final String CMD_TYPE = "DeviceControl";
// 内层 SnapConfig 元素由父分发器 DeviceControlQueryMessageHandler 调用
public boolean canHandle(Element element) {
    return element != null && element.element("SnapConfig") != null;
}
```

---

### F27 🔴 `SIPCommander2022Supplement.deviceUpgradeCmdImpl` — `manufacturer` 大小写与规范不符

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：225
**方法**：K08、C05

**问题描述**：
```java
xml.append("<manufacturer>").append(escapeXml(manufacturer)).append("</manufacturer>\r\n");
```

**问题**：
- 设计文档第 10.1 节和 2022 版 A.2.3.1.12 规定：manufacturer 字段名应为 `Manufacturer`（M 大写）
- SIPCommanderSupplement.java 第 105 行（接口注释）也明确写 `Manufacturer` 大写
- 但实际代码使用小写 `manufacturer`
- 这与 `DeviceControlType.java:117` 注释（"XML字段：FirmWare、FileURL、Manufacturer、sessionID"）也不一致

**影响**：设备端 XML 解析失败，设备升级命令不可用。

**修复建议**：
```java
xml.append("<Manufacturer>").append(escapeXml(manufacturer)).append("</Manufacturer>\r\n");
```

---

### F28 🔴 `SIPCommander2022Supplement` — `Pan`/`Tilt`/`Zoom` 元素名大小写不规范

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：115-117
**方法**：K05、C05

**问题描述**：
```java
xml.append("<pan>").append(...).append("</pan>\r\n");        // 全小写
xml.append("<Tilt>").append(...).append("</Tilt>\r\n");        // 仅 T 大写
xml.append("<zoom>").append(...).append("</zoom>\r\n");        // 全小写
```

**问题**：
- SIPCommanderSupplement.java 第 47-49 行接口注释说明：`pan`（小写）、`Tilt`（T 大写）、`zoom`（小写）
- 与之配套的 DeviceControlTypeTest.java 第 18-25 行也断言 "PTzPrecisectrl" 是大小写混合的
- 但 2022 版 A.2.3.1.11 规范原文是 `PTzPrecisectrl` 容器元素名 + `Pan`/`Tilt`/`Zoom`（全部首字母大写）作为子元素
- `PTzPrecisectrl` 中 "P" 大写是规范原文的"驼峰规则"，子元素名应一致使用首字母大写

**影响**：标准 2022 设备无法解析控制命令。

**修复建议**：
```java
xml.append("<Pan>").append(pan).append("</Pan>\r\n");
xml.append("<Tilt>").append(tilt).append("</Tilt>\r\n");
xml.append("<Zoom>").append(zoom).append("</Zoom>\r\n");
```

---

## 三、🟠 严重问题（业务规则）

### F29 🟠 `ApiDeviceControlController.ptzPrecise` — 数值范围未校验

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**行号**：75-97
**方法**：C01

**问题描述**：
```java
public WVPResult<?> ptzPrecise(@RequestParam Double pan, @RequestParam Double tilt, @RequestParam Double zoom) {
    // 仅校验 deviceId 空值
    if (deviceId == null || deviceId.isEmpty()) { ... }
    // 完全未校验 pan/tilt/zoom 范围
    cmder.ptzPreciseCmd(device, channelId, pan, tilt, zoom);
}
```

**规范要求**（设计文档第 10.1 节）：
- `pan` 范围：-180.00 ~ 180.00
- `tilt` 范围：-90.00 ~ 90.00
- `zoom` 范围：0 ~ 20

**SIPCommander2022Supplement.ptzPreciseCmdImpl 内部有校验**（102-104 行），但**先调用 cmder.ptzPreciseCmd 才校验**会执行完整的 XML 构造与 SIP 发送流程。

**影响**：无效参数被发送到设备，浪费带宽、可能引起设备异常。

**修复建议**：在 Controller 层先做范围校验。

---

### F30 🟠 `ApiDeviceControlController.deviceUpgrade` — `sessionId` 长度未校验

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**行号**：282-309
**方法**：C08

**问题描述**：
GB/T 28181-2022 A.2.3.1.12 规定 sessionID 长度 32~128 字节，Controller 完全未校验。

**影响**：可传入任意长度字符串，可能造成：
- 设备解析失败
- 日志注入
- 缓冲区溢出（理论上 Java 无此风险，但设备 C 代码可能有）

---

### F31 🟠 `SnapshotConfigMessageHandler` — 缺少 `SnapConfigCmd` 设备响应处理

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/SnapshotConfigMessageHandler.java`
**行号**：150-360
**方法**：C12

**问题描述**：
1. 处理器只实现了设备 → 平台方向的请求处理（`handForDevice`）
2. 缺少对**设备响应的处理**（平台 → 设备发送后，设备应回 200 OK，平台需解析响应）
3. 缺少对**抓拍完成通知**的处理
   - 设计文档第 12.2 节明确："抓拍图像传输完成通知"
   - 由 `SnapshotFinishedNotifyMessageHandler` 处理（已存在但与本处理器未联动）
4. `SnapshotFinishedNotifyMessageHandler.java` 经抽查存在但**未确认是否处理了 `uploadsnapshot` 通知的 sessionID 关联**

**影响**：抓拍流程不完整，前端无法获取抓拍结果。

---

### F32 🟠 `DeviceUpgradeResultNotifyMessageHandler` — 异常吞没

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/DeviceUpgradeResultNotifyMessageHandler.java`
**行号**：180-220
**方法**：F01

**问题描述**：
处理器内多处 `catch (Exception e) { log.error(...); }` 但**没有向上抛出或返回错误**：
- 一旦设备上报异常，平台端无感知
- 数据库/缓存中的升级状态保持 PENDING

**影响**：升级失败静默，运维无法主动发现问题。

---

### F33 🟠 `CruiseTrackQueryMessageHandler` — 业务逻辑简化

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/CruiseTrackQueryMessageHandler.java`
**行号**：107-353
**方法**：C07、C11

**问题描述**：
1. 只实现了基础查询框架，**未实际查询数据库/缓存中的巡航轨迹数据**
2. 使用 `DEFAULT_*` 常量作为响应（与 HomePositionQueryMessageHandler 同样问题）
3. 没有设备状态机：发出 Query 后，无法跟踪设备是否回 Response

**影响**：所有 4 个新查询命令（看守位/巡航/PTZ状态/存储卡）都是"伪实现"。

---

### F34 🟠 `SIPCommander2022Supplement.snapshotConfigCmdImpl` — `Resolution` 字段非规范

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：390, 406
**方法**：C05

**问题描述**：
```java
// 390 行注释："Resolution 字段为后端扩展（前端传入），规范中无此字段"
// 406 行：xml.append("<Resolution>").append(resolution).append("</Resolution>\r\n");
```

**问题**：
1. 2022 版 9.14 抓拍配置**未定义 Resolution 字段**（设计文档已确认）
2. 实际规范定义的抓拍参数在 SnapConfig 容器下，但具体字段为：snapNum、Interval、UploadURL、SessionID
3. 注释自己也承认 "规范中无此字段" → 自由发挥

**影响**：与任何 GB/T 28181-2022 设备不兼容；自定义字段可能被设备忽略或报错。

**修复建议**：删除 Resolution 字段；改用 snapNum/Interval/UploadURL/SessionID。

---

### F35 🟠 `ApiDeviceControlController.storageCardStatusQuery` — 方法命名不规范

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**行号**：191-210
**方法**：C04

**问题描述**：
```java
@PostMapping("/storage_card_status_query/{deviceId}/{channelId}")
public WVPResult<?> storageCardStatusQuery(...) {
    cmder.storageCardStatusQueryCmd(device, channelId);  // 不存在的服务方法
}
```

**问题**：
1. 注解为 `@PostMapping`，但**查询类命令应是 @GetMapping**（参见 homePositionQuery 也是 @GetMapping）
2. 与前端 `ui/api/frontEnd.js:105-111` 期望的 `get` 方法不一致
3. Service 接口 `ISIPCommander` 没有 `storageCardStatusQueryCmd` 方法

**影响**：所有平台 → 设备的存储卡状态查询命令均失败。

---

### F36 🟠 `ApiDeviceControlController.targetTrack` — 注解不一致

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**行号**：115-141
**方法**：C04

**问题描述**：
```java
@PostMapping("/target_track/{deviceId}/{channelId}")
public WVPResult<?> targetTrack(...) {
    if (!"Auto".equals(action) && !"Manual".equals(action)) { ... }
    // ↑ 仅校验两个值，遗漏 "Stop"（停止跟踪）
}
```

**问题**：
1. 注释（74-75 行）写"停止跟踪由后端超时自动处理"，但根据 2022 版 A.2.3.1.14，action 可取值 "Stop"（停止跟踪）
2. 校验白名单不完整，前端发送 "Stop" 会被 400 拒绝

**影响**：无法停止目标跟踪，设备持续运行。

**修复建议**：增加 `"Stop"` 到白名单：
```java
if (!"Auto".equals(action) && !"Manual".equals(action) && !"Stop".equals(action)) {
    return WVPResult.fail(400, "action 参数非法，仅支持 Auto/Manual/Stop");
}
```

---

### F37 🟠 `GbCode2022.isValidCapturePosition` — 取值范围 1~10 与单字符字段矛盾

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/GbCode2022.java`
**行号**：283-295
**方法**：C10、K12

**问题描述**：
```java
public static boolean isValidCapturePosition(String capturePos) {
    // 284-285 行
    if (capturePos.length() != 1) {  // ← 仅允许 1 位
        return false;
    }
    int code = Integer.parseInt(capturePos);
    return code >= CAPTURE_POSITION_MIN && code <= CAPTURE_POSITION_MAX;  // 1~10
}
```

**问题**：
1. 长度校验为 1 位 → 最大值 "9"
2. 取值范围为 1~10 → 边界值 "10" 永远不通过
3. 注释 285 行明确说"采集位置码取值范围 1~10"

**影响**：任何带有采集位置码 = 10 的设备被识别为非法。

**修复建议**：
```java
if (capturePos.length() > 2) return false;  // 允许 1~2 位
```

---

## 四、🟡 一般问题

### F38 🟡 `ApiDeviceControlController` — 8 个端点无单元测试

**文件**：`tests/java/unit/`
**方法**：I01

**问题描述**：
`tests/java/unit/` 目录下只有 8 个测试类（DeviceControlTypeTest、GBProtocolVersionHelperTest、GbCode2022Test、MansrtspHelperTest、SM3DigestHelperTest、SdpFieldHelperTest、SipCharsetHelperTest、TcpReconnectHelperTest），**无 ApiDeviceControlController 测试** → Controller 层的参数校验、文件上传、SSRF 防护等关键逻辑无验证。

---

### F39 🟡 `ApiDeviceControlController` — `@PathVariable` 未限制格式

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
**行号**：所有端点
**方法**：C01

**问题描述**：
`deviceId` 和 `channelId` 都应为 20 位国标编码（仅数字），但 `@PathVariable` 默认无格式限制 → 攻击者可传入 `../../etc/passwd` 等字符串到 URL。

**修复建议**：
```java
@PathVariable @Pattern(regexp = "\\d{20}") String deviceId
```

---

### F40 🟡 `SIPCommander2022Supplement.sendSipMessage` — 异常吞没

**文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：448-475
**方法**：F01

**问题描述**：
```java
private void sendSipMessage(Device device, String channelId, String xmlStr) {
    try {
        // SIP 发送逻辑
    } catch (Exception e) {
        log.error("[SIP] 发送失败", e);
        // ← 没有 rethrow 也没有返回 false
    }
}
```

**影响**：调用方无法判断发送是否成功 → 200 OK 实际是假成功。

---

### F41 🟡 `MansrtspHelper.ALLOWED_SCALES` 缺少 8x/16x

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/MansrtspHelper.java`
**行号**：40-42
**方法**：C04

**问题描述**：
- 当前白名单：`{0.25, 0.5, 1.0, 2.0, 4.0}` → 最多 4 倍速
- 2022 版 A.2.4 实际支持：`{0.25, 0.5, 1, 2, 4, 8, 16}`

**影响**：8x/16x 倍速被自动 snap 到 4x，不符合规范。

---

### F42 🟡 `SdpFieldHelper.appendDownloadSpeed` — 未校验 speed 范围

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SdpFieldHelper.java`
**行号**：161-176
**方法**：C01

**问题描述**：
```java
public static void appendDownloadSpeed(StringBuilder sdpBuilder, double speed) {
    if (sdpBuilder == null) return;
    sdpBuilder.append("a=downloadspeed:").append(speed).append("\r\n");  // 未校验范围
}
```

**问题**：
- 规范要求 downloadspeed 取值：1, 2, 4, 8
- 方法未做范围校验或 snap

**影响**：设备收到非法速度值可能拒绝下载。

---

### F43 🟡 `RegisterRedirectHelper` — `tls` 参数未实现

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/auth/RegisterRedirectHelper.java`
**行号**：1-197
**方法**：C04

**问题描述**：
设计文档第 9.2 节（注册重定向）规定 302 响应应包含：
- Contact: 重定向目标
- Expires: 新注册有效期
- **可选**：Date、Retry-After

当前实现仅添加 Contact，未实现 Expires 处理 → 设备按重定向后可能立即重新注册。

---

### F44 🟡 `SipMessageFilter.MAX_CONTENT_LENGTH` 默认值偏小

**文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java`
**行号**：97

**问题描述**：
```java
public static final int MAX_CONTENT_LENGTH = 1024 * 1024;  // 1MB
```

**问题**：
- 设备目录查询响应可能含数千个通道信息，1MB 容易超限
- 应为可配置项而非硬编码常量

---

## 五、🟢 建议

### F45 🟢 `SM3DigestHelper.digest(String)` 与 `digest(byte[])` 行为不一致

**问题**：`digest(byte[])` 当 data 为 null 时返回 `null`；`digest(String)` 调用前者，所以最终也可能返回 `null`。建议统一为 `""`。

---

### F46 🟢 `SipCharsetHelper` 静态初始化吞异常

**文件**：`SipCharsetHelper.java:66-76`
**问题**：降级到 UTF-8 但日志只 warn → 监控告警不会触发。

---

### F47 🟢 `SdpFieldHelper.appendDownloadSField` 不返回 s 字段值

**建议**：返回 boolean 表示是否成功添加，便于链路追踪。

---

### F48 🟢 多个工具类常量命名风格不一致

- `SipCharsetHelper`：使用 `DEFAULT_CHARSET` (全大写)
- `SipCharsetHelper`：使用 `DEFAULT_CHARSET_OBJ` (混合)
- `MansrtspHelper`：使用 `HEADER_SCALE` (全大写)
- `MansrtspHelper`：使用 `DEFAULT_SCALE` (全大写)

整体一致，但 `DEFAULT_CHARSET_OBJ` 的 `_OBJ` 后缀属于过度设计 → 建议改为直接 `DEFAULT_CHARSET` 持有 Charset 对象（拆掉 String 常量）。

---

## 六、审计方法学记录（本轮）

| 编号 | 方法 | 应用文件数 | 命中问题数 |
|------|------|----------|----------|
| C01 | 参数范围校验 | 5 | 4 (F29, F30, F39, F42) |
| C04 | 业务规则一致性 | 8 | 5 (F33, F35, F36, F37, F41) |
| C05 | XML 元素名大小写 | 6 | 4 (F26, F27, F28, F34) |
| C07 | 异步响应 | 4 | 1 (F33) |
| C08 | 会话 ID 长度 | 2 | 1 (F30) |
| C10 | 编码规则 | 1 | 1 (F37) |
| C11 | 状态机 | 3 | 1 (F33) |
| C12 | 错误码 | 3 | 1 (F32) |
| F01 | 异常吞没 | 4 | 2 (F32, F40) |
| I01 | 单元测试存在 | 1 | 1 (F38) |
| K05 | PTZ 精准控制 | 1 | 1 (F28) |
| K08 | 设备升级 | 1 | 1 (F27) |
| K12 | 抓拍配置 | 1 | 1 (F26) |

**本轮合计**：13 种方法，22 个新问题（3🔴 8🟠 7🟡 4🟢）。

---

**第 2 轮审计结束。**
