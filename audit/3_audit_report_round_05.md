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
| P2-一般 | 1 |
| P3-建议 | 0 |
| **合计** | **1** |

---

## 二、P2-一般问题（新增1个）

### R5-P2-01: SipMessageFilter X-GB-ver头域存在时用WARN级别记录

- **文件**：`news/.../utils/SipMessageFilter.java` 第168行
- **审计方法**：#97 日志规范
- **问题描述**：
  ```java
  if (gbVerHeader != null) {
      logger.warn("[SIP过滤] X-GB-ver: {}", gbVerHeader);
  }
  ```
  X-GB-ver头域存在是正常情况（2022版设备应携带此头域），不应使用WARN级别记录。WARN级别表示警告/异常情况，正常情况应使用INFO或DEBUG。
- **影响**：生产环境会产生大量WARN日志，干扰真正的告警发现。
- **修复建议**：改为DEBUG级别。

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

- 扫描范围：news/、backend/、patches/、scripts/
- 结果：无TODO/FIXME ✓

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
| P0 | 6 | 0 | 0 | 0 | 0 | 6 |
| P1 | 28 | 2 | 1 | 0 | 0 | 31 |
| P2 | 42 | 4 | 3 | 2 | 1 | 52 |
| P3 | 20 | 2 | 1 | 1 | 0 | 24 |
| **合计** | **96** | **8** | **5** | **3** | **1** | **113** |

---

## 五、收敛趋势分析

| 轮次 | 新增问题数 | 趋势 |
|------|-----------|------|
| 第1轮 | 96 | 基线 |
| 第2轮 | 8 | -92% |
| 第3轮 | 5 | -38% |
| 第4轮 | 3 | -40% |
| 第5轮 | 1 | -67% |

新增问题数持续下降，第5轮仅1个P2问题。从第6轮开始进入收敛确认阶段。
