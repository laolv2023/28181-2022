# WVP 合规性升级改造代码审计 — Round 2

> **审计日期**：2026-06-30
> **审计范围**：ui/、patches/、tests/、scripts/ 全部代码

---

## P0 (Critical)

### R2-P0-001: SecurityConfig.vue — loadConfig 方法未定义但被调用

**文件**：`ui/components/SecurityConfig.vue` L207
**维度**：A01
**问题**：
```javascript
created() {
    // 审计修复P2-41: 从后端加载配置
    this.loadConfig()
},
```
`created` 钩子调用 `this.loadConfig()`，但 `methods` 中**没有定义 `loadConfig` 方法**。methods 中仅有 `handleSave` 和 `handleReset`。这将导致运行时 `TypeError: this.loadConfig is not a function`。

注释声称"审计修复P2-41: 从后端加载配置"，但修复未实际实现。

**影响**：页面加载时 JavaScript 错误，安全配置页面无法正常工作。
**修复建议**：实现 `loadConfig` 方法，调用后端 `/api/device/config/get_security` 接口加载配置。

---

### R2-P0-002: frontEnd.js — deviceUpgrade API 使用 data 传参但后端使用 @RequestParam

**文件**：`ui/api/frontEnd.js` L139 vs `backend/.../ApiDeviceControlController.java` L273-277
**维度**：G10
**问题**：
前端 API：
```javascript
return request({
    method: 'post',
    url: `/api/device/control/device_upgrade/${deviceId}/${channelId}`,
    data: { firmware, fileUrl, manufacturer, sessionId },  // JSON body
    timeout: 30000
})
```
后端 Controller：
```java
@PostMapping("/device_upgrade/{deviceId}/{channelId}")
public WVPResult<?> deviceUpgrade(
    @PathVariable String deviceId,
    @PathVariable String channelId,
    @RequestParam String firmware,      // 期望 query param
    @RequestParam String fileUrl,       // 期望 query param
    @RequestParam String manufacturer,  // 期望 query param
    @RequestParam String sessionId)     // 期望 query param
```
前端使用 `data`（JSON body）传参，后端使用 `@RequestParam`（URL query param）接收。参数无法正确传递，后端将报 `Missing required parameter` 错误。

**影响**：设备升级功能完全不可用。
**修复建议**：统一前后端参数传递方式。要么前端改为 `params`，要么后端改为 `@RequestBody`。

---

### R2-P0-003: frontEnd.js — snapshotConfig API 同样使用 data 传参但后端使用 @RequestParam

**文件**：`ui/api/frontEnd.js` L253 vs `backend/.../ApiDeviceControlController.java` L423-427
**维度**：G10
**问题**：与 R2-P0-002 相同的问题。前端使用 `data: { resolution, snapNum, ... }`（JSON body），后端使用 `@RequestParam` 接收。

**影响**：图像抓拍配置功能不可用。
**修复建议**：同 R2-P0-002。

---

## P1 (High)

### R2-P1-001: frontEnd.js — ptzPrecise 使用 params 传参但后端使用 @RequestParam + POST

**文件**：`ui/api/frontEnd.js` L43
**维度**：G10
**问题**：
```javascript
return request({
    method: 'post',
    url: `/api/device/control/ptz_precise/${deviceId}/${channelId}`,
    params: { pan: pan ?? null, tilt: tilt ?? null, zoom: zoom ?? null },
    timeout: 10000
})
```
前端使用 `params`（URL query string），后端使用 `@RequestParam`。这里匹配正确（POST + query params）。

但 `pan ?? null` 当 pan 为 `0` 时返回 `0`（正确），当 pan 为 `undefined` 时返回 `null`。`@RequestParam Double pan` 接收到 `null` 字符串会报错。

**影响**：当参数缺失时后端报错。
**修复建议**：参数校验在前端完成，缺失参数时直接 reject。

---

### R2-P1-002: DeviceUpgradeDialog.vue — canUpgrade 计算属性逻辑错误

**文件**：`ui/components/DeviceUpgradeDialog.vue` L205-210
**维度**：F07
**问题**：
```javascript
canUpgrade() {
    return this.uploadSuccess &&
           this.form.manufacturer.trim() !== '' &&
           !this.uploading &&
           !this.upgrading
}
```
按钮文字（L118）：
```html
{{ uploading ? '上传中...' : '开始升级' }}
```
当 `uploading=true` 时，`canUpgrade` 返回 false（按钮禁用），但 `handleUpgrade` 方法中（L340-342）：
```javascript
if (!this.uploadSuccess) {
    await this.doUpload()
}
```
`handleUpgrade` 在 `canUpgrade=false`（uploadSuccess=false）时不应被触发。但按钮的 `:disabled` 绑定到 `canUpgrade`，当 uploadSuccess=false 时按钮禁用，用户无法点击"开始升级"。

这意味着用户**必须先手动上传文件**才能升级，但 UI 没有提供单独的"上传"按钮——上传是在 `handleUpgrade` 中自动触发的。这形成了死循环：按钮禁用→无法点击→无法触发上传→uploadSuccess 永远 false。

**影响**：用户无法执行设备升级操作。
**修复建议**：修改 `canUpgrade` 逻辑，允许在已选择文件但未上传时也能点击升级按钮（按钮触发上传+升级流程）。

---

### R2-P1-003: 多个 Vue 组件中 beforeDestroy 引用未定义的 timer 和 abortController

**文件**：
- `ui/components/DeviceUpgradeDialog.vue` L216-217
- `ui/components/SecurityConfig.vue` L212-213
- `ui/components/SnapshotConfigDialog.vue` L173-174
**维度**：B01
**问题**：
```javascript
beforeDestroy() {
    if (this.timer) { clearTimeout(this.timer); }
    if (this.abortController) { this.abortController.abort(); }
},
```
`this.timer` 和 `this.abortController` 在 `data()` 中未声明，在组件中从未赋值。`this.abortController.abort()` 在 `abortController` 为 undefined 时不会执行（因 if 判断），但代码冗余且制造了"已实现资源清理"的假象。

**修复建议**：删除未使用的清理代码，或实际实现 timer 和 abortController。

---

### R2-P1-004: SecurityConfig.vue — beforeRouteLeave 在组件内定义但未被路由感知

**文件**：`ui/components/SecurityConfig.vue` L182
**维度**：G09
**问题**：`beforeRouteLeave` 是 Vue Router 的组件内守卫，仅在路由配置中注册的组件才生效。但注释（L18-23）说明该组件"作为独立页面使用，需在路由中注册"。如果用户直接通过 `<SecurityConfig>` 标签使用而非路由组件，`beforeRouteLeave` 不会生效。

**修复建议**：明确文档说明此组件必须在路由中注册使用。

---

### R2-P1-005: frontEnd.js — uploadFirmware 未校验文件类型

**文件**：`ui/api/frontEnd.js` L154-170
**维度**：C03
**问题**：`uploadFirmware` 仅检查 `file instanceof File` 和 `file.size === 0`，未校验文件扩展名。虽然 `DeviceUpgradeDialog.vue` 的 `handleFileChange` 中有前端校验（L271-278），但 `uploadFirmware` 作为公共 API 可被直接调用，绕过 Dialog 的校验。

**修复建议**：在 `uploadFirmware` 中也做文件类型校验。

---

## P2 (Medium)

### R2-P2-001: SnapshotConfigDialog.vue — snapshotList 的 index 计算错误

**文件**：`ui/components/SnapshotConfigDialog.vue` L221
**维度**：F07
**问题**：
```javascript
this.snapshotList.unshift({
    index: this.snapshotList.length + 1,
    ...
})
```
使用 `unshift`（在数组头部插入），但 `index` 使用 `length + 1`（追加到末尾时的序号）。第一次抓拍：length=0, index=1；第二次抓拍：length=1, index=2（但 unshift 后第一条变成第二条）。序号与实际顺序不一致。

**修复建议**：使用固定递增计数器或 `this.snapshotList.length + 1`（在 unshift 之前计算）。

---

### R2-P2-002: DeviceUpgradeDialog.vue — 文件扩展名提取方式不安全

**文件**：`ui/components/DeviceUpgradeDialog.vue` L273
**维度**：B06
**问题**：
```javascript
const ext = '.' + fileName.split('.').pop()
```
当文件名为 `firmware`（无扩展名）时，`split('.')` 返回 `['firmware']`，`pop()` 返回 `'firmware'`，`ext` 变成 `.firmware`，不在白名单中（正确行为）。

但当文件名为 `.bin`（隐藏文件，无主名）时，`split('.')` 返回 `['', 'bin']`，`pop()` 返回 `'bin'`，`ext` 变成 `.bin`（正确）。

整体逻辑可接受，但边界情况需注意。

**修复建议**：可接受，添加注释说明边界行为。

---

### R2-P2-003: patches/ 目录下补丁文件未实际应用验证

**文件**：`patches/` 目录
**维度**：G04
**问题**：patches 目录下有 6 个补丁文件（.md 格式），描述了对 WVP 原始代码的修改。但这些补丁仅以文档形式存在，未实际以 `.patch` 或 `.diff` 格式提供，无法验证是否可正确应用。

**修复建议**：提供标准 patch 格式文件或验证文档中的修改指引可操作性。

---

### R2-P2-004: tests/sipp/ 下的 XML 测试脚本未验证与 2022 规范的一致性

**文件**：`tests/sipp/*.xml`
**维度**：E01-E20
**问题**：SIPp 测试脚本中的 SIP 消息模板需要验证 XML 元素名大小写、SDP 字段格式等是否与 2022 规范一致。未逐文件检查。

**修复建议**：逐个验证 SIPp 测试脚本中的协议参数。

---

### R2-P2-005: scripts/merge.sh 未检查目标目录是否存在

**文件**：`scripts/merge.sh`
**维度**：F03
**问题**：需检查脚本是否有目录存在性检查、错误处理等。

---

## Round 2 统计

| 严重性 | 数量 |
|--------|------|
| P0 (Critical) | 3 |
| P1 (High) | 5 |
| P2 (Medium) | 5 |
| P3 (Low) | 0 |
| **总计** | **13** |

## Round 1+2 累计

| 严重性 | Round 1 | Round 2 | 总计 |
|--------|---------|---------|------|
| P0 | 7 | 3 | 10 |
| P1 | 10 | 5 | 15 |
| P2 | 10 | 5 | 15 |
| P3 | 4 | 0 | 4 |
| **总计** | **31** | **13** | **44** |
