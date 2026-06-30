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
| P1-严重 | 2 |
| P2-一般 | 4 |
| P3-建议 | 2 |
| **合计** | **8** |

---

## 二、P0问题交叉验证结果

| 编号 | 文件 | 验证结果 |
|------|------|---------|
| P0-01 | 5个Handler cmdType小写 | ✓ 确认：5个Handler均使用cmdType但常量是CMD_TYPE |
| P0-02 | SdpFieldHelperTest断言矛盾 | ✓ 确认：assertTrue和assertFalse检查相同字符串 |
| P0-03 | SipTlsProperties孤立Javadoc | ✓ 确认：第256-260行孤立注释 |
| P0-04 | GBProtocolVersionHelper格式 | ✓ 确认：第118行两条语句同一行 |
| P0-05 | FormatSdcard不一致 | ✓ 确认：SIPCommander用FormatSdcard，枚举用FormatsDcard |
| P0-06 | FileURL注释矛盾 | ✓ 确认：注释说u小写但代码U大写 |

---

## 三、P1-严重问题（新增2个）

### R2-P1-01: SIPp场景设备控制命令XML根元素错误

- **文件**：`tests/sipp/control/` 目录下9个控制场景（除ctrl_snapshot_finished_notify和ctrl_upgrade_result_notify外）
- **审计方法**：#90 XML元素名大小写合规性、#111 SIPp场景完整性
- **问题描述**：
  9个设备控制场景使用 `<Control>` 作为XML根元素：
  ```xml
  <Control>
  <CmdType>DeviceControl</CmdType>
  ...
  </Control>
  ```
  但GB/T 28181-2022规范中设备控制命令的XML根元素应为 `<Notify>`（CmdType=DeviceControl）。只有ctrl_snapshot_finished_notify和ctrl_upgrade_result_notify正确使用 `<Notify>`。
- **影响**：测试场景与规范不符，无法验证平台对正确格式消息的处理。
- **修复建议**：将控制场景的XML根元素改为 `<Notify>`。

### R2-P1-02: SIPCommander2022Supplement snapshotConfig缺少uploaduRL字段

- **文件**：`backend/.../SIPCommander2022Supplement.java` 第371-377行
- **审计方法**：#19 功能完整性、#90 XML元素名大小写合规性
- **问题描述**：
  ```java
  xml.append("<SnapConfig>\r\n");
  xml.append("<Resolution>").append(resolution).append("</Resolution>\r\n");
  xml.append("<snapNum>").append(snapNum).append("</snapNum>\r\n");
  // 缺少 uploaduRL 字段
  xml.append("</SnapConfig>\r\n");
  ```
  注释说"字段大小写: uploaduRL(u小写)"但代码中未添加该字段。方法参数有uploadUrl但未使用。
- **影响**：抓拍配置命令缺少上传URL字段，设备不知道将抓拍图像上传到哪里。
- **修复建议**：添加 `<uploaduRL>` 字段。

---

## 四、P2-一般问题（新增4个）

### R2-P2-01: ApiDeviceControlController错误消息泄露deviceId

- **文件**：`backend/.../ApiDeviceControlController.java` 第84行
- **审计方法**：#41 错误信息泄露
- **问题描述**：
  ```java
  return WVPResult.fail(404, "设备不存在: " + deviceId);
  ```
  错误消息包含deviceId，可能泄露设备编码信息给攻击者。
- **修复建议**：返回通用错误消息，详细信息记录在日志中。

### R2-P2-02: SIPCommander2022Supplement fileUrl返回相对路径

- **文件**：`backend/.../SIPCommander2022Supplement.java` 第239行
- **审计方法**：#19 功能完整性
- **问题描述**：
  ```java
  String fileUrl = "/firmware/" + deviceId + "/" + savedName;
  ```
  返回相对路径，设备无法通过此URL下载固件。
- **修复建议**：返回完整的HTTP可访问URL。

### R2-P2-03: GbCode2022设备类型码校验不严格

- **文件**：`news/.../utils/GbCode2022.java` 第275-278行
- **审计方法**：#19 功能完整性
- **问题描述**：
  ```java
  if (!isNewTypeCode(deviceTypeCode)) {
      logger.debug("[编码校验] 设备类型码非新型: {}", deviceTypeCode);
      // 不阻断，继续校验
  }
  ```
  设备类型码非新型时仅记录debug日志，不阻断校验。
- **修复建议**：根据业务需求决定是否阻断。

### R2-P2-04: ApiConfigController配置值类型检查不完整

- **文件**：`backend/.../ApiConfigController.java` 第79-116行
- **审计方法**：#27 参数范围校验
- **问题描述**：
  ```java
  if (config.containsKey("sm3DigestEnabled")) {
      Object val = config.get("sm3DigestEnabled");
      if (val instanceof Boolean) {
          SECURITY_CONFIG.put("sm3DigestEnabled", val);
      }
      // 非Boolean时静默忽略，无错误反馈
  }
  ```
  配置值类型不匹配时静默忽略，用户不知道配置未生效。
- **修复建议**：类型不匹配时返回错误信息。

---

## 五、P3-建议问题（新增2个）

| 编号 | 文件 | 审计方法 | 问题描述 |
|------|------|---------|---------|
| R2-P3-01 | 多个Handler | #105 | handForPlatform传parentPlatform但未使用，死代码 |
| R2-P3-02 | SIPCommander2022Supplement | #96 | SN计数器注释说需持久化但未实现 |

---

## 六、累计问题统计（2轮）

| 级别 | 第1轮 | 第2轮 | 累计 |
|------|-------|-------|------|
| P0 | 6 | 0 | 6 |
| P1 | 28 | 2 | 30 |
| P2 | 42 | 4 | 46 |
| P3 | 20 | 2 | 22 |
| **合计** | **96** | **8** | **104** |
