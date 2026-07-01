# Patch 05: DeviceControlQueryMessageHandler — 2022 控制命令

> **目标文件**: `src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/DeviceControlQueryMessageHandler.java`
> **改造项**: 7（PTZ精准控制）、8（存储卡格式化）、9（目标跟踪）、10（设备软件升级）
> **原始补丁**: `patches/05-DeviceControlQueryMessageHandler.patch`（hunk 行号偏移 + 语义错误）
> **设计文档来源**: 第10.1节（第898~923行），2022版A.2.3.1

---

## 审计发现 B-11: 命令失败仍返回 OK（已修正）

原始补丁第241-252行存在严重语义错误：`deviceUpgradeCmd` 捕获异常后仍返回 `Response.OK`。
以下集成指南已修正此问题。

---

## 变更 1: 新增枚举值（DeviceControlType.java 同步修改）

```java
// === 改造项7: PTZ精准控制 ===
PTZ_PRECISE_CTRL("PTzPrecisectrl", "PTZ精准控制"),

// === 改造项8: 存储卡格式化 ===
FORMAT_SDCARD("FormatSdcard", "存储卡格式化"),

// === 改造项9: 目标跟踪 ===
TARGET_TRACK("TargetTrack", "目标跟踪"),

// === 改造项10: 设备软件升级 ===
DEVICE_UPGRADE("Deviceupgrade", "设备软件升级"),
```

## 变更 2: switch-case 新增分支

```java
case PTZ_PRECISE_CTRL:
    handlePtzPreciseCtrl(channel, rootElement, request, DeviceControlType.PTZ_PRECISE_CTRL);
    break;

case FORMAT_SDCARD:
    handleFormatSdcard(channel, request, DeviceControlType.FORMAT_SDCARD);
    break;

case TARGET_TRACK:
    handleTargetTrack(channel, rootElement, request);
    break;

case DEVICE_UPGRADE:
    handleDeviceUpgrade(channel, rootElement, request);
    break;
```

## 变更 3: handlePtzPreciseCtrl 方法

```java
private void handlePtzPreciseCtrl(CommonGBChannel channel, Element rootElement,
        SIPRequest request, DeviceControlType type) {
    Element ptzElement = rootElement.element("PTzPrecisectrl");
    if (ptzElement != null) {
        String pan = ptzElement.elementText("pan");
        String tilt = ptzElement.elementText("Tilt");  // T大写
        String zoom = ptzElement.elementText("zoom");
        log.info("[设备控制] PTZ精准控制: pan={}, Tilt={}, zoom={}", pan, tilt, zoom);
    } else {
        log.warn("[设备控制] PTZ精准控制: 未找到PTzPrecisectrl元素");
    }
    responseAck(request, Response.OK);
}
```

## 变更 4: handleFormatSdcard 方法

```java
private void handleFormatSdcard(CommonGBChannel channel, SIPRequest request,
        DeviceControlType type) {
    log.info("[设备控制] 存储卡格式化: channel={}",
            channel != null ? channel.getChannelId() : "null");
    responseAck(request, Response.OK);
}
```

## 变更 5: handleTargetTrack 方法

```java
private void handleTargetTrack(CommonGBChannel channel, Element rootElement,
        SIPRequest request) {
    String action = rootElement.elementText("TargetTrack");
    if (!"Auto".equals(action) && !"Manual".equals(action)) {
        log.warn("[设备控制] 目标跟踪: 非法action={}", action);
        responseAck(request, Response.BAD_REQUEST);
        return;
    }
    log.info("[设备控制] 目标跟踪: action={}", action);
    responseAck(request, Response.OK);
}
```

## 变更 6: handleDeviceUpgrade 方法（🔴 关键修正 B-11）

```java
private void handleDeviceUpgrade(CommonGBChannel channel, Element rootElement,
        SIPRequest request) {
    Element upgradeElement = rootElement.element("Deviceupgrade");
    if (upgradeElement == null) {
        log.warn("[设备控制] 设备升级: 未找到Deviceupgrade元素");
        responseAck(request, Response.BAD_REQUEST);
        return;
    }

    String firmWare = upgradeElement.elementText("FirmWare");
    String fileUrl = upgradeElement.elementText("FileuRL");
    String manufacturer = upgradeElement.elementText("Manufacturer");

    Device device = deviceService.getDevice(channel.getGbId());
    if (device == null) {
        log.warn("[设备控制] 设备升级: 设备不存在 channel={}", channel.getGbId());
        responseAck(request, Response.NOT_FOUND);
        return;
    }

    try {
        // 🔴 审计修复 B-11: 移除 try-catch 包裹 responseAck，改为在异常时返回错误
        cmder2022.deviceUpgradeCmd(device, channel.getGbId(), firmWare, fileUrl, manufacturer);
        // ✅ 仅在成功时返回 OK
        responseAck(request, Response.OK);
    } catch (Exception e) {
        log.error("[设备控制] 设备升级命令下发失败: channel={}, error={}",
                channel.getGbId(), e.getMessage());
        // ✅ 失败时返回 SERVER_INTERNAL_ERROR，不再返回 OK
        try {
            responseAck(request, Response.SERVER_INTERNAL_ERROR);
        } catch (SipException | InvalidArgumentException | ParseException ex) {
            log.error("[设备控制] 设备升级: 错误响应发送失败", ex);
        }
    }
}
```

### B-11 修正对比

| | 原始代码（错误） | 修正后 |
|---|---|---|
| 异常处理 | catch 后仍执行 `responseAck(OK)` | catch 后执行 `responseAck(SERVER_INTERNAL_ERROR)` |
| 语义 | 命令失败 → 返回成功 | 命令失败 → 返回服务器错误 |
