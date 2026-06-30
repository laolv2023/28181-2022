# WVP 合规性升级改造代码审计 — Round 8

> **审计焦点**：前端 UI 一致性、API 契约验证

---

## P2 (Medium)

### R8-P2-001: frontEnd.js — storageCardFormat API URL 与后端不一致

**文件**：`ui/api/frontEnd.js` L90 vs `backend/.../ApiDeviceControlController.java` L156
**维度**：G10
**问题**：
前端：`url: \`/api/device/control/format_storage_card/${deviceId}/${channelId}\``
后端：`@PostMapping("/format_storage_card/{deviceId}/{channelId}")`

路径一致。但前端使用 `data` 传参，后端使用 `@RequestParam`。同 R2-P0-002 问题模式。

**修复建议**：统一前后端参数传递方式。

---

### R8-P2-002: frontEnd.js — queryCruiseTrack API 路径与方法验证

**文件**：`ui/api/frontEnd.js` L171
**维度**：G10
**问题**：
前端使用 `method: 'get'`，后端 `@PostMapping`。HTTP 方法不一致。

**修复建议**：统一为 GET（查询操作）或 POST。

---

## Round 8 统计

| 严重性 | 数量 |
|--------|------|
| P0 | 0 |
| P1 | 0 |
| P2 | 2 |
| **总计** | **2** |
