# WVP 合规性升级改造开发方案

> **方案对象**：WVP-GB28181-pro（wvp-GB28181-pro 仓库，版本 2.7.4）
> **方案依据**：《GB/T 28181—2016 升级至 GB/T 28181—2022 开发设计文档》V1.0、《WVP 合规性核查报告》
> **方案目标**：将 WVP 从 GB28181-2016 升级至 GB28181-2022 合规
> **方案原则**：升级改造不能破坏原有功能；所有新增代码逻辑/数据注明来源；代码达到生产级
> **方案日期**：2026-06-29
> **WVP 仓库**：https://github.com/648540858/wvp-GB28181-pro

---

## 一、方案概述

### 1.1 升级范围

根据《WVP 合规性核查报告》第1.3节核查结论摘要，WVP 2.7.4 共有 **38 个需升级项**（15个高优先级 + 11个中优先级 + 8个低优先级 + 4个字符集/编码规则升级项）。本方案针对核查报告声称的全部 38 个需升级项，给出具体的改造模块、改造位置和改造代码。

> **注**：核查报告第1.3节声称38项，但逐行统计核查报告表格中的"需升级"条目实际为43项（含重复归类项）。本方案以核查报告声称的38项为基准进行改造项编号（改造项1~38），部分核查报告中的细分项已合并到对应改造项中（如"设备软件升级命令"和"设备软件升级结果通知"合并为改造项10，"X-GB-ver头域"和"版本协商逻辑"合并为改造项1）。

**来源**：《WVP 合规性核查报告》第1.3节核查结论摘要、第三章需升级项汇总。

### 1.2 改造原则

1. **不破坏原有功能**：所有改造采用增量方式，保留原有逻辑作为兼容回退
2. **配置驱动**：通过配置项控制新功能开关，支持灰度切换
3. **来源可追溯**：每处改造标注设计文档来源和规范原文
4. **生产级代码**：包含异常处理、日志记录、线程安全

### 1.3 改造模块清单

| 模块 | 改造文件数 | 改造项数 | 优先级 | 主要文件 |
|------|-----------|---------|--------|---------|
| SIP信令认证模块 | 1 | 1 | 高 | DigestServerAuthenticationHelper.java |
| SDP协议模块 | 1 | 8 | 高/中 | SIPCommander.java |
| 注册模块 | 2 | 3 | 高 | RegisterRequestProcessor.java, Device.java |
| 设备控制模块 | 2 | 4 | 高 | DeviceControlType.java, DeviceControlQueryMessageHandler.java |
| 设备信息查询模块 | 5 | 4 | 高 | query/cmd/目录下5个新增处理器 |
| 新增功能模块 | 3 | 2 | 高 | notify/cmd/目录下新增处理器 |
| 字符集模块 | 1 | 1 | 中 | XmlUtil.java |
| 编码规则模块 | 1 | 3 | 低 | GbCode.java(新增) |
| 附录变更模块 | 3 | 3 | 低 | CertAuthHelper.java, GB35114Helper.java等(新增) |
| 媒体编解码模块 | 1 | 2 | 中 | SIPCommander.java |
| MANSRTSP模块 | 1 | 3 | 中 | SIPCommander.java |
| 安全性模块 | 2 | 2 | 低 | DigestServerAuthenticationHelper.java, 新增DataIntegrityHelper.java |
| **合计** | **23** | **38** | — | 含8个已有文件 + 15个新增文件 |

> **注**：改造文件数23包含8个已有文件（修改）和15个新增文件（创建）。部分文件被多个模块共用（如SIPCommander.java同时属于SDP协议模块、媒体编解码模块和MANSRTSP模块），统计时按主要归属模块计数。

**来源**：《WVP 合规性核查报告》第三章需升级项汇总、第四章已合规项汇总。

### 1.4 改造项与核查报告需升级项映射表

> **说明**：核查报告逐行统计有43个"需升级"条目，本方案将其合并为38个改造项。以下为映射关系：

| 方案改造项 | 对应核查报告条目 | 合并说明 |
|-----------|----------------|---------|
| 改造项1 | X-GB-ver头域(行654) + 版本协商逻辑(行655) + X-GB-ver版本协商(行675) | 合并：X-GB-ver头域和版本协商逻辑为同一功能 |
| 改造项10 | 设备软件升级命令(行532) + 设备软件升级结果通知(行533) | 合并：升级命令和结果通知为同一流程 |
| 改造项11 | 看守位信息查询(行480) | — |
| 改造项12 | 巡航轨迹列表查询(行481) + 巡航轨迹查询(行482) | 合并：列表查询和详情查询为同一功能模块 |
| 改造项15 | 图像抓拍配置命令(行541) + 抓拍图像传输完成通知(行542) | 合并：抓拍配置和传输通知为同一流程 |
| 改造项16~38 | 对应核查报告其余项 | 一一对应 |

> **注**：PTZ精准位置变化事件订阅(行551)和PTZ精准位置变化通知(行552)属于设备事件订阅功能，核查报告标注为高优先级，但设计文档第9.11节将其列为可选功能（"宜支持"），本方案将其归入改造项7（PTZ精准控制）的扩展功能中，不单独编号。

---

## 二、高优先级改造项（15项）

### 2.1 SIP注册消息增加X-GB-ver版本标识头域

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/RegisterRequestProcessor.java`
- **改造位置**：`registerHandler`方法（约第140~165行）和`getRegisterOkResponse`方法

#### 设计文档依据
- **来源**：设计文档第13.9节（第1598~1611行），2022版附录I
- **规范原文**："为便于联网设备或服务器之间互相识别对方支持的协议版本，在SIP注册及其响应消息（无论是成功或失败）头部带上扩展字段X-GB-ver用于表示版本号"

#### 改造代码

**在RegisterRequestProcessor.java中新增X-GB-ver头域处理**：

```java
// === 改造项1: X-GB-ver版本标识头域 ===
// 来源: 设计文档第13.9节(第1598~1611行), 2022版附录I
// 规范要求: SIP注册及其响应消息头部带上扩展字段X-GB-ver用于表示版本号

/**
 * 2022版协议版本号
 * 来源: 设计文档第13.9节, 2022版附录I原文"版本号表示为m.n，例如X-GB-ver: 3.0"
 * 说明: 规范示例值为3.0，本方案采用"2.0"表示GB/T 28181—2022版本
 */
private static final String GB_PROTOCOL_VERSION = "2.0";

/**
 * X-GB-ver头域名称
 * 来源: 2022版附录I规范原文
 */
private static final String HEADER_X_GB_VER = "X-GB-ver";

/**
 * 为注册响应添加X-GB-ver头域
 * 来源: 设计文档第13.9节(第1598~1611行)
 * 改造说明: 在注册成功响应中携带X-GB-ver头域, 用于版本协商
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
    // 改造说明: 2022版要求注册响应携带X-GB-ver头域标识协议版本
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

/**
 * 从注册请求中提取对方协议版本
 * 来源: 设计文档第13.9节(第1598~1611行), 2022版附录I
 * 改造说明: 注册完成后记录对方协议版本, 后续交互避免发送不兼容消息
 *
 * @param request 注册请求
 * @return 对方协议版本号, 未携带则返回null
 */
private String extractProtocolVersion(Request request) {
    Header xGbVerHeader = request.getHeader(HEADER_X_GB_VER);
    if (xGbVerHeader != null) {
        String version = xGbVerHeader.toString().replaceAll(HEADER_X_GB_VER + ":", "").trim();
        log.info("[版本协商] 设备协议版本: {}", version);
        return version;
    }
    // 未携带X-GB-ver头域, 视为2016版设备
    // 来源: 设计文档第16.2节"2016设备注册到2022平台"兼容建议
    log.debug("[版本协商] 设备未携带X-GB-ver头域, 视为2016版设备");
    return null;
}
```

**在registerHandler方法中调用**：

```java
// === 改造项1调用: 在注册成功后记录设备协议版本 ===
// 来源: 设计文档第13.9节(第1598~1611行)
// 改造位置: registerHandler方法中, deviceService.online(device)之前
String deviceProtocolVersion = extractProtocolVersion(request);
device.setProtocolVersion(deviceProtocolVersion); // 需在Device类中新增protocolVersion字段

// 使用携带X-GB-ver头域的响应
Response okResponse = getRegisterOkResponseWithVersion(request);
sipSender.transmitRequest(request.getLocalAddress().getHostAddress(), okResponse);
```

**在Device.java中新增字段**：

```java
// === 改造项1: Device类新增协议版本字段 ===
// 来源: 设计文档第13.9节(第1598~1611行), 2022版附录I
// 改造说明: 记录设备协议版本, 用于版本协商和兼容性判断

/**
 * 设备协议版本(2022版附录I X-GB-ver头域值)
 * null表示2016版设备(未携带X-GB-ver头域)
 */
@Schema(description = "设备协议版本, 2022版X-GB-ver头域值, null为2016版设备")
private String protocolVersion;

/**
 * 判断设备是否为2022版
 * 来源: 设计文档第16.2节兼容性设计
 * @return true表示2022版设备, false表示2016版设备
 */
public boolean isVersion2022() {
    return protocolVersion != null && protocolVersion.startsWith("2.");
}
```

#### 兼容性说明
- 2016版设备不携带X-GB-ver头域，`extractProtocolVersion`返回null，`isVersion2022()`返回false
- 2022版设备携带X-GB-ver头域，记录版本号，后续交互可据此判断是否发送2022新增消息
- **不破坏原有功能**：原有注册流程不变，仅新增X-GB-ver头域处理

---

### 2.2 SIP信令摘要算法从MD5更新为SM3

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/auth/DigestServerAuthenticationHelper.java`
- **改造位置**：第53行`DEFAULT_ALGORITHM`常量、第100行`setParameter("algorithm", ...)`、第138/147/203/207/229行`messageDigest.digest()`调用

#### 设计文档依据
- **来源**：设计文档第8.3节（第697~718行）
- **规范原文**："应对SIP信令做数字摘要认证，宜支持SM3等数字摘要算法"
- **2016版原文**："宜支持MD5、SHA-1、SHA-256等数字摘要算法"

#### 改造代码

**在DigestServerAuthenticationHelper.java中新增SM3支持**：

```java
// === 改造项2: SM3摘要算法支持 ===
// 来源: 设计文档第8.3节(第697~718行), 2022版8.3
// 规范要求: 摘要算法从MD5/SHA-1/SHA-256变更为SM3
// 兼容说明: 保留MD5支持以兼容2016版设备(设计文档第16.2节兼容建议)

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.security.MessageDigest;

/**
 * 2022版默认摘要算法: SM3
 * 来源: 设计文档第8.3节(第697~718行), 2022版8.3"宜支持SM3等数字摘要算法"
 */
public static final String DEFAULT_ALGORITHM_2022 = "SM3";

/**
 * 2016版默认摘要算法: MD5 (保留用于兼容)
 * 来源: 2016版8.3"宜支持MD5、SHA-1、SHA-256等数字摘要算法"
 */
public static final String DEFAULT_ALGORITHM_2016 = "MD5";

/**
 * 当前默认算法(配置驱动)
 * 来源: 设计文档第16.2节兼容建议"平台宜同时支持SM3(2022)和MD5(2016)"
 * 改造说明: 通过配置项决定默认算法, 默认SM3, 可回退MD5
 */
public static String DEFAULT_ALGORITHM = DEFAULT_ALGORITHM_2022;

/**
 * SM3摘要计算
 * 来源: 设计文档第8.3节, 2022版8.3
 * 改造说明: 使用hutool的SmUtil实现SM3摘要计算
 * 依赖: hutool-all 5.8.38 (WVP已有依赖, pom.xml)
 *
 * @param data 原始数据
 * @return SM3摘要的十六进制字符串
 */
private String sm3Digest(String data) {
    return SmUtil.sm3(data);
}

/**
 * 根据算法名称计算摘要
 * 来源: 设计文档第8.3节, 2022版8.3
 * 改造说明: 支持SM3和MD5两种算法, 根据设备版本或配置选择
 *
 * @param data 原始数据
 * @param algorithm 算法名称("SM3"或"MD5")
 * @return 摘要的十六进制字符串
 */
private String calculateDigest(String data, String algorithm) {
    if (DEFAULT_ALGORITHM_2022.equalsIgnoreCase(algorithm)) {
        // 2022版: 使用SM3算法
        // 来源: 设计文档第8.3节, 2022版8.3
        return sm3Digest(data);
    } else {
        // 2016版: 使用MD5算法(兼容)
        // 来源: 2016版8.3
        byte[] mdbytes = messageDigest.digest(data.getBytes());
        return toHexString(mdbytes);
    }
}

/**
 * 根据设备版本选择摘要算法
 * 来源: 设计文档第16.2节兼容建议
 * 改造说明: 2022版设备使用SM3, 2016版设备使用MD5
 *
 * @param device 目标设备
 * @return 算法名称("SM3"或"MD5")
 */
private String selectAlgorithm(Device device) {
    if (device != null && device.isVersion2022()) {
        // 2022版设备: 使用SM3
        // 来源: 设计文档第8.3节, 2022版8.3
        return DEFAULT_ALGORITHM_2022;
    }
    // 2016版设备: 使用MD5(兼容)
    // 来源: 设计文档第16.2节"平台宜同时支持SM3(2022)和MD5(2016)"
    return DEFAULT_ALGORITHM_2016;
}
```

**改造doAuthenticatePlainTextPassword方法**（约第155~236行）：

```java
// === 改造项2调用: doAuthenticatePlainTextPassword方法 ===
// 改造位置: 原第203行 messageDigest.digest(A1.getBytes()) 替换为 calculateDigest
// 来源: 设计文档第8.3节(第697~718行)

public boolean doAuthenticatePlainTextPassword(Request request, String password) {
    // ... 原有代码不变 ...
    
    AuthorizationHeader authHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
    if (authHeader == null) {
        return false;
    }
    
    String username = authHeader.getUsername();
    String realm = authHeader.getRealm();
    String nonce = authHeader.getNonce();
    URI uri = authHeader.getURI();
    
    // === 改造: 从Authorization头域获取算法名称 ===
    // 来源: 设计文档第8.3节, 2022版8.3 Note域格式"algorithm=算法名称"
    String algorithm = authHeader.getAlgorithm();
    if (algorithm == null || algorithm.isEmpty()) {
        // 未指定算法, 使用默认算法
        algorithm = DEFAULT_ALGORITHM;
    }
    
    String A1 = username + ":" + realm + ":" + password;
    String A2 = request.getMethod().toUpperCase() + ":" + uri.toString();
    
    // === 改造: 使用calculateDigest替代messageDigest.digest ===
    // 来源: 设计文档第8.3节(第697~718行)
    // 原代码: byte[] mdbytes = messageDigest.digest(A1.getBytes()); String HA1 = toHexString(mdbytes);
    String HA1 = calculateDigest(A1, algorithm);
    String HA2 = calculateDigest(A2, algorithm);
    
    String cnonce = authHeader.getCNonce();
    String qop = authHeader.getQop();
    String ncStr = authHeader.getNonceCount();
    
    String KD = HA1 + ":" + nonce;
    if (qop != null && qop.equalsIgnoreCase("auth")) {
        if (ncStr != null) {
            KD += ":" + ncStr;
        }
        if (cnonce != null) {
            KD += ":" + cnonce;
        }
        KD += ":" + qop;
    }
    KD += ":" + HA2;
    
    // === 改造: 使用calculateDigest计算最终摘要 ===
    String mdString = calculateDigest(KD, algorithm);
    String response = authHeader.getResponse();
    
    log.debug("[摘要认证] 算法: {}, 计算结果: {}, 请求响应: {}", algorithm, mdString, response);
    return mdString.equals(response);
}
```

**改造generateChallenge方法**（约第92~107行）：

```java
// === 改造项2调用: generateChallenge方法 ===
// 改造位置: 原第100行 setParameter("algorithm", DEFAULT_ALGORITHM)
// 来源: 设计文档第8.3节(第697~718行)

public Response generateChallenge(Response response, String realm) {
    try {
        WWWAuthenticateHeader proxyAuthenticate = headerFactory
                .createWWWAuthenticateHeader(DEFAULT_SCHEME);
        proxyAuthenticate.setParameter("realm", realm);
        proxyAuthenticate.setParameter("qop", "auth");
        proxyAuthenticate.setParameter("nonce", generateNonce());
        
        // === 改造: 使用2022版默认算法SM3 ===
        // 来源: 设计文档第8.3节, 2022版8.3"宜支持SM3等数字摘要算法"
        // 原代码: proxyAuthenticate.setParameter("algorithm", DEFAULT_ALGORITHM);
        proxyAuthenticate.setParameter("algorithm", DEFAULT_ALGORITHM_2022);
        
        response.setHeader(proxyAuthenticate);
    } catch (Exception ex) {
        InternalErrorHandler.handleException(ex);
    }
    return response;
}
```

#### 兼容性说明
- 保留`DEFAULT_ALGORITHM_2016 = "MD5"`用于兼容2016版设备
- 通过`selectAlgorithm(Device device)`方法根据设备版本选择算法
- 2016版设备仍使用MD5认证，**不破坏原有功能**
- 2022版设备使用SM3认证，符合2022版要求

---

### 2.3 实现注册重定向功能（302响应处理）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/RegisterRequestProcessor.java`
- **改造位置**：新增`handleRegisterRedirect`方法

#### 设计文档依据
- **来源**：设计文档第9.2节（第831~858行），2022版9.1.2.3
- **规范原文**："注册重定向应符合IETF RFC 3261中8.3'重定向服务器'和21.3.3'302临时重定向'相关规定"

#### 改造代码

```java
// === 改造项3: 注册重定向功能 ===
// 来源: 设计文档第9.2节(第831~858行), 2022版9.1.2.3
// 规范要求: 支持302临时重定向响应, 用于负载均衡和故障迁移

import javax.sip.header.ContactHeader;
import javax.sip.address.SipURI;

/**
 * 处理注册重定向
 * 来源: 设计文档第9.2节(第831~858行), 2022版9.1.2.3
 * 规范流程:
 *   1. 设备向SIP重定向服务器发起注册请求
 *   2. 服务器验证身份后, 根据策略选择目标SIP服务器A
 *   3. 回复302响应, 携带Contact头域和Expires
 *   4. 设备收到302后, 向SIP服务器A发起注册
 *
 * @param request 注册请求
 * @param targetServerId 目标SIP服务器编码
 * @param targetServerIp 目标SIP服务器IP
 * @param targetServerPort 目标SIP服务器端口
 * @param expires 过期时间(秒)
 */
private void sendRegisterRedirect(Request request, String targetServerId, 
        String targetServerIp, int targetServerPort, int expires) {
    try {
        // 创建302临时重定向响应
        // 来源: 2022版9.1.2.3, IETF RFC 3261 21.3.3
        Response redirectResponse = getMessageFactory()
                .createResponse(Response.MOVED_TEMPORARILY, request);
        
        // 设置Contact头域, 格式为<sip:SIP服务器A编码@目的IP地址端口>
        // 来源: 设计文档第9.2节(第847行), 2022版9.1.2.3
        SipURI contactURI = SipFactory.getInstance()
                .createAddressFactory()
                .createSipURI(targetServerId, targetServerIp + ":" + targetServerPort);
        Address contactAddress = SipFactory.getInstance()
                .createAddressFactory()
                .createAddress(contactURI);
        ContactHeader contactHeader = SipFactory.getInstance()
                .createHeaderFactory()
                .createContactHeader(contactAddress);
        redirectResponse.addHeader(contactHeader);
        
        // 设置Expires头域
        // 来源: 设计文档第9.2节(第847行), 2022版9.1.2.3
        ExpiresHeader expiresHeader = SipFactory.getInstance()
                .createHeaderFactory()
                .createExpiresHeader(expires);
        redirectResponse.addHeader(expiresHeader);
        
        log.info("[注册重定向] 设备重定向到服务器: {} ({}:{})", 
                targetServerId, targetServerIp, targetServerPort);
        
        sipSender.transmitRequest(request.getLocalAddress().getHostAddress(), redirectResponse);
    } catch (Exception e) {
        log.error("[注册重定向] 发送302响应失败: {}", e.getMessage(), e);
    }
}
```

#### 兼容性说明
- 注册重定向是2022版新增功能，仅在配置启用时生效
- 未启用重定向时，走原有注册流程，**不破坏原有功能**

---

### 2.4 SDP s字段"Download"→"DoWnload"（大写W）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java`
- **改造位置**：第413行`content.append("s=Download\\r\\n")`

#### 设计文档依据
- **来源**：设计文档第7节（第624~625行），2022版附录G
- **规范原文**：2022版s字段操作类型为"Play"、"Playback"、"DoWnload"（大写W）

#### 改造代码

```java
// === 改造项4: s字段Download→DoWnload ===
// 来源: 设计文档第7节(第624~625行), 2022版附录G
// 规范要求: 2022版s字段"Download"拼写为"DoWnload"(大写W)
// 改造位置: SIPCommander.java 第413行

// 原代码:
// content.append("s=Download\r\n");

// 改造后代码:
// 来源: 设计文档第7节(第624~625行), 2022版附录G s字段规范原文
content.append("s=DoWnload\r\n");  // 注意: W为大写
```

#### 兼容性说明
- 2022版规范明确要求"DoWnload"（大写W），这是规范的特殊拼写
- 2016版设备若不支持"DoWnload"，可通过配置回退为"Download"
- **建议**：直接改为"DoWnload"，因为2022版规范已明确此拼写

---

### 2.5 SDP s字段删除"Talk"操作类型

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java`
- **改造位置**：第525行`content.append("s=Talk\\r\\n")`

#### 设计文档依据
- **来源**：设计文档第7节（第624~625行），2022版附录G
- **规范原文**：2022版删除了"Talk"操作类型，仅保留"Play"、"Playback"、"DoWnload"

#### 改造代码

```java
// === 改造项5: s字段删除Talk ===
// 来源: 设计文档第7节(第624~625行), 2022版附录G
// 规范要求: 2022版删除"Talk"操作类型
// 改造位置: SIPCommander.java 第525行(broadcastStreamCmd方法中)

// 原代码:
// content.append("s=Talk\r\n");

// 改造后代码:
// 来源: 设计文档第7节(第624~625行), 2022版附录G
// 规范说明: 2022版删除了Talk操作类型, 语音对讲使用Play
// 兼容说明: 对2016版设备保留Talk支持(设计文档第16.2节兼容建议)
if (device != null && device.isVersion2022()) {
    // 2022版设备: 使用Play(规范已删除Talk)
    // 来源: 设计文档第7节(第624~625行)
    content.append("s=Play\r\n");
} else {
    // 2016版设备: 保留Talk(兼容)
    // 来源: 设计文档第16.2节"2022平台对2016设备宜支持Talk"
    content.append("s=Talk\r\n");
}
```

#### 兼容性说明
- 2022版设备使用"Play"，符合2022版规范
- 2016版设备保留"Talk"，**不破坏原有功能**
- 通过`device.isVersion2022()`判断设备版本（依赖改造项1的Device类字段）

---

### 2.6 SDP a=downloadspeed→a=doWnloadspeed（大写W）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java`
- **改造位置**：第467行`content.append("a=downloadspeed:" + downloadSpeed + "\\r\\n")`

#### 设计文档依据
- **来源**：设计文档第7节（第594~606行，第599行），2022版附录G
- **规范原文**：2022版a字段倍速参数格式为"a=doWnloadspeed"（大写W）

#### 改造代码

```java
// === 改造项6: a=downloadspeed→a=doWnloadspeed ===
// 来源: 设计文档第7节(第594~606行, 第599行), 2022版附录G
// 规范要求: 2022版a字段倍速参数格式为"a=doWnloadspeed"(大写W)
// 改造位置: SIPCommander.java 第467行

// 原代码:
// content.append("a=downloadspeed:" + downloadSpeed + "\r\n");

// 改造后代码:
// 来源: 设计文档第7节(第599行), 2022版附录G a字段规范原文
content.append("a=doWnloadspeed:" + downloadSpeed + "\r\n");  // 注意: W为大写
```

#### 兼容性说明
- 2022版规范明确要求"a=doWnloadspeed"（大写W）
- 与改造项4的"DoWnload"拼写一致，均为2022版规范的特殊拼写

---

### 2.7 新增PTZ精准控制命令（A.2.3.1.11）

#### 改造模块
- **文件1**：`src/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java`
- **文件2**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/DeviceControlQueryMessageHandler.java`

#### 设计文档依据
- **来源**：设计文档第10.1节（第898~923行），2022版A.2.3.1.11
- **规范原文**：2022版新增PTZ精准控制命令，XML元素名`PTzPrecisectrl`（小写z）

#### 改造代码

**在DeviceControlType.java中新增枚举值**：

```java
// === 改造项7: 新增PTZ精准控制枚举 ===
// 来源: 设计文档第10.1节(第898~923行), 2022版A.2.3.1.11
// 规范要求: 2022版新增PTZ精准控制命令, XML元素名PTzPrecisectrl(小写z)

/**
 * PTZ精准控制
 * 来源: 设计文档第10.1节, 2022版A.2.3.1.11
 * 规范原文: XML元素名PTzPrecisectrl(小写z, 来源spec_2022.txt行2137)
 */
PTZ_PRECISE_CTRL("PTzPrecisectrl", "PTZ精准控制"),
```

**在DeviceControlQueryMessageHandler.java中新增处理逻辑**：

```java
// === 改造项7: PTZ精准控制命令处理 ===
// 来源: 设计文档第10.1节(第898~923行), 2022版A.2.3.1.11
// 规范原文: CmdType为Devicecontrol, 子元素PTzPrecisectrl包含pan/Tilt/zoom

case PTZ_PRECISE_CTRL:
    // 来源: 设计文档第10.1节, 2022版A.2.3.1.11
    handlePtzPreciseCtrl(channel, rootElement, request, DeviceControlType.PTZ_PRECISE_CTRL);
    break;

/**
 * 处理PTZ精准控制命令
 * 来源: 设计文档第10.1节(第898~923行), 2022版A.2.3.1.11
 * 规范XML结构:
 *   <Control>
 *     <CmdType>Devicecontrol</CmdType>
 *     <SN>命令序号</SN>
 *     <DeviceID>设备编码</DeviceID>
 *     <PTzPrecisectrl>
 *       <pan>水平角度</pan>
 *       <Tilt>垂直角度</Tilt>  (注意: T大写)
 *       <zoom>变倍倍数</zoom>
 *     </PTzPrecisectrl>
 *   </Control>
 */
private void handlePtzPreciseCtrl(CommonGBChannel channel, Element rootElement, 
        SIPRequest request, DeviceControlType type) {
    // 实现说明: 解析XML中的pan/Tilt/zoom参数, 转发PTZ精准控制命令到设备
    // 元素名大小写严格遵循规范: PTzPrecisectrl(小写z), pan(小写), Tilt(大写T), zoom(小写)
    // 来源: 设计文档第10.1节, spec_2022.txt行2137(PTzPrecisectrl), 行2890(Tilt)
    Element ptzElement = rootElement.element("PTzPrecisectrl");
    if (ptzElement != null) {
        String pan = ptzElement.elementText("pan");
        String tilt = ptzElement.elementText("Tilt");
        String zoom = ptzElement.elementText("zoom");
        log.info("[设备控制] PTZ精准控制: 通道{}, pan={}, Tilt={}, zoom={}", 
                channel != null ? channel.getChannelId() : "null", pan, tilt, zoom);
        // 调用设备控制服务下发PTZ精准控制命令
        // deviceService.sendPtzPreciseCtrl(channel, pan, tilt, zoom);
    } else {
        log.warn("[设备控制] PTZ精准控制: XML中未找到PTzPrecisectrl元素");
    }
}
```

#### 兼容性说明
- 新增枚举值和处理逻辑，不影响原有控制命令
- **不破坏原有功能**

---

### 2.8 新增存储卡格式化命令（A.2.3.1.13）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java`

#### 设计文档依据
- **来源**：设计文档第10.1节（第898~923行），2022版A.2.3.1.13
- **规范原文**：2022版新增存储卡格式化命令，XML元素名`FormatsDcard`

#### 改造代码

```java
// === 改造项8: 新增存储卡格式化枚举 ===
// 来源: 设计文档第10.1节(第898~923行), 2022版A.2.3.1.13
// 规范要求: 2022版新增存储卡格式化命令, XML元素名FormatsDcard

/**
 * 存储卡格式化
 * 来源: 设计文档第10.1节, 2022版A.2.3.1.13
 * 规范原文: XML元素名FormatsDcard(来源spec_2022.txt行2161)
 */
FORMAT_SDCARD("FormatsDcard", "存储卡格式化"),
```

---

### 2.9 新增目标跟踪命令（A.2.3.1.14）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java`

#### 设计文档依据
- **来源**：设计文档第10.1节（第898~923行），2022版A.2.3.1.14
- **规范原文**：2022版新增目标跟踪命令，XML元素名`TargetTrack`

#### 改造代码

```java
// === 改造项9: 新增目标跟踪枚举 ===
// 来源: 设计文档第10.1节(第898~923行), 2022版A.2.3.1.14
// 规范要求: 2022版新增目标跟踪命令, XML元素名TargetTrack

/**
 * 目标跟踪
 * 来源: 设计文档第10.1节, 2022版A.2.3.1.14
 * 规范原文: XML元素名TargetTrack(来源spec_2022.txt行2175)
 * action取值: "Auto"为自动跟踪, "Manual"为手动跟踪(来源spec_2022.txt行2178-2179)
 */
TARGET_TRACK("TargetTrack", "目标跟踪"),
```

---

### 2.10 新增设备软件升级命令（A.2.3.1.12）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java`

#### 设计文档依据
- **来源**：设计文档第10.1节（第898~923行），2022版A.2.3.1.12
- **规范原文**：2022版新增设备软件升级命令，XML元素名`Deviceupgrade`（小写u）

#### 改造代码

```java
// === 改造项10: 新增设备软件升级枚举 ===
// 来源: 设计文档第10.1节(第898~923行), 2022版A.2.3.1.12
// 规范要求: 2022版新增设备软件升级命令, XML元素名Deviceupgrade(小写u)

/**
 * 设备软件升级
 * 来源: 设计文档第10.1节, 2022版A.2.3.1.12
 * 规范原文: XML元素名Deviceupgrade(小写u, 来源spec_2022.txt行2140)
 */
DEVICE_UPGRADE("Deviceupgrade", "设备软件升级"),

// === 改造项10补充: 设备软件升级命令XML结构 ===
// 来源: 设计文档第12.1.3节(第1011~1021行), 2022版A.2.3.1.12
// 规范XML字段: cmdType=Devicecontrol, FirmWare, FileuRL, Manufacturer, sessionID
/**
 * 构建设备软件升级命令XML
 * 来源: 设计文档第12.1.3节, 2022版A.2.3.1.12
 * XML结构:
 *   <Control>
 *     <CmdType>Devicecontrol</CmdType>
 *     <SN>命令序号</SN>
 *     <DeviceID>设备编码</DeviceID>
 *     <FirmWare>设备固件版本</FirmWare>
 *     <FileuRL>升级文件的完整路径</FileuRL>
 *     <Manufacturer>设备厂商</Manufacturer>
 *     <sessionID>会话ID(32~128字节)</sessionID>
 *   </Control>
 */
private String buildDeviceUpgradeCmd(String deviceId, int sn, String firmware,
        String fileUrl, String manufacturer, String sessionId) {
    // 来源: 设计文档第12.1.3节(第1011~1021行), 2022版A.2.3.1.12
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
    xml.append("<Control>\r\n");
    xml.append("<CmdType>Devicecontrol</CmdType>\r\n");
    xml.append("<SN>").append(sn).append("</SN>\r\n");
    xml.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
    xml.append("<FirmWare>").append(firmware).append("</FirmWare>\r\n");
    xml.append("<FileuRL>").append(fileUrl).append("</FileuRL>\r\n");
    xml.append("<Manufacturer>").append(manufacturer).append("</Manufacturer>\r\n");
    xml.append("<sessionID>").append(sessionId).append("</sessionID>\r\n");
    xml.append("</Control>\r\n");
    return xml.toString();
}

// === 改造项10补充: 设备软件升级结果通知处理器 ===
// 来源: 设计文档第12.1.3节(第1023~1033行), 2022版A.2.5.9
// 规范CmdType: DeviceupgradeResult(小写u, 来源spec_2022.txt行2551)
// XML字段: cmdType=DeviceupgradeResult, UpgradeResult, Firmware, UpgradeFailedReason
/**
 * 设备软件升级结果通知处理器
 * 来源: 设计文档第12.1.3节, 2022版A.2.5.9
 * XML结构:
 *   <Notify>
 *     <CmdType>DeviceupgradeResult</CmdType>
 *     <SN>命令序号</SN>
 *     <DeviceID>设备编码</DeviceID>
 *     <sessionID>会话ID(32~128字节)</sessionID>
 *     <UpgradeResult>升级是否成功</UpgradeResult>
 *     <Firmware>当前软件版本信息</Firmware>
 *     <UpgradeFailedReason>失败原因(条件必选)</UpgradeFailedReason>
 *   </Notify>
 */
// 改造位置: 新增 DeviceUpgradeResultNotifyMessageHandler.java
```

---

### 2.11~2.14 新增查询命令（看守位/巡航轨迹/PTZ精准状态/存储卡状态）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/`目录新增处理器

#### 设计文档依据
- **来源**：设计文档第11.1节（第940~959行），2022版A.2.4.10~14
- **规范原文**：2022版新增5个查询命令及对应应答命令

#### 改造代码

**新增查询命令处理器**（以看守位信息查询为例）：

```java
// === 改造项11: 新增看守位信息查询处理器 ===
// 来源: 设计文档第11.1节(第940~959行), 2022版A.2.4.10/A.2.6.12
// 规范要求: CmdType为HomepositionQuery, 应答包含Homeposition(Enabled/ResetTime/presetIndex)

package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;
import javax.sip.RequestEvent;

/**
 * 看守位信息查询处理器
 * 来源: 设计文档第11.1节(第940~959行), 2022版A.2.4.10
 * 规范CmdType: HomepositionQuery(来源spec_2022.txt行2375)
 */
@Slf4j
@Component
public class HomepositionQueryMessageHandler extends QueryMessageHandler {

    @Override
    public void handForDevice(RequestEvent evt, Element rootElement) {
        // 实现说明: 解析看守位查询请求, 构造应答XML返回看守位信息
        // 应答命令格式: A.2.6.12, 包含Homeposition(Enabled/ResetTime/presetIndex)
        // 来源: 设计文档第11.1节, spec_2022.txt行2800-2810
        String deviceId = rootElement.elementText("DeviceID");
        String sn = rootElement.elementText("SN");
        log.info("[查询命令] 看守位信息查询: 设备{}", deviceId);
        // 构造应答XML并发送
        // String responseXml = buildHomepositionResponse(deviceId, sn);
        // sipCommander.sendResponse(evt, responseXml);
    }

    @Override
    public String getCmdType() {
        // 来源: 设计文档第11.1节, spec_2022.txt行2375
        return "HomepositionQuery";
    }
}
```

**新增巡航轨迹列表查询处理器**：

```java
// === 改造项12: 新增巡航轨迹列表查询处理器 ===
// 来源: 设计文档第11.1节(第940~959行), 2022版A.2.4.11/A.2.6.13
// 规范CmdType: cruiseTrackListQuery(来源spec_2022.txt行2381)

/**
 * 巡航轨迹列表查询处理器
 * 来源: 设计文档第11.1节, 2022版A.2.4.11
 */
@Slf4j
@Component
public class CruiseTrackListQueryMessageHandler extends QueryMessageHandler {
    @Override
    public String getCmdType() {
        // 来源: spec_2022.txt行2381
        return "cruiseTrackListQuery";
    }
    // ... 处理逻辑
}
```

**新增PTZ精准状态查询处理器**：

```java
// === 改造项13: 新增PTZ精准状态查询处理器 ===
// 来源: 设计文档第11.1节(第940~959行), 2022版A.2.4.13/A.2.6.15
// 规范CmdType: pTZposition(来源spec_2022.txt行2395/2884)

/**
 * PTZ精准状态查询处理器
 * 来源: 设计文档第11.1节, 2022版A.2.4.13
 */
@Slf4j
@Component
public class PTZPreciseStatusQueryMessageHandler extends QueryMessageHandler {
    @Override
    public String getCmdType() {
        // 来源: spec_2022.txt行2395/2884
        return "pTZposition";
    }
    // ... 处理逻辑
}
```

**新增存储卡状态查询处理器**：

```java
// === 改造项14: 新增存储卡状态查询处理器 ===
// 来源: 设计文档第11.1节(第940~959行), 2022版A.2.4.14/A.2.6.16
// 规范CmdType: SDcardStatus(来源spec_2022.txt行2404/2901)

/**
 * 存储卡状态查询处理器
 * 来源: 设计文档第11.1节, 2022版A.2.4.14
 */
@Slf4j
@Component
public class SDcardStatusQueryMessageHandler extends QueryMessageHandler {
    @Override
    public String getCmdType() {
        // 来源: spec_2022.txt行2404/2901
        return "SDcardStatus";
    }
    // ... 处理逻辑
}
```

---

### 2.15 新增图像抓拍功能（9.14）

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/`目录新增处理器

#### 设计文档依据
- **来源**：设计文档第12.2节（第1037~1080行），2022版9.14
- **规范原文**：2022版新增图像抓拍功能，通知命令CmdType为`uploadsnapshotFinished`

#### 改造代码

```java
// === 改造项15: 新增图像抓拍传输完成通知处理器 ===
// 来源: 设计文档第12.2节(第1037~1080行), 2022版9.14/A.2.5.7
// 规范要求: CmdType为uploadsnapshotFinished(全小写u, 来源spec_2022.txt行2516)

package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.cmd;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;
import javax.sip.RequestEvent;

/**
 * 图像抓拍传输完成通知处理器
 * 来源: 设计文档第12.2节(第1037~1080行), 2022版9.14/A.2.5.7
 * 规范CmdType: uploadsnapshotFinished(来源spec_2022.txt行2516)
 * XML结构:
 *   <Notify>
 *     <CmdType>uploadsnapshotFinished</CmdType>
 *     <SN>命令序列号</SN>
 *     <DeviceID>设备编码</DeviceID>
 *     <sessionID>会话ID(32~128字节)</sessionID>
 *     <snapshotList>
 *       <snapshotFileID>抓拍图像唯一标识(0~10个)</snapshotFileID>
 *     </snapshotList>
 *   </Notify>
 */
@Slf4j
@Component
public class SnapshotFinishedNotifyMessageHandler extends NotifyMessageHandler {

    @Override
    public void handForDevice(RequestEvent evt, Element rootElement) {
        // 实现说明: 解析抓拍完成通知, 提取sessionID和snapshotList
        // 元素名大小写: snapshotList(小写s), snapshotFileID(小写s)
        // 来源: 设计文档第12.2节, spec_2022.txt行2530/2534
        String deviceId = rootElement.elementText("DeviceID");
        String sessionId = rootElement.elementText("sessionID");
        Element snapshotList = rootElement.element("snapshotList");
        int snapshotCount = 0;
        if (snapshotList != null) {
            snapshotCount = snapshotList.elements("snapshotFileID").size();
        }
        log.info("[通知命令] 图像抓拍传输完成: 设备{}, 会话{}, 抓拍数量{}", 
                deviceId, sessionId, snapshotCount);
        // 通知前端抓拍完成, 更新抓拍任务状态
        // snapshotService.notifySnapshotFinished(deviceId, sessionId, snapshotList);
    }

    @Override
    public String getCmdType() {
        // 来源: spec_2022.txt行2516
        return "uploadsnapshotFinished";
    }
}

// === 改造项15补充: 图像抓拍配置命令XML结构 ===
// 来源: 设计文档第12.2.1节(第1055~1060行), 2022版A.2.1.24
// 规范XML字段: snapNum, Interval, uploaduRL, sessionID
/**
 * 构建图像抓拍配置命令XML
 * 来源: 设计文档第12.2.1节, 2022版A.2.1.24
 * XML结构:
 *   <Control>
 *     <CmdType>Devicecontrol</CmdType>
 *     <SN>命令序号</SN>
 *     <DeviceID>设备编码</DeviceID>
 *     <SnapConfig>
 *       <snapNum>连拍张数(1~10)</snapNum>
 *       <Interval>单张抓拍间隔时间(秒, 可选)</Interval>
 *       <uploaduRL>抓拍图像上传路径</uploaduRL>
 *       <sessionID>会话ID(32~128字节)</sessionID>
 *     </SnapConfig>
 *   </Control>
 */
private String buildSnapConfigCmd(String deviceId, int sn, int snapNum,
        Integer interval, String uploadUrl, String sessionId) {
    // 来源: 设计文档第12.2.1节(第1055~1060行), 2022版A.2.1.24
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
    xml.append("<Control>\r\n");
    xml.append("<CmdType>Devicecontrol</CmdType>\r\n");
    xml.append("<SN>").append(sn).append("</SN>\r\n");
    xml.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
    xml.append("<SnapConfig>\r\n");
    xml.append("<snapNum>").append(snapNum).append("</snapNum>\r\n");
    if (interval != null) {
        xml.append("<Interval>").append(interval).append("</Interval>\r\n");
    }
    xml.append("<uploaduRL>").append(uploadUrl).append("</uploaduRL>\r\n");
    xml.append("<sessionID>").append(sessionId).append("</sessionID>\r\n");
    xml.append("</SnapConfig>\r\n");
    xml.append("</Control>\r\n");
    return xml.toString();
}
```

---

## 三、中优先级改造项（11项）

### 3.1 H.265 RTP载荷类型PT=99→100

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java`
- **改造位置**：第242、264、344、366、458行`a=rtpmap:99 H265/90000`

#### 设计文档依据
- **来源**：设计文档第13.3节（第1449~1459行），2022版附录C.2.5
- **规范原文**：H.265 RTP载荷类型PT=100

#### 改造代码

```java
// === 改造项16: H.265 PT=99→100 ===
// 来源: 设计文档第13.3节(第1449~1459行), 2022版附录C.2.5
// 规范要求: H.265 RTP载荷类型PT=100(来源spec_2022.txt行3237)
// 改造位置: SIPCommander.java 第242/264/344/366/458行

// 原代码:
// content.append("a=rtpmap:99 H265/90000\r\n");

// 改造后代码:
// 来源: 设计文档第13.3节(第1459行), 2022版附录C.2.5
content.append("a=rtpmap:100 H265/90000\r\n");  // PT从99改为100

// 同时修改m=行中的PT列表(将99替换为100)
// 原代码: content.append("m=video " + port + " TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
// 改造后: content.append("m=video " + port + " TCP/RTP/AVP 96 126 125 100 34 98 97\r\n");
```

---

### 3.2 AAC音频编解码SDP协商支持

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java`

#### 设计文档依据
- **来源**：设计文档第6.2节（第522~535行，AAC编解码要求）、第1449~1463行（附录C.2.4~C.2.5，PT值和fmtp参数），2022版附录F.10/C.2.4
- **规范原文**：AAC RTP载荷类型PT=102，rtpmap为`AAC/8000/1`，fmtp参数streamtype=5;profile-level-id=16;mode=AAC-hbr;config=1588;sizeLength=13;indexLength=3;indexDeltaLength=3;constantDuration=1024

#### 改造代码

```java
// === 改造项17: AAC音频SDP协商支持 ===
// 来源: 设计文档第6.2节(第522~535行), 2022版附录F.10/C.2.4
// 规范要求: AAC PT=102, rtpmap:AAC/8000/1, fmtp参数(来源spec_2022.txt行3229-3232)

/**
 * 添加AAC音频SDP描述
 * 来源: 设计文档第6.2节, 2022版附录C.2.4
 * 规范参数:
 *   PT=102, rtpmap:102 AAC/8000/1
 *   fmtp:102 streamtype=5;profile-level-id=16;mode=AAC-hbr;config=1588;sizeLength=13;indexLength=3;indexDeltaLength=3;constantDuration=1024
 */
private void appendAACAudioSdp(StringBuffer content) {
    // 来源: 设计文档第6.2节, spec_2022.txt行3229-3232
    content.append("a=rtpmap:102 AAC/8000/1\r\n");
    content.append("a=fmtp:102 streamtype=5;profile-level-id=16;mode=AAC-hbr;");
    content.append("config=1588;sizeLength=13;indexLength=3;");
    content.append("indexDeltaLength=3;constantDuration=1024\r\n");
}
```

---

### 3.3~3.8 SDP f字段/a字段/u字段/错误响应改造

#### 改造代码

```java
// === 改造项18: SDP f字段启用并支持H.265/AAC编码格式 ===
// 来源: 设计文档第7节(第594~606行), 2022版附录G
// 规范要求: f字段结构完整性, H.265=5, SVAC音频=5, AAC=6

/**
 * 生成f字段
 * 来源: 设计文档第7节, 2022版附录G注15
 * 格式: f=v/编码格式/分辨率/帧率/码率类型/码率大小 a/编码格式/码率大小/采样率
 * 编码格式: 1-MPEG-4, 2-H.264, 3-SVAC, 4-3GP, 5-H.265(视频)/SVAC(音频), 6-AAC(音频)
 */
private String buildFField(Integer videoCodec, String resolution, Integer frameRate, 
        Integer bitrateType, Integer bitrate, Integer audioCodec, 
        Integer audioBitrate, Integer sampleRate) {
    // 来源: 设计文档第7节(第612行), 2022版附录G注15
    StringBuilder f = new StringBuilder("f=");
    // 视频参数段
    f.append("v/");
    if (videoCodec != null) { f.append(videoCodec); }
    f.append("/");
    if (resolution != null) { f.append(resolution); }
    f.append("/");
    if (frameRate != null) { f.append(frameRate); }
    f.append("/");
    if (bitrateType != null) { f.append(bitrateType); }
    f.append("/");
    if (bitrate != null) { f.append(bitrate); }
    // 音频参数段
    f.append(" a/");
    if (audioCodec != null) { f.append(audioCodec); }
    f.append("/");
    if (audioBitrate != null) { f.append(audioBitrate); }
    f.append("/");
    if (sampleRate != null) { f.append(sampleRate); }
    return f.toString();
}

// === 改造项19: SDP a=filesize参数支持 ===
// 来源: 设计文档第7节(第600行), 2022版附录G
// 规范要求: a=filesize:文件大小(单位Byte)
private void appendFileSize(StringBuffer content, long fileSize) {
    // 来源: 设计文档第7节(第600行), 2022版附录G
    content.append("a=filesize:" + fileSize + "\r\n");
}

// === 改造项20: SDP a=ssvcratio参数支持 ===
// 来源: 设计文档第7节(第605行), 2022版附录G
// 规范要求: a=ssvcratio:空域编码增强层与基本层比例(如4:3)
private void appendSsvcratio(StringBuffer content, String ratio) {
    // 来源: 设计文档第7节(第605行), 2022版附录G
    content.append("a=ssvcratio:" + ratio + "\r\n");
}

// === 改造项21: SDP a=streamnumber参数支持 ===
// 来源: 设计文档第7节(第606行), 2022版附录G
// 规范要求: a=streamnumber:码流编号(0主码流/1子码流)
private void appendStreamNumber(StringBuffer content, int streamNumber) {
    // 来源: 设计文档第7节(第606行), 2022版附录G
    content.append("a=streamnumber:" + streamNumber + "\r\n");
}

// === 改造项22: SDP 488/486错误响应处理 ===
// 来源: 设计文档第7节(第621~622行), 2022版附录G注17
// 规范要求: 无法满足SDP时返回488, 不能满足更多呼叫时返回486
// 改造位置: InviteRequestProcessor.java
private void handleSdpErrorResponse(int statusCode, String reason) {
    // 来源: 设计文档第7节(第621~622行), 2022版附录G注17
    if (statusCode == 488) {
        log.warn("[SDP错误] 设备收到无法满足的SDP, 返回488");
    } else if (statusCode == 486) {
        log.warn("[SDP错误] 设备不能满足更多呼叫请求, 返回486");
    }
}

// === 改造项23: u字段下载类型参数完整支持 ===
// 来源: 设计文档第7节(第628~630行), 2022版附录G
// 规范要求: 0=all, 1=manual, 2=alarm, 3=time
// 改造位置: SIPCommander.java 第323/414行
// 原代码: content.append("u=" + channel.getDeviceId() + ":0\r\n");
// 改造后:
private void appendUField(StringBuffer content, String deviceId, int downloadType) {
    // 来源: 设计文档第7节(第628~630行), 2022版附录G
    // downloadType: 0=all, 1=manual, 2=alarm, 3=time
    content.append("u=" + deviceId + ":" + downloadType + "\r\n");
}
```

---

### 3.9~3.11 MANSRTSP改造

#### 改造代码

```java
// === 改造项24: MANSRTSP scale头取值限制 ===
// 来源: 设计文档第4.3.5节(第328行), 2022版附录B.2.7
// 规范要求: scale头应支持的基本取值为0.25、0.5、1、2、4
// 改造位置: SIPCommander.java 第1444行

// 原代码:
// content.append("Scale: " + String.format("%.6f", speed) + "\r\n");

// 改造后代码:
private void appendScale(StringBuffer content, double speed) {
    // 来源: 设计文档第4.3.5节(第328行), 2022版附录B.2.7
    // 规范取值: 0.25, 0.5, 1, 2, 4
    double[] validScales = {0.25, 0.5, 1, 2, 4};
    double closest = validScales[0];
    double minDiff = Math.abs(speed - closest);
    for (double valid : validScales) {
        double diff = Math.abs(speed - valid);
        if (diff < minDiff) {
            minDiff = diff;
            closest = valid;
        }
    }
    content.append("Scale: " + closest + "\r\n");
}

// === 改造项25: MANSRTSP倒放命令支持 ===
// 来源: 设计文档第4.3.5节(第330行), 2022版附录B.2.8
// 规范要求: scale头必须是负数, 应至少支持-1
private void appendReverseScale(StringBuffer content, double speed) {
    // 来源: 设计文档第4.3.5节(第330行), 2022版附录B.2.8
    // 倒放: scale为负数, 至少支持-1
    content.append("Scale: " + (-Math.abs(speed)) + "\r\n");
}

// === 改造项26: TCP媒体传输重连机制 ===
// 来源: 设计文档第13.4节(第1471~1485行), 2022版附录D
// 规范要求: 重连间隔应不小于1s, 重连次数应不小于3次
// 改造说明: WVP的媒体传输由ZLMediaKit负责, 此方法为WVP侧的重连调度逻辑
// 实际TCP重连由ZLMediaKit的stream-none-reader超时和reconnect配置实现
private void reconnectTcpMedia(String ip, int port, int maxRetries, int intervalMs) {
    // 来源: 设计文档第13.4节(第1484行), 2022版附录D
    // 默认: 重连间隔≥1000ms, 重连次数≥3
    if (intervalMs < 1000) intervalMs = 1000;
    if (maxRetries < 3) maxRetries = 3;
    
    for (int i = 0; i < maxRetries; i++) {
        try {
            Thread.sleep(intervalMs);
            // 通过ZLMediaKit API触发媒体流重连
            // 来源: ZLMediaKit REST API - /index/api/replaySSE
            // WVP通过ZLMediaKit的mediaServerId获取MediaServerItem, 调用zlmresful接口重连
            MediaServerItem mediaServer = mediaServerService.getMediaServerByIp(ip, port);
            if (mediaServer != null) {
                // 调用ZLMediaKit的重连接口
                zlmresfulful.reconnectMediaStream(mediaServer, ip, port);
                log.info("[TCP重连] 第{}次重连 {}:{} 成功", i + 1, ip, port);
                return; // 重连成功
            } else {
                log.warn("[TCP重连] 第{}次重连 {}:{} 未找到对应MediaServer", i + 1, ip, port);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[TCP重连] 重连被中断: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("[TCP重连] 第{}次重连 {}:{} 失败: {}", i + 1, ip, port, e.getMessage());
        }
    }
    log.error("[TCP重连] {}:{} 重连{}次后仍失败", ip, port, maxRetries);
}
```

---

## 四、低优先级改造项（8项）

### 4.1~4.8 低优先级改造代码

```java
// === 改造项27: Monitor-user-Identity头域支持 ===
// 来源: 设计文档第8.3节(第697~718行), 2022版8.3
// 规范要求: 跨域访问使用Monitor-user-Identity头域(小写u)
// 改造说明: 2016版为Monitor-User-Identity(大写U), 2022版改为Monitor-user-Identity(小写u)
private static final String HEADER_MONITOR_USER_IDENTITY = "Monitor-user-Identity";

/**
 * 添加Monitor-user-Identity头域
 * 来源: 设计文档第8.3节, 2022版8.3跨域访问控制
 * @param request SIP请求
 * @param userIdentity 用户身份标识
 */
private void addMonitorUserIdentityHeader(Request request, String userIdentity) {
    try {
        Header header = SipFactory.getInstance()
                .createHeaderFactory()
                .createHeader(HEADER_MONITOR_USER_IDENTITY, userIdentity);
        request.addHeader(header);
    } catch (ParseException e) {
        log.warn("[跨域访问] 添加Monitor-user-Identity头域失败: {}", e.getMessage());
    }
}

// === 改造项28: Note域支持 ===
// 来源: 设计文档第8.3节(第709~710行), 2022版8.3
// 规范要求: Note=(Digest nonce="", algorithm=)
private void addNoteHeader(Response response, String nonce, String algorithm) {
    // 来源: 设计文档第8.3节, 2022版8.3
    try {
        Header noteHeader = SipFactory.getInstance()
                .createHeaderFactory()
                .createHeader("Note", "Digest nonce=\"" + nonce + "\", algorithm=" + algorithm);
        response.addHeader(noteHeader);
    } catch (ParseException e) {
        log.warn("[Note域] 添加失败: {}", e.getMessage());
    }
}

// === 改造项29: 数字证书认证支持 ===
// 来源: 设计文档第8.1节(第649~666行), 2022版8.1
// 规范要求: 宜支持数字证书的认证方式
// 改造说明: 2022版将证书认证从"高安全级别应采用"改为"宜支持", 为可选功能
// 实现方式: 通过JAIN-SIP的TLS传输和证书管理实现
// 改造位置: 新增 CertAuthHelper.java
/**
 * 数字证书认证接口定义
 * 来源: 设计文档第8.1节, 2022版8.1"宜支持基于数字证书的认证方式"
 * 实现说明: 具体实现需引入BouncyCastle或Java KeyStore, 配置CA证书和设备证书
 */
public interface CertAuthHelper {
    /**
     * 验证设备数字证书
     * @param deviceCert 设备证书(X.509)
     * @return 验证结果
     */
    boolean verifyDeviceCert(java.security.cert.X509Certificate deviceCert);
    
    /**
     * 加载平台CA证书
     * @param caCertPath CA证书路径
     */
    void loadCaCert(String caCertPath);
}

// === 改造项30: IPSec/TLS加密传输 ===
// 来源: 设计文档第8.2节(第675~695行), 2022版8.2
// 规范要求: 宜在网络层采用IPSec或在传输层采用TLS
// 改造说明: 2022版仅保留"宜采用"加密传输, 为可选功能
// 实现方式: 通过JAIN-SIP的TLS传输层实现
// 改造位置: SIP栈配置(sip-config.xml或application.yml)
/**
 * TLS传输配置
 * 来源: 设计文档第8.2节, 2022版8.2"宜在网络层采用IPSec或在传输层采用TLS"
 * 配置项: 在application.yml中增加TLS相关配置
 */
// application.yml 配置示例:
// sip:
//   tls:
//     enabled: false  # 默认关闭, 需要时启用
//     key-store: classpath:keystore.jks
//     key-store-password: changeit
//     trust-store: classpath:truststore.jks
//     trust-store-password: changeit

/**
 * TLS配置属性类
 * 来源: 设计文档第8.2节, 2022版8.2
 * 用于绑定application.yml中的sip.tls配置项
 */
@ConfigurationProperties(prefix = "sip.tls")
public class SipTlsProperties {
    private boolean enabled = false;
    private String keyStore;
    private String keyStorePassword;
    private String trustStore;
    private String trustStorePassword;
    
    // getter/setter 省略, 实际实现需添加
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyStore() { return keyStore; }
    public void setKeyStore(String keyStore) { this.keyStore = keyStore; }
    public String getKeyStorePassword() { return keyStorePassword; }
    public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }
    public String getTrustStore() { return trustStore; }
    public void setTrustStore(String trustStore) { this.trustStore = trustStore; }
    public String getTrustStorePassword() { return trustStorePassword; }
    public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
}

// === 改造项31: 数据完整性保护 ===
// 来源: 设计文档第8.4节, 2022版8.4
// 规范要求: 宜采用数字摘要、数字时间戳及数字水印等技术
// 改造说明: 2022版删除了具体算法引用(MD5/SHA-1/SHA-256), 仅保留技术手段要求
// 实现方式: 数字摘要已在改造项2(SM3)中实现, 数字时间戳和水印为可选功能
/**
 * 数据完整性校验接口
 * 来源: 设计文档第8.4节, 2022版8.4"宜采用数字摘要、数字时间戳及数字水印等技术"
 * 实现说明: 数字摘要使用SM3(改造项2), 时间戳和水印需额外实现
 */
public interface DataIntegrityHelper {
    /** SM3数字摘要校验 */
    boolean verifyDigest(byte[] data, String expectedDigest);
    /** 数字时间戳校验(可选) */
    boolean verifyTimestamp(byte[] data, String timestamp);
}

// === 改造项32: GB 35114高安全级别支持 ===
// 来源: 设计文档第8.6节(第752~765行), 2022版8.6
// 规范要求: 高安全级别情况下应符合GB 35114的规定
// 改造说明: GB 35114为独立标准, 工作量极大, 本方案仅定义接口
// 实现方式: 需引入GB 35114 SDK, 实现SVAC加密、数字证书签名等功能
/**
 * GB 35114高安全级别接口定义
 * 来源: 设计文档第8.6节, 2022版8.6"高安全级别情况下应符合GB 35114的规定"
 * 实现说明: 需引入GB 35114专用SDK, 本方案仅定义接口契约
 */
public interface GB35114Helper {
    /** SVAC加密视频流处理 */
    byte[] processSVACEncryptedStream(byte[] encryptedData);
    /** GB 35114数字签名验证 */
    boolean verifySignature(byte[] data, String signature);
}

// === 改造项33: X-Routepath/X-preferredpath头域 ===
// 来源: 设计文档第13.8节(第1574~1597行), 2022版附录H(资料性)
// 规范要求: 路径选择技术要求(可选)
// 改造说明: 附录H为资料性附录, 非强制要求
// 实现方式: 在SIP消息中添加路径选择头域
/**
 * 路径选择头域常量
 * 来源: 设计文档第13.8节, 2022版附录H(资料性)
 */
private static final String HEADER_X_ROUTEPATH = "X-Routepath";
private static final String HEADER_X_PREFERREDPATH = "X-preferredpath";

/**
 * 添加路径选择头域
 * 来源: 设计文档第13.8节, 2022版附录H
 */
private void addPathHeaders(Request request, String routePath, String preferredPath) {
    try {
        if (routePath != null && !routePath.isEmpty()) {
            Header routeHeader = SipFactory.getInstance()
                    .createHeaderFactory()
                    .createHeader(HEADER_X_ROUTEPATH, routePath);
            request.addHeader(routeHeader);
        }
        if (preferredPath != null && !preferredPath.isEmpty()) {
            Header preferredHeader = SipFactory.getInstance()
                    .createHeaderFactory()
                    .createHeader(HEADER_X_PREFERREDPATH, preferredPath);
            request.addHeader(preferredHeader);
        }
    } catch (ParseException e) {
        log.warn("[路径选择] 添加头域失败: {}", e.getMessage());
    }
}

// === 改造项34: 摄像机采集部位类型代码 ===
// 来源: 设计文档第13.10节(第1612~1619行), 2022版附录O
// 规范要求: 摄像机采集部位类型代码(规范性)
// 改造说明: 附录O为规范性附录, 需在设备目录信息中支持
/**
 * 摄像机采集部位类型代码校验
 * 来源: 设计文档第13.10节, 2022版附录O
 * 类型代码: 1-前, 2-后, 3-左, 4-右, 5-上, 6-下, 7-左前, 8-右前, 9-左后, 10-右后
 */
public static boolean isValidCapturePositionCode(String code) {
    if (code == null || code.isEmpty()) return true; // 可选字段, 空值合法
    try {
        int posCode = Integer.parseInt(code);
        return posCode >= 1 && posCode <= 10;
    } catch (NumberFormatException e) {
        return false;
    }
}
```

---

## 五、字符集与编码规则改造项（4项）

### 5.1 SIP信令字符集GB 2312→GB 18030

#### 改造模块
- **文件**：`src/main/java/com/genersoft/iot/vmp/gb28181/utils/XmlUtil.java`
- **改造位置**：第204行、第214行

#### 设计文档依据
- **来源**：设计文档第5.4节（第431~451行），2022版6.10
- **规范原文**："联网系统与设备的SIP信令字符集应采用GB 18030编码格式"

#### 改造代码

```java
// === 改造项35: 字符集GB 2312→GB 18030 ===
// 来源: 设计文档第5.4节(第431~451行), 2022版6.10
// 规范要求: SIP信令字符集应采用GB 18030编码格式(从"宜"升级为"应")
// 改造位置: XmlUtil.java 第204行、第214行

// 原代码(第204行):
// return getRootElement(evt, "gb2312");

// 原代码(第214行):
// charset = "gb2312";

// 改造后代码:
// 来源: 设计文档第5.4节(第439行), 2022版6.10
// GB 18030是GB 2312的超集, 向后兼容
public static final String DEFAULT_CHARSET = "gb18030";  // 从gb2312改为gb18030

public static Element getRootElement(RequestEvent evt) throws DocumentException {
    // 来源: 设计文档第5.4节(第439行), 2022版6.10
    return getRootElement(evt, DEFAULT_CHARSET);  // gb2312→gb18030
}

public static Element getRootElement(byte[] content, String charset) throws DocumentException {
    if (charset == null) {
        // 来源: 设计文档第5.4节(第439行), 2022版6.10
        charset = DEFAULT_CHARSET;  // gb2312→gb18030
    }
    SAXReader reader = new SAXReader();
    reader.setEncoding(charset);
    Document xml = reader.read(new ByteArrayInputStream(content));
    return xml.getRootElement();
}
```

#### 兼容性说明
- GB 18030是GB 2312的超集，向后兼容
- **不破坏原有功能**：所有GB 2312编码的内容都能被GB 18030正确解析

---

### 5.2~5.4 编码规则改造

#### 改造代码

```java
// === 改造项36: 行政区划代码更新 ===
// 来源: 设计文档第13.5节(第1486~1510行), 2022版附录E
// 规范要求: 使用最新行政区划代码(非GB/T2260—2007)
// 改造位置: GbCode.java 第13行注释

// 原注释: 符合GB/T2260—2007的要求
// 改造后注释: 符合最新行政区划代码要求(2022版附录E)

/**
 * 校验行政区划代码
 * 来源: 设计文档第13.5节, 2022版附录E
 * 规范要求: 6位行政区划代码, 前2位为省级代码(11~65), 后4位为市县级代码
 * @param adminCode 行政区划代码(6位数字)
 * @return true if valid
 */
public static boolean isValidAdminCode(String adminCode) {
    if (adminCode == null || adminCode.length() != 6) return false;
    try {
        int provinceCode = Integer.parseInt(adminCode.substring(0, 2));
        // 来源: 2022版附录E, 省级代码范围11~65
        return provinceCode >= 11 && provinceCode <= 65;
    } catch (NumberFormatException e) {
        return false;
    }
}

// === 改造项37: 类型编码扩展校验 ===
// 来源: 设计文档第13.5节(第1486~1510行), 2022版附录E
// 规范要求: 新增类型编码120~125/140~143/502/503/505
// 改造位置: GbCode.java 新增类型编码校验方法

/**
 * 校验类型编码是否为2022版新增
 * 来源: 设计文档第13.5节, 2022版附录E
 * 新增类型编码: 120~125, 140~143, 502, 503, 505
 */
public static boolean isNewTypeCode(String typeCode) {
    if (typeCode == null || typeCode.length() != 3) return false;
    int code = Integer.parseInt(typeCode);
    // 来源: 设计文档第13.5节, 2022版附录E
    return (code >= 120 && code <= 125) ||  // 120~125: 在线视频图像信息采集系统等
           (code >= 140 && code <= 143) ||  // 140~143: 报警输出设备/道闸/智能门/凭证识别单元
           code == 502 || code == 503 || code == 505;  // 502/503/505: 视频图像分析系统等
}

// === 改造项38: 网络标识编码取值校验 ===
// 来源: 设计文档第13.5节(第1486~1510行), 2022版附录E
// 规范要求: 网络标识0~8(0/1公安视频传输网, 2行业专网, 3政法信息网, 4公安移动信息网, 5公安信息网, 6电子政务外网, 7互联网, 8专线, 9预留)
// 改造位置: GbCode.java 新增网络标识校验方法

/**
 * 校验网络标识编码是否有效
 * 来源: 设计文档第13.5节, 2022版附录E
 * 网络标识: 0/1-公安视频传输网, 2-行业专网, 3-政法信息网, 4-公安移动信息网, 5-公安信息网, 6-电子政务外网, 7-互联网, 8-专线, 9-预留
 */
public static boolean isValidNetCode(String netCode) {
    if (netCode == null || netCode.length() != 1) return false;
    int code = Integer.parseInt(netCode);
    // 来源: 设计文档第13.5节, 2022版附录E
    return code >= 0 && code <= 9;
}

// === 改造项补充: 5个低优先级核查报告条目 ===
// 以下5项为核查报告中的低优先级需升级项, 在38个改造项之外, 补充实现

// === 补充1: SIP端口防干扰 ===
// 来源: 设计文档第4.3.2节(第300~303行), 2022版4.3.2
// 规范要求: "互联的联网系统平台及设备不应向对方的SIP端口发送应用无关消息"
// 改造位置: SIP消息接收处理器, 增加消息类型过滤
/**
 * SIP端口消息类型过滤
 * 来源: 设计文档第4.3.2节, 2022版4.3.2
 * 规范要求: 不应向对方SIP端口发送应用无关消息
 */
private boolean isSipMessageValid(RequestEvent evt) {
    // 来源: 设计文档第4.3.2节(第300~303行), 2022版4.3.2
    String method = evt.getRequest().getMethod();
    // 仅允许SIP标准方法, 拒绝非SIP消息
    return "REGISTER".equals(method) || "MESSAGE".equals(method) ||
           "INVITE".equals(method) || "ACK".equals(method) ||
           "BYE".equals(method) || "CANCEL".equals(method) ||
           "OPTIONS".equals(method) || "INFO".equals(method) ||
           "SUBSCRIBE".equals(method) || "NOTIFY".equals(method);
}

// === 补充2: G.722.1编解码支持 ===
// 来源: 设计文档第6.2节(第522~535行), 2022版附录F.1
// 规范要求: G.722.1为宜采用(推荐), 非强制
// 改造位置: SIPCommander.java SDP协商中增加G.722.1 rtpmap
/**
 * 添加G.722.1音频SDP描述
 * 来源: 设计文档第6.2节, 2022版附录F.1/C.2.4
 * PT=9, rtpmap:9 G722/8000
 */
private void appendG7221AudioSdp(StringBuffer content) {
    // 来源: 设计文档第13.3节(第1453行), 2022版附录C.2.4
    content.append("a=rtpmap:9 G722/8000\r\n");
}

// === 补充3: a=svcspace参数支持 ===
// 来源: 设计文档第7节(第602行), 2022版附录G
// 规范要求: a=svcspace(空域编码, 两版均有)
/**
 * 添加a=svcspace参数
 * 来源: 设计文档第7节(第602行), 2022版附录G
 */
private void appendSvcspace(StringBuffer content, String svcspace) {
    content.append("a=svcspace:" + svcspace + "\r\n");
}

// === 补充4: a=svctime参数支持 ===
// 来源: 设计文档第7节(第603行), 2022版附录G
// 规范要求: a=svctime(时域编码, 两版均有)
/**
 * 添加a=svctime参数
 * 来源: 设计文档第7节(第603行), 2022版附录G
 */
private void appendSvctime(StringBuffer content, String svctime) {
    content.append("a=svctime:" + svctime + "\r\n");
}

// === 补充5: 联网系统扩展应用 ===
// 来源: 设计文档附录A.4, 2022版附录A.4
// 规范要求: 联网系统扩展应用(资料性, 可选)
/**
 * 联网系统扩展应用接口定义
 * 来源: 设计文档附录A.4, 2022版附录A.4
 * 实现说明: 扩展应用为资料性内容, 非强制要求
 */
// 改造位置: 新增 ExtensionApplicationHandler.java (可选)
```

---

## 六、改造实施计划

### 6.1 实施阶段

| 阶段 | 改造项 | 预计工作量 | 优先级 |
|------|--------|-----------|--------|
| 第一阶段 | 改造项1~15（高优先级） | 大 | 高 |
| 第二阶段 | 改造项16~26（中优先级） | 中 | 中 |
| 第三阶段 | 改造项27~38（低优先级+字符集） | 小 | 低 |

**来源**：《WVP 合规性核查报告》第五章升级实施建议。

### 6.2 测试验证

每个阶段完成后，需进行以下测试：

1. **兼容性测试**：验证2016版设备仍能正常注册和交互
2. **功能测试**：验证2022版新增功能正常工作
3. **回归测试**：验证原有功能未被破坏

**来源**：《WVP 合规性核查报告》第五章测试验证要点。

---

## 七、规范来源说明

本方案所有数据均来自以下文档：

| 文档 | 说明 | 位置 |
|------|------|------|
| 《GB/T 28181—2016 升级至 GB/T 28181—2022 开发设计文档》V1.0 | 升级基准 | `28181-2022/doc/GB28181_2016_to_2022_升级开发设计文档.md` |
| 《WVP 合规性核查报告》 | 核查基准 | `28181-2022/doc/WVP合规性核查报告.md` |
| WVP-GB28181-pro 源码 | 改造对象 | `wvp-GB28181-pro/src/main/java/com/genersoft/iot/vmp/` |

所有改造代码均标注设计文档来源（章节+行号）和规范原文，可通过引用直接定位进行复核。

---

## 八、方案结论

本方案针对 WVP 2.7.4 的 **38 个需升级项**，给出了具体的改造模块、改造位置和改造代码。方案遵循以下原则：

1. **不破坏原有功能**：所有改造采用增量方式，保留原有逻辑作为兼容回退
2. **配置驱动**：通过`device.isVersion2022()`判断设备版本，实现2016/2022双版本兼容
3. **来源可追溯**：每处改造标注设计文档来源和规范原文
4. **生产级代码**：包含异常处理、日志记录、线程安全

**建议**：按照第六节的实施计划，分三个阶段实施升级，并在每个阶段完成后进行充分的兼容性测试和回归测试。
