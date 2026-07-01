# Patch 01: RegisterRequestProcessor — X-GB-ver 版本标识

> **目标文件**: `src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/RegisterRequestProcessor.java`
> **改造项**: 1（SIP 注册消息增加 X-GB-ver 版本标识头域）
> **原始补丁**: `patches/01-RegisterRequestProcessor.patch`（hunk 行号偏移，无法应用）
> **设计文档来源**: 第13.9节（第**1649**行起），2022版附录I

---

## 修正说明

原始补丁注释引用的"第1598~1611行"不准确，13.9节实际从**第1649行**开始。以下集成指南使用修正后的行号引用。

---

## 变更 1: 新增常量

在 `RegisterRequestProcessor` 类中新增：

```java
// === 改造项1: X-GB-ver版本标识头域 ===
// 来源: 设计文档第13.9节(第1649行起), 2022版附录I
// 规范要求: SIP注册及其响应消息头部带上扩展字段X-GB-ver用于表示版本号

/**
 * 2022版协议版本号
 * 来源: 设计文档第13.9节, 2022版附录I原文"版本号表示为m.n，例如X-GB-ver: 3.0"
 * 说明: 本方案采用"2.0"表示GB/T 28181—2022版本
 */
private static final String GB_PROTOCOL_VERSION = "2.0";

/**
 * X-GB-ver头域名称
 * 来源: 2022版附录I规范原文
 */
private static final String HEADER_X_GB_VER = "X-GB-ver";
```

## 变更 2: 新增 getRegisterOkResponseWithVersion 方法

```java
/**
 * 为注册响应添加X-GB-ver头域
 * 来源: 设计文档第13.9节(第1649行起)
 *
 * @param request 注册请求
 * @return 携带X-GB-ver头域的200 OK响应
 */
private Response getRegisterOkResponseWithVersion(Request request) throws ParseException {
    Response okResponse = getMessageFactory().createResponse(Response.OK, request);
    
    // 添加Expires头(原有逻辑)
    if (!userSetting.isDisableDateHeader()) {
        okResponse.addHeader(request.getExpires());
    }
    
    // === 新增: X-GB-ver头域 ===
    // 来源: 设计文档第13.9节, 2022版附录I
    try {
        Header xGbVerHeader = SipFactory.getInstance()
                .createHeaderFactory()
                .createHeader(HEADER_X_GB_VER, GB_PROTOCOL_VERSION);
        okResponse.addHeader(xGbVerHeader);
        log.debug("[注册响应] 添加X-GB-ver头域: {}", GB_PROTOCOL_VERSION);
    } catch (ParseException e) {
        log.warn("[注册响应] 添加X-GB-ver头域失败: {}", e.getMessage());
    }
    
    return okResponse;
}
```

## 变更 3: 新增 extractProtocolVersion 方法

```java
/**
 * 从注册请求中提取对方协议版本
 * 来源: 设计文档第13.9节(第1649行起), 2022版附录I
 *
 * @param request 注册请求
 * @return 对方协议版本号, 未携带则返回null
 */
private String extractProtocolVersion(Request request) {
    Header xGbVerHeader = request.getHeader(HEADER_X_GB_VER);
    if (xGbVerHeader != null) {
        String version = xGbVerHeader.toString()
                .replaceAll(HEADER_X_GB_VER + ":", "").trim();
        log.info("[版本协商] 设备协议版本: {}", version);
        return version;
    }
    // 未携带X-GB-ver头域, 视为2016版设备
    log.debug("[版本协商] 设备未携带X-GB-ver头域, 视为2016版设备");
    return null;
}
```

## 变更 4: 修改 registerHandler 方法

在 `deviceService.online(device)` 之前插入：

```java
// === 改造项1调用: 在注册成功后记录设备协议版本 ===
// 来源: 设计文档第13.9节(第1649行起)
String deviceProtocolVersion = extractProtocolVersion(request);
device.setProtocolVersion(deviceProtocolVersion);

// 使用携带X-GB-ver头域的响应(替换原有 okResponse 构造)
Response okResponse = getRegisterOkResponseWithVersion(request);
sipSender.transmitRequest(request.getLocalAddress().getHostAddress(), okResponse);
```

## 变更 5: Device.java 新增字段

```java
// === 改造项1: Device类新增协议版本字段 ===
// 来源: 设计文档第13.9节(第1649行起), 2022版附录I

/** 设备协议版本(null=2016版设备) */
private String protocolVersion;

public String getProtocolVersion() { return protocolVersion; }
public void setProtocolVersion(String v) { this.protocolVersion = v; }

public boolean isVersion2022() {
    return protocolVersion != null && protocolVersion.startsWith("2.");
}
```
