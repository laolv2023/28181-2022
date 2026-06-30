# 第四轮代码审计报告（并发安全与资源管理）

> **审计轮次**：第 4 轮
> **审计日期**：2026-06-30
> **审计目标**：并发安全、资源泄漏、错误处理路径、日志安全

---

## 一、第四轮审计概况

第四轮聚焦于：
1. 并发安全（静态可变状态、线程安全集合、锁）
2. 资源泄漏（Socket、InputStream、SIP Session）
3. 错误处理路径完整性
4. 日志中敏感信息泄露
5. XML元素名合规性

### 1.1 新增问题统计

| 级别 | 数量 |
|------|------|
| P0-致命 | 0 |
| P1-严重 | 1 |
| P2-一般 | 3 |
| P3-建议 | 1 |
| **合计** | **5** |

---

## 二、P1-严重问题（新增1个）

### R4-P1-01: SIPCommander2022Supplement FileuRL元素名大小写错误

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
- **行号**：约180行
- **审计方法**：#90 XML元素名大小写合规性
- **问题描述**：
  ```java
  xml.append("<FileuRL>").append(escapeXml(fileUrl)).append("</FileuRL>\r\n");
  ```
  XML元素名为 `<FileuRL>`（u小写）。根据GB/T 28181-2022附录A.2.3.1.13，文件URL的XML元素名应为 `<FileURL>`（U、R、L均大写）。
- **影响**：设备按规范解析 `<FileURL>` 但平台发送 `<FileuRL>`，设备无法找到文件URL元素，升级命令中的下载地址丢失，设备升级失败。
- **修复建议**：改为 `<FileURL>`。

---

## 三、P2-一般问题（新增3个）

### R4-P2-01: SipMessageFilter catch(Throwable)过于宽泛

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java`
- **行号**：147, 159, 170
- **审计方法**：#6 异常吞没
- **问题描述**：`catch (Throwable t)` 捕获了包括 `OutOfMemoryError` 在内的所有错误。对于OOM等严重错误，应该让JVM处理而非静默吞没。
- **修复建议**：改为 `catch (Exception e)`，对Error单独处理。

### R4-P2-02: SipMessageFilter X-GB-ver头域解析异常仅DEBUG级别

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java`
- **行号**：170-172
- **审计方法**：#97 日志规范
- **问题描述**：X-GB-ver头域解析异常仅以DEBUG级别记录。生产环境通常禁用DEBUG日志，此异常被静默吞没。X-GB-ver是2022版协议版本协商的关键头域，解析失败可能导致版本判断错误。
- **修复建议**：提升为WARN级别。

### R4-P2-03: SIPCommander2022Supplement log.info打印完整XML

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander2022Supplement.java`
- **行号**：约116行
- **审计方法**：#97 日志规范
- **问题描述**：`log.info` 打印整个XML消息体，生产环境会产生大量日志。
- **修复建议**：改为DEBUG级别。

---

## 四、P3-建议问题（新增1个）

| 编号 | 文件 | 审计方法 | 问题描述 |
|------|------|---------|---------|
| R4-P3-01 | SipTlsProperties.java | #104 | @Configuration和@ConfigurationProperties同时使用 |

---

## 五、累计问题统计（4轮）

| 级别 | 第1轮 | 第2轮 | 第3轮 | 第4轮 | 累计 |
|------|-------|-------|-------|-------|------|
| P0 | 8 | 0 | 0 | 0 | 8 |
| P1 | 29 | 3 | 2 | 1 | 35 |
| P2 | 49 | 5 | 4 | 3 | 61 |
| P3 | 24 | 2 | 2 | 1 | 29 |
| **合计** | **110** | **10** | **8** | **5** | **133** |
