# 第三轮代码审计报告（测试质量与合规性）

> **审计轮次**：第 3 轮
> **审计日期**：2026-06-30
> **审计目标**：测试断言有效性、SIPp场景合规性、枚举值规范对齐

---

## 一、第三轮审计概况

第三轮聚焦于：
1. Java单元测试的断言有效性
2. SIPp测试场景的GB28181合规性
3. 枚举值与GB28181-2022规范的对齐
4. 测试是否固化了代码错误

### 1.1 新增问题统计

| 级别 | 数量 |
|------|------|
| P0-致命 | 0 |
| P1-严重 | 2 |
| P2-一般 | 4 |
| P3-建议 | 2 |
| **合计** | **8** |

---

## 二、P1-严重问题（新增2个）

### R3-P1-01: SdpFieldHelperTest固化了"DoWnload"大写W的错误

- **文件**：`tests/java/unit/SdpFieldHelperTest.java`
- **行号**：22, 42
- **审计方法**：#107 测试断言有效性、#90 XML元素名大小写合规性
- **问题描述**：
  ```java
  assertTrue(sdp.contains("s=DoWnload"), "s字段应包含DoWnload(大写W)");  // 第22行
  assertTrue(sdp.contains("a=doWnloadspeed:"), "应包含a=doWnloadspeed(大写W)");  // 第42行
  ```
  测试断言固化了 "DoWnload"（大写W）和 "doWnloadspeed"（大写W）。根据GB/T 28181-2022规范，SDP s=字段应为 "Download"（标准大小写），a=downloadspeed应为全小写。大写W是设计文档中的误解，测试不仅没有发现错误，反而通过断言"确认"了这个错误。
- **影响**：设备按规范解析 "Download" 但平台发送 "DoWnload"，SDP协商失败，历史媒体回放不可用。
- **修复建议**：修正为 "Download" 和 "downloadspeed"，同步修正SdpFieldHelper.java和patches/03-SIPCommander.patch。

### R3-P1-02: SM3DigestHelperTest A1.1-06会因SM3_AVAILABLE bug而失败

- **文件**：`tests/java/unit/SM3DigestHelperTest.java`
- **行号**：251
- **审计方法**：#106 单元测试覆盖
- **问题描述**：
  ```java
  assertEquals(providerRegistered, available, "SM3可用性应与Bouncy Castle注册状态一致");
  ```
  由于SM3DigestHelper.java第74行 `SM3_AVAILABLE = available;` 在检测逻辑之前执行，`SM3_AVAILABLE` 恒为false。当Bouncy Castle已注册（providerRegistered=true）时，`available`（即 `isSm3Available()` 返回值）仍为false，断言失败。
- **影响**：测试A1.1-06必然失败，CI/CD流水线被阻断。
- **修复建议**：先修复P0-08（SM3_AVAILABLE赋值时机），再运行测试。

---

## 三、P2-一般问题（新增4个）

### R3-P2-01: DeviceControlTypeTest固化了可能错误的枚举值

- **文件**：`tests/java/unit/DeviceControlTypeTest.java`
- **行号**：21, 33, 50, 64
- **审计方法**：#107 测试断言有效性
- **问题描述**：测试断言固化了 "PTzPrecisectrl"（小写z）、"FormatsDcard"（大写S）、"Deviceupgrade"（小写u）等枚举值。这些大小写混合的命名需要与GB/T 28181-2022规范原文逐一核对。如果规范使用不同的大小写，测试固化了错误。
- **修复建议**：逐项核对规范原文，确认每个枚举值的大小写。

### R3-P2-02: SIPp场景使用硬编码IP和端口

- **文件**：多个SIPp场景文件
- **审计方法**：#110 测试隔离性
- **问题描述**：SIPp场景使用硬编码的 `127.0.0.1:5060` 等地址，无法适应不同测试环境。
- **修复建议**：使用SIPp的 `[remote_ip]` 和 `[remote_port]` 变量。

### R3-P2-03: SIPp场景SN全部硬编码为1

- **文件**：多个SIPp场景文件
- **审计方法**：#108 边界测试
- **问题描述**：所有测试用例使用 `<SN>1</SN>`，无法验证SN递增逻辑和SN冲突处理。
- **修复建议**：使用递增SN或随机SN。

### R3-P2-04: 测试脚本run_sipp_tests.sh丢弃stderr输出

- **文件**：`tests/scripts/run_sipp_tests.sh`
- **行号**：22
- **审计方法**：#112 测试脚本健壮性
- **问题描述**：`2>/dev/null` 丢弃了sipp的错误输出，调试困难。
- **修复建议**：重定向到日志文件而非/dev/null。

---

## 四、P3-建议问题（新增2个）

| 编号 | 文件 | 审计方法 | 问题描述 |
|------|------|---------|---------|
| R3-P3-01 | SdpFieldHelperTest.java | #107 | "空StringBuilder安全"测试使用assertTrue(true) |
| R3-P3-02 | 多个SIPp场景 | #104 | ResponseTimeRepartition配置冗余 |

---

## 五、累计问题统计（3轮）

| 级别 | 第1轮 | 第2轮 | 第3轮 | 累计 |
|------|-------|-------|-------|------|
| P0 | 8 | 0 | 0 | 8 |
| P1 | 29 | 3 | 2 | 34 |
| P2 | 49 | 5 | 4 | 58 |
| P3 | 24 | 2 | 2 | 28 |
| **合计** | **110** | **10** | **8** | **128** |
