# WVP 源码补丁修正包

> **原始问题**: 4/6 补丁无法应用到 WVP 源码（hunk 行号偏移、注释引用错误）
> **修正方法**: 根据《开发方案》重新提取变更，转换为可直接集成的代码指南
> **修正日期**: 2026-07-01

---

## 补丁状态

| 补丁 | 目标文件 | 原始状态 | 修正方式 |
|------|----------|----------|----------|
| 01 | `RegisterRequestProcessor.java` | ❌ hunk 偏移 | 重写为集成指南 |
| 02 | `DigestServerAuthenticationHelper.java` | ❌ hunk 偏移 | 重写为集成指南 |
| 03 | `DeviceControlType.java` | ✅ 正常 | 无需处理 |
| 04 | `SIPCommander.java`（s字段/下载速度） | ❌ hunk 偏移 | 重写为集成指南 |
| 05 | `DeviceControlQueryMessageHandler.java` | ❌ hunk 偏移 + 语义错误 | 重写 + 修复 B-11 |
| 06 | `UserSetting.java` | ✅ 正常 | 无需处理 |

---

## 审计发现的问题

### C-06: 行号引用错误
- 01 第66行：声称"设计文档第13.9节(第1598~1611行)"，实际13.9节在**第1649行**
- 02 第8行：声称"设计文档第5.3节(第531~550行)"，实际5.3节在**第742行**（8.3节）

### B-11: 命令失败仍返回OK（patch 05）
`deviceUpgradeCmd` 失败后仍 `responseAck(request, Response.OK)`，应返回错误响应。

---

## 使用方法

本目录下的 `.md` 文件替代原始 `.patch` 文件。每个文件包含：
1. 目标 WVP 源文件路径
2. 精确的代码变更（取自《开发方案》）
3. 修正后的设计文档行号引用

直接在对应 WVP 源文件中应用变更即可，无需 `git apply`。
