# 第四轮代码审计报告

> **审计轮次**：第 4 轮
> **审计日期**：2026-06-30
> **审计目标**：并发安全、资源泄漏、错误处理路径、日志安全、配置默认值
> **审计方法**：130种方法深度应用 + 模式匹配扫描

---

## 一、第四轮审计概况

第四轮审计聚焦于：
1. 并发安全（静态可变状态、线程安全集合、锁）
2. 资源泄漏（Socket、InputStream、SIP Session）
3. 错误处理路径完整性（异常掩盖、响应失败）
4. 日志中敏感信息泄露
5. 配置默认值安全性
6. XML元素名合规性

### 1.1 新增问题统计

| 级别 | 数量 |
|------|------|
| P0-致命 | 0 |
| P1-严重 | 2 |
| P2-一般 | 6 |
| P3-建议 | 2 |
| **合计** | **10** |

---

## 二、P1-严重问题（新增2个）

### R4-P1-01: SIPCommander2022Supplement的FileuRL元素名大小写错误

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java` 第180行
- **审计方法**：#36 合规性校验、#87 SDP/XML格式
- **问题描述**：
  ```java
  xml.append("<FileuRL>").append(escapeXml(fileUrl)).append("</FileuRL>\r\n");
  ```
  XML元素名为 `<FileuRL>`，其中'u'为小写。根据GB/T 28181-2022附录A.2.3.1.13设备升级命令，文件URL的XML元素名应为 `<FileURL>`（U、R、L均大写）。当前写法 `FileuRL` 是大小写混合，不符合规范。
- **影响**：设备按照规范解析 `<FileURL>` 元素，但平台发送的是 `<FileuRL>`，设备无法找到文件URL元素，升级命令中的下载地址丢失，设备升级失败。
- **修复建议**：改为 `<FileURL>`。

### R4-P1-02: respondAck的X-GB-ver头域添加在主try块内，头域失败导致响应不发送

- **文件**：`news/.../DeviceUpgradeResultNotifyMessageHandler.java` 第264-273行、`news/.../SnapshotFinishedNotifyMessageHandler.java` 第261-270行
- **审计方法**：#6 异常吞没、#15 超时/响应、#19 功能完整性
- **问题描述**：
  ```java
  private void respondAck(RequestEvent evt) {
      try {
          Response response = ...createResponse(Response.OK);
          // X-GB-ver 头域添加在此 try 块内
          Header gbVerHeader = SipFactory.getInstance()...createHeader("X-GB-ver", "2.0");
          response.addHeader(gbVerHeader);  // ← 如果此处抛 ParseException
          serverTransaction.sendResponse(response);  // ← 则此行不执行，响应不发送
      } catch (...) {
          logger.error(...);  // 只记录日志，不重试
      }
  }
  ```
  X-GB-ver头域的创建（createHeader）可能抛出ParseException。如果抛出，整个respondAck失败，200 OK响应不发送。设备收不到响应会超时重发通知。
- **影响**：X-GB-ver头域添加失败时，设备收不到ACK，导致通知重发，增加网络负载和设备功耗。生产环境中如果JAIN SIP实现有偶发性ParseException，会导致大量通知重发。
- **修复建议**：将X-GB-ver头域添加放在独立的try-catch中，头域添加失败时仍发送响应（不带X-GB-ver）。

---

## 三、P2-一般问题（新增6个）

### R4-P2-01: SipMessageFilter先读取完整body再检查大小

- **文件**：`news/.../SipMessageFilter.java` 第154-155行
- **审计方法**：#5 资源泄漏、#30 内存耗尽防护
- **问题描述**：
  ```java
  byte[] rawContent = request.getRawContent();  // 先读取完整body到内存
  if (rawContent != null && rawContent.length > MAX_CONTENT_LENGTH) {  // 再检查大小
      return false;
  }
  ```
  `getRawContent()` 在检查大小之前就将完整消息体读入内存。对于恶意构造的超大消息（如100MB），即使MAX_CONTENT_LENGTH=1MB，整个100MB也会先读入内存，可能触发OOM。
- **影响**：攻击者发送超大SIP消息可导致WVP进程OOM崩溃。
- **修复建议**：先通过Content-Length头判断大小，超限直接拒绝，不读取body。

### R4-P2-02: SipCharsetHelper.encode使用String而非Charset对象

- **文件**：`news/.../SipCharsetHelper.java` 第120、144行
- **审计方法**：#61 性能、#22 返回值一致性
- **问题描述**：
  ```java
  // 第120行
  public static byte[] encode(String text) throws UnsupportedEncodingException {
      return text.getBytes(DEFAULT_CHARSET);  // 使用String常量"gb18030"
  }
  ```
  `DEFAULT_CHARSET`是String常量"gb18030"，而`DEFAULT_CHARSET_OBJ`是已初始化的Charset对象。使用String版本的getBytes需要每次查找Charset，且声明throws UnsupportedEncodingException（实际上gb18030在Java中始终可用，异常是死代码）。应使用`text.getBytes(DEFAULT_CHARSET_OBJ)`。
- **影响**：性能开销（每次Charset查找），且方法签名误导（声明抛出实际不会抛出的异常）。
- **修复建议**：使用Charset对象版本。

### R4-P2-03: ApiDeviceControlController异常消息泄露内部信息

- **文件**：`backend/.../ApiDeviceControlController.java` 第91、131、159、224、281行
- **审计方法**：#52 日志脱敏、#38 信息泄露
- **问题描述**：
  ```java
  return WVPResult.fail(500, "PTZ精准控制命令下发失败: " + e.getMessage());
  ```
  所有接口的异常处理都将`e.getMessage()`返回给客户端。异常消息可能包含内部类名、SQL语句、文件路径等敏感信息。
- **影响**：信息泄露，攻击者可利用异常消息探测系统内部结构。
- **修复建议**：返回通用错误消息，详细信息只记录在服务端日志。

### R4-P2-04: SipTlsProperties未校验端口范围和密钥库类型

- **文件**：`news/.../conf/SipTlsProperties.java` 第284行isValid方法
- **审计方法**：#26 输入校验、#27 参数范围校验
- **问题描述**：isValid方法未校验：
  1. serverPort是否在1-65535范围
  2. keyStoreType是否为JKS/PKCS12/PKCS11之一
  3. keyStorePath/trustStorePath是否为空
- **影响**：配置错误可能导致TLS初始化失败，但isValid返回true，问题在运行时才暴露。
- **修复建议**：补充端口范围和密钥库类型校验。

### R4-P2-05: GbCode2022采集位置码CAPTURE_POSITION_MAX=10但只取1位

- **文件**：`news/.../GbCode2022.java` 第285行、常量定义
- **审计方法**：#36 合规性校验、#84 国标编码校验
- **问题描述**：
  ```java
  String capturePositionCode = gbCode.substring(13, 14);  // 只取1位
  if (!isValidCapturePositionCode(capturePositionCode)) {  // 校验1-10
  ```
  substring(13,14)只取1个字符，但CAPTURE_POSITION_MAX=10意味着合法值可以是10（两位数）。如果编码中第14-15位是"10"，substring(13,14)只取到"1"，校验通过但语义错误。
- **影响**：采集位置码为10的设备编码校验逻辑错误。
- **修复建议**：确认规范中采集位置码是1位(1-9)还是2位(1-10)，修正常量或提取逻辑。

### R4-P2-06: 多个Handler的cmdType字段未声明为static final

- **文件**：`SnapshotConfigMessageHandler.java` 第88行、`CruiseTrackQueryMessageHandler.java` 第116行、`HomePositionQueryMessageHandler.java` 第90行、`PtzPreciseStatusQueryMessageHandler.java` 第83行、`StorageCardStatusQueryMessageHandler.java` 第87行
- **审计方法**：#96 魔法数字/常量、#104 代码质量
- **问题描述**：
  ```java
  private final String cmdType = "SnapConfig";  // 应为 private static final
  ```
  cmdType是固定值，应为static final。当前每个实例都创建一个String对象，浪费内存。
- **影响**：轻微内存浪费，代码不规范。
- **修复建议**：改为 `private static final String cmdType`。

---

## 四、P3-建议问题（新增2个）

### R4-P3-01: SipTlsProperties的@Configuration和@ConfigurationProperties同时使用

- **文件**：`news/.../conf/SipTlsProperties.java`
- **审计方法**：#104 代码质量
- **问题描述**：Spring Boot 2.2+推荐使用@ConfigurationPropertiesScan或@EnableConfigurationProperties，而非在Properties类上加@Configuration。

### R4-P3-02: 多个Handler的handForPlatform/handForDevice传参不一致

- **文件**：`DeviceUpgradeResultNotifyMessageHandler.java`、`SnapshotFinishedNotifyMessageHandler.java`
- **审计方法**：#105 死代码
- **问题描述**：handForPlatform传parentPlatform，handForDevice传null。但handleMessage内部没有使用parentPlatform参数，这是死代码。

---

## 五、第四轮自我检查与反思

### 5.1 并发安全验证

| 检查项 | 结果 |
|--------|------|
| 静态可变状态 | SIPCommander2022Supplement.snCounter（synchronized保护，但int可能溢出） |
| 线程安全集合 | ApiConfigController使用ConcurrentHashMap ✓ |
| MessageDigest线程安全 | DigestServerAuthenticationHelper的sm3MessageDigest非线程安全（P1-07） |
| SimpleDateFormat | 未使用（无线程安全问题） |

### 5.2 资源泄漏验证

| 检查项 | 结果 |
|--------|------|
| Socket | TcpReconnectHelper正确关闭 ✓ |
| InputStream | SIPCommander2022Supplement的file.getInputStream()未显式关闭（P2-15） |
| SIP Session | getServerTransaction可能返回null（P2-03） |

### 5.3 累计问题统计（4轮）

| 级别 | 第1轮 | 第2轮 | 第3轮 | 第4轮 | 累计 |
|------|-------|-------|-------|-------|------|
| P0 | 5 | 0 | 0 | 0 | 5 |
| P1 | 21 | 6 | 4 | 2 | 33 |
| P2 | 47 | 9 | 8 | 6 | 70 |
| P3 | 23 | 3 | 2 | 2 | 30 |
| **合计** | **96** | **18** | **14** | **10** | **138** |

### 5.4 待第五轮深入检查

1. 最终交叉验证所有P0/P1问题
2. 检查是否有遗漏的审计方法应用
3. 验证文档对齐的完整性
4. 编写审计总结报告
