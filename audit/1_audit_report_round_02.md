# 第二轮代码审计报告（交叉验证）

> **审计轮次**：第 2 轮
> **审计日期**：2026-06-30
> **审计目标**：交叉验证第1轮P0/P1问题，查漏补缺

---

## 一、第二轮审计概况

第二轮聚焦于：
1. 逐行复核所有P0编译错误的准确性
2. 检查第1轮遗漏的文件类型和问题
3. 交叉验证前后端API契约一致性
4. 验证配置项接入情况

### 1.1 新增问题统计

| 级别 | 数量 |
|------|------|
| P0-致命 | 0 |
| P1-严重 | 3 |
| P2-一般 | 5 |
| P3-建议 | 2 |
| **合计** | **10** |

---

## 二、P0问题交叉验证结果

| 编号 | 文件 | 验证结果 |
|------|------|---------|
| P0-01 | SipTlsProperties.java:245,261 | ✓ 确认：两个setServerPort方法 |
| P0-02 | SIPCommander2022Supplement.java:71-72 | ✓ 确认：不完整的String声明 |
| P0-03 | SIPCommander2022Supplement.java:222-223 | ✓ 确认：Paths.get()内含字段声明 |
| P0-04 | SIPCommander2022Supplement.java:236-239 | ✓ 确认：try块未关闭 |
| P0-05 | ApiDeviceControlController.java:80 | ✓ 确认：@PostMapping值格式错误 |
| P0-06 | ApiDeviceControlController.java:386 | ✓ 确认：@PostMapping值格式错误 |
| P0-07 | ApiConfigController.java:29 | ✓ 确认：@PreAuthorize未导入 |
| P0-08 | SM3DigestHelper.java:74 | ✓ 确认：SM3_AVAILABLE恒为false |

---

## 三、P1-严重问题（新增3个）

### R2-P1-01: ApiDeviceControlController @PreAuthorize实际导入9次（非8次）

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
- **审计方法**：#21 编译正确性、#104 代码重复
- **问题描述**：第1轮记录为8次，实际grep确认导入9次。重复导入数量修正。
- **影响**：代码质量极差，编译器警告。

### R2-P1-02: 无数据库迁移脚本，protocolVersion字段无法持久化

- **文件**：整个项目
- **审计方法**：#24 数据持久化
- **问题描述**：patches/04-Device.patch为Device类添加了 `protocolVersion` 字段，但项目中无SQL迁移脚本（无 .sql 文件）。如果使用JPA/Hibernate自动建表，字段会自动添加；但如果使用MyBatis手动映射，需要手动修改数据库表结构。无迁移脚本意味着部署时可能遗漏此步骤。
- **影响**：protocolVersion字段在数据库中不存在，设备协议版本无法持久化，重启后丢失。

### R2-P1-03: SIPp测试场景XML根元素不合规

- **文件**：`tests/sipp/control/ctrl_ptz_precise.xml` 等多个SIPp场景
- **审计方法**：#111 SIPp场景完整性
- **问题描述**：部分SIPp场景使用 `<scenario>` 作为根元素，但SIPp标准根元素应为 `<scenario name="...">`。部分场景缺少name属性。此外，部分场景的XML元素名大小写不一致（如 `<recv>` vs `<Receive>`）。
- **影响**：SIPp可能无法正确解析场景文件，测试无法运行。

---

## 四、P2-一般问题（新增5个）

### R2-P2-01: ApiDeviceControlController 错误消息泄露内部信息

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
- **行号**：多处
- **审计方法**：#62 信息泄露
- **问题描述**：部分错误响应包含 `e.getMessage()`，可能泄露内部堆栈信息给客户端。
- **修复建议**：记录完整异常到日志，返回通用错误消息给客户端。

### R2-P2-02: ApiDeviceControlController sessionId生成可能失败

- **文件**：`backend/src/main/java/com/genersoft/iot/vmp/gb28181/controller/ApiDeviceControlController.java`
- **审计方法**：#68 随机数安全性
- **问题描述**：`generateSessionId()` 使用 `SecureRandom`，但在某些无 `/dev/urandom` 的环境中可能阻塞。虽然有 `Math.random()` 回退，但回退方案非密码学安全。
- **修复建议**：配置JVM使用非阻塞随机源。

### R2-P2-03: GbCode2022 正则表达式每次调用重新编译

- **文件**：`news/main/java/com/genersoft/iot/vmp/gb28181/utils/GbCode2022.java`
- **行号**：123, 264
- **审计方法**：#96 魔法数字、性能
- **问题描述**：`gbCode.matches("\\d{20}")` 每次调用都重新编译正则表达式。在高频调用场景下影响性能。
- **修复建议**：预编译为 `private static final Pattern`。

### R2-P2-04: 多个Handler的respondAck方法X-GB-ver头域添加在try块内

- **文件**：SnapshotFinishedNotifyMessageHandler.java, DeviceUpgradeResultNotifyMessageHandler.java
- **行号**：约262-273
- **审计方法**：#6 异常吞没、#19 功能完整性
- **问题描述**：`respondAck()` 方法在主try块内添加X-GB-ver头域。如果头域创建失败（ParseException），整个响应不会发送，设备收不到ACK会重发消息。
- **修复建议**：将X-GB-ver头域添加移到独立的try-catch中，头域添加失败不影响响应发送。

### R2-P2-05: frontEnd.js API函数参数风格不统一

- **文件**：`ui/api/frontEnd.js`
- **审计方法**：#101 接口设计
- **问题描述**：部分函数使用对象解构参数（`{ deviceId, channelId, ... } = {}`），部分使用位置参数（`deviceId, channelId`）。风格不统一。
- **修复建议**：统一参数风格。

---

## 五、P3-建议问题（新增2个）

| 编号 | 文件 | 审计方法 | 问题描述 |
|------|------|---------|---------|
| R2-P3-01 | ptzControls-patch.md | #95 | 包含开发过程注释 |
| R2-P3-02 | 多个SIPp场景 | #104 | ResponseTimeRepartition配置冗余 |

---

## 六、累计问题统计（2轮）

| 级别 | 第1轮 | 第2轮 | 累计 |
|------|-------|-------|------|
| P0 | 8 | 0 | 8 |
| P1 | 29 | 3 | 32 |
| P2 | 49 | 5 | 54 |
| P3 | 24 | 2 | 26 |
| **合计** | **110** | **10** | **120** |
