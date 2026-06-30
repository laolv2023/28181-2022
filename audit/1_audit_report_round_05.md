# 第五轮代码审计报告（最终查漏）

> **审计轮次**：第 5 轮
> **审计日期**：2026-06-30
> **审计目标**：异常处理模式扫描、日志级别验证、最终查漏

---

## 一、第五轮审计概况

第五轮聚焦于：
1. 异常处理模式全量扫描（catch Exception/catch Throwable）
2. 日志级别合理性验证
3. System.out/printStackTrace检查
4. 硬编码凭据检查
5. v-html/XSS检查

### 1.1 新增问题统计

| 级别 | 数量 |
|------|------|
| P0-致命 | 0 |
| P1-严重 | 0 |
| P2-一般 | 2 |
| P3-建议 | 0 |
| **合计** | **2** |

---

## 二、P2-一般问题（新增2个）

### R5-P2-01: SipMessageFilter读取消息体异常仅DEBUG级别

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java`
- **行号**：159-161
- **审计方法**：#6 异常吞没、#97 日志规范
- **问题描述**：
  ```java
  } catch (Throwable t) {
      logger.debug("[SIP过滤] 读取消息体异常: {}", t.getMessage());
  }
  ```
  读取消息体时捕获Throwable（包括OOM），仅以DEBUG级别记录。生产环境消息体读取异常无法被监控发现。
- **修复建议**：提升为WARN级别，对OOM单独处理。

### R5-P2-02: SipMessageFilter X-GB-ver头域解析异常仅DEBUG级别

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java`
- **行号**：170-172
- **审计方法**：#6 异常吞没、#97 日志规范、#85 协议版本协商
- **问题描述**：X-GB-ver头域解析异常仅以DEBUG级别记录。协议版本协商异常无法被监控发现。
- **修复建议**：提升为WARN级别。

---

## 三、全量扫描结果

### 3.1 System.out/printStackTrace扫描

- 扫描范围：news/、backend/、ui/、patches/、tests/、scripts/
- 结果：自研代码中无System.out/printStackTrace ✓

### 3.2 硬编码凭据扫描

- 扫描范围：news/、backend/
- 结果：无硬编码凭据 ✓

### 3.3 v-html/XSS扫描

- 扫描范围：ui/
- 结果：无v-html/innerHTML/eval ✓

### 3.4 TODO/FIXME扫描

- 扫描范围：news/、backend/、patches/
- 结果：所有TODO/待异步均已在第1轮记录为P1问题 ✓

### 3.5 parseInt/parseLong扫描

- 扫描范围：news/
- 结果：GbCode2022.java的parseInt均有正则预校验或try-catch保护 ✓

### 3.6 资源关闭扫描

- 扫描范围：news/
- 结果：TcpReconnectHelper的Socket正确关闭 ✓

### 3.7 Controller输入校验扫描

- 扫描范围：backend/
- 结果：已在第1轮记录参数校验缺失问题 ✓

### 3.8 前端定时器/事件监听扫描

- 扫描范围：ui/
- 结果：QueryResultDialog和StorageCardDialog的定时器在beforeDestroy/dialog close中清理 ✓

---

## 四、累计问题统计（5轮）

| 级别 | 第1轮 | 第2轮 | 第3轮 | 第4轮 | 第5轮 | 累计 |
|------|-------|-------|-------|-------|-------|------|
| P0 | 8 | 0 | 0 | 0 | 0 | 8 |
| P1 | 29 | 3 | 2 | 1 | 0 | 35 |
| P2 | 49 | 5 | 4 | 3 | 2 | 63 |
| P3 | 24 | 2 | 2 | 1 | 0 | 29 |
| **合计** | **110** | **10** | **8** | **5** | **2** | **135** |

---

## 五、收敛趋势分析

| 轮次 | 新增问题数 | 趋势 |
|------|-----------|------|
| 第1轮 | 110 | 基线 |
| 第2轮 | 10 | -91% |
| 第3轮 | 8 | -20% |
| 第4轮 | 5 | -38% |
| 第5轮 | 2 | -60% |

新增问题数持续下降，第5轮仅2个P2问题。从第6轮开始进入收敛确认阶段。
