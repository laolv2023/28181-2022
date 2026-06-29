# WVP 前端 UI 改造方案（GB/T 28181-2022 配套）

> **方案对象**: WVP-GB28181-pro 前端（web/ 目录，Vue 2.6 + Element UI）
> **方案依据**: 《WVP 合规性升级改造开发方案》后端 38 项改造
> **方案目标**: 为后端新增的 2022 版功能提供前端 UI 入口和交互界面
> **方案日期**: 2026-06-29
> **WVP 前端版本**: 4.4.0 (vue-admin-template)

---

## 一、改造概述

### 1.1 改造范围

后端合规性升级新增了 12 项用户可感知的功能，其中 8 项需要前端 UI 配套，4 项为信令层透传无需前端改造。

| 类别 | 数量 | 说明 |
|---|---|---|
| **必须改前端** | 5 项 | 新增功能无 UI 入口，用户无法触发 |
| **建议改前端** | 3 项 | 提升用户体验，后端功能不受影响 |
| **无需改前端** | 4 项 | SM3、X-GB-ver、注册重定向、SDP 字段 — 信令层透传（SM3和注册重定向在F8中提供配置开关，但业务逻辑无需前端改动） |

### 1.2 技术栈

| 维度 | 版本 |
|---|---|
| Vue | 2.6.10 |
| Vue Router | 3.0.6 |
| Vuex | 3.1.0 |
| Element UI | ^2.15.14 |
| HTTP | axios ^0.24.0 |
| 构建 | vue-cli-service |

### 1.3 改造原则

1. **增量改造**：不修改原有页面结构，新增独立组件和对话框
2. **配置驱动**：通过设备 `isVersion2022()` 判断，仅 2022 版设备显示新功能按钮。前端通过 API 响应中的 `protocolVersion` 字段判断设备版本，`ptzControls.vue` 等通用组件需新增 `isVersion2022` prop 由父组件传入
3. **来源标注**：每个新增组件/方法标注对应后端改造项编号
4. **生产级**：包含加载状态、错误处理、用户反馈

---

## 二、改造项清单

### 2.1 必须改造（5 项）

| # | 改造项 | 后端改造项 | 改造位置 | 说明 |
|---|---|---|---|---|
| F1 | PTZ 精准控制面板 | 改造项7 | `web/src/views/common/ptzControls.vue` | 新增精准控制输入框 |
| F2 | 存储卡格式化+状态查询 | 改造项8,14 | `web/src/views/device/common/` 新增组件 | 新增对话框 |
| F3 | 目标跟踪控制 | 改造项9 | `web/src/views/common/ptzControls.vue` | 新增跟踪按钮 |
| F4 | 设备软件升级 | 改造项10 | `web/src/views/device/common/` 新增组件 | 新增升级对话框 |
| F5 | 新增查询页面 | 改造项11-13 | `web/src/views/device/common/` 新增组件 | 看守位/巡航轨迹/PTZ 精准状态查询 |

### 2.2 建议改造（3 项）

| # | 改造项 | 后端改造项 | 改造位置 | 说明 |
|---|---|---|---|---|
| F6 | 设备列表新增"协议版本"列 | 改造项1 | `web/src/views/device/list.vue` | 显示 X-GB-ver 协商结果 |
| F7 | 图像抓拍配置 | 改造项15 | `web/src/views/device/common/` 新增组件 | 抓拍分辨率/数量配置 |
| F8 | SM3/TLS 配置入口 | 改造项2,3,26,30,35 | `web/src/views/operations/securityConfig.vue` 新增页面 | 安全配置页新增开关 |

### 2.3 无需改造（4 项）

| 后端改造项 | 说明 |
|---|---|
| 改造项1 X-GB-ver | 信令层自动协商，前端无感知（F6 仅展示结果） |
| 改造项2 SM3 | 摘要算法后端自动降级，前端无需改动业务逻辑（F8 仅提供配置开关） |
| 改造项3 注册重定向 | 302 响应后端自动处理，F8 提供系统级功能开关 |
| 改造项4-6,18-23 SDP 字段 | 媒体协商后端自动处理 |

---

## 三、详细改造方案

### 3.1 F1: PTZ 精准控制面板

**后端改造项**: 改造项7（PTZ 精准控制命令，A.2.3.1.11）
**改造文件**: `web/src/views/common/ptzControls.vue`
**新增 API**: `web/src/api/frontEnd.js`

#### 新增前端 API

```javascript
// web/src/api/frontEnd.js 新增

/**
 * PTZ精准控制
 * 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11
 * @param {Object} params - { deviceId, channelId, pan, tilt, zoom }
 * @returns {Promise}
 */
export function ptzPrecise({ deviceId, channelId, pan, tilt, zoom }) {
  return request({
    method: 'get',
    url: `/api/device/control/ptz_precise/${deviceId}/${channelId}`,
    params: { pan, tilt, zoom }
  })
}
```

#### ptzControls.vue 改造

在 PTZ 控制面板底部新增"精准控制"折叠区域：

```vue
<!-- === F1改造: PTZ精准控制面板 === -->
<!-- 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11 -->
<!-- 规范要求: 2022版新增PTZ精准控制, 支持pan/Tilt/zoom精确角度设置 -->
<!-- D7已修复: 补充展开/收起按钮触发 preciseVisible -->
<div v-if="isVersion2022" class="ptz-precise-toggle" @click="preciseVisible = !preciseVisible">
  <i :class="preciseVisible ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" />
  <span>精准控制（GB/T 28181-2022）</span>
</div>
<div v-show="preciseVisible" class="ptz-precise-section" style="transition: all 0.3s;">
  <el-form :inline="true" size="mini" label-width="60px">
    <el-form-item label="水平角">
      <el-input-number v-model="precisePan"
        :precision="2" :step="0.1" :min="-180" :max="180"
        controls-position="right" size="mini" style="width: 120px" />
    </el-form-item>
    <el-form-item label="垂直角">
      <el-input-number v-model="preciseTilt"
        :precision="2" :step="0.1" :min="-90" :max="90"
        controls-position="right" size="mini" style="width: 120px" />
    </el-form-item>
    <el-form-item label="变倍">
      <el-input-number v-model="preciseZoom"
        :precision="1" :step="0.5" :min="0" :max="20"
        controls-position="right" size="mini" style="width: 120px" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" size="mini" @click="handlePtzPrecise">发送</el-button>
    </el-form-item>
  </el-form>
</div>
```

新增 data/methods：

```javascript
// props 新增（A2修复: ptzControls.vue 需新增 isVersion2022 prop）
// 来源: 审计A2, 通用组件无法获取设备版本, 需由父组件传入
props: {
  isVersion2022: { type: Boolean, default: false }  // 由父组件 live/index.vue 传入
},

// data 新增
preciseVisible: false,    // 精准控制面板展开状态
precisePan: 0,            // 水平角度 (-180~180)
preciseTilt: 0,           // 垂直角度 (-90~90)
preciseZoom: 1,           // 变倍倍数 (0~20)
trackMode: 'Auto',        // 目标跟踪模式 (Auto/Manual)

// methods 新增
handlePtzPrecise() {
  // 来源: 后端改造项7, 2022版A.2.3.1.11 PTZ精准控制
  this.$emit('ptz-precise', {
    pan: this.precisePan,
    tilt: this.preciseTilt,
    zoom: this.preciseZoom
  })
}
```

> **R3修复**: `ptz-precise` 和 `target-track` 事件由父组件 `live/index.vue` 监听并调用 API。父组件需添加：`<ptz-controls @ptz-precise="handlePtzPrecise" @target-track="handleTargetTrack" :is-version2022="device.isVersion2022()" />`，并在 methods 中调用 `ptzPrecise(params)` 和 `targetTrack(params)` API。

### 3.2 F2: 存储卡格式化与状态查询

**后端改造项**: 改造项8（存储卡格式化）、改造项14（存储卡状态查询）
**新增文件**: `web/src/views/device/common/StorageCardDialog.vue`

```vue
<!-- StorageCardDialog.vue — 存储卡格式化与状态查询 -->
<!-- 来源: 后端改造项8(格式化, 2022版A.2.3.1.13) + 改造项14(状态查询, 2022版A.2.4.14) -->
<template>
  <el-dialog title="存储卡管理" :visible.sync="visible" width="500px"
    :close-on-click-modal="false" @open="queryStatus">
    <div v-loading="loading">
      <!-- 存储卡状态 -->
      <h4 style="margin: 8px 0">存储卡状态</h4>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总容量">{{ capacity }} MB</el-descriptions-item>
        <el-descriptions-item label="剩余容量">{{ remainCapacity }} MB</el-descriptions-item>
      </el-descriptions>
    </div>
    <div slot="footer">
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="danger" :loading="formatting"
        @click="handleFormat" :disabled="status === 0">
        格式化存储卡
      </el-button>
    </div>
  </el-dialog>
</template>
```

新增 API：

```javascript
// web/src/api/frontEnd.js 新增

/** 存储卡状态查询 (改造项14, 2022版A.2.4.14) */
export function queryStorageCardStatus(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/storage_card_status_query/${deviceId}/${channelId}`
  })
}

/** 存储卡格式化 (改造项8, 2022版A.2.3.1.13) */
// B1修复: 破坏性操作使用 POST 方法
export function formatStorageCard(deviceId, channelId) {
  return request({
    method: 'post',
    url: `/api/device/control/format_sdcard/${deviceId}/${channelId}`
  })
}
```

> **R3修复**: StorageCardDialog.vue 需包含 `data() { return { visible: false, cardStatus: {}, formatting: false } }` 和 methods: `queryStatus()`（调用 `queryStorageCardStatus`）、`handleFormat()`（调用 `formatStorageCard`）、`handleClose()`。`@open` 事件触发 `queryStatus()`。模板中 `statusTagType`、`statusText`、`capacity`、`remainCapacity` 为 computed 属性，从 `cardStatus` 派生。

### 3.3 F3: 目标跟踪控制

**后端改造项**: 改造项9（目标跟踪命令，A.2.3.1.14）
**改造文件**: `web/src/views/common/ptzControls.vue`

在 PTZ 功能按钮区新增"目标跟踪"按钮：

```vue
<!-- === F3改造: 目标跟踪 === -->
<!-- 来源: 后端改造项9, 设计文档第10.1节, 2022版A.2.3.1.14 -->
<!-- 规范要求: 2022版新增目标跟踪, action: Auto(自动)/Manual(手动)（停止跟踪由后端超时自动处理） -->
<div class="ptz-func-row" v-if="isVersion2022">
  <div class="ptz-func-btn" :class="{active: trackMode === 'Auto'}"
    title="自动跟踪" @click="$emit('target-track', 'Auto')">
    <i class="iconfont icon-track" /><span>自动跟踪</span>
  </div>
  <div class="ptz-func-btn" :class="{active: trackMode === 'Manual'}"
    title="手动跟踪" @click="$emit('target-track', 'Manual')">
    <i class="iconfont icon-track-manual" /><span>手动跟踪</span>
  </div>
</div>
```

新增 API：

```javascript
/** 目标跟踪 (改造项9, 2022版A.2.3.1.14) */
export function targetTrack({ deviceId, channelId, action }) {
  return request({
    method: 'get',
    url: `/api/device/control/target_track/${deviceId}/${channelId}`,
    params: { action }  // Auto | Manual（停止跟踪由后端超时自动处理，非规范定义值）
  })
}
```

### 3.4 F4: 设备软件升级

**后端改造项**: 改造项10（设备软件升级命令，A.2.3.1.12）
**新增文件**: `web/src/views/device/common/DeviceUpgradeDialog.vue`

```vue
<!-- DeviceUpgradeDialog.vue — 设备软件升级 -->
<!-- 来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12 -->
<!-- 规范要求: 2022版新增设备软件升级, 需上传固件文件 -->
<template>
  <el-dialog title="设备软件升级" :visible.sync="visible" width="600px"
    :close-on-click-modal="false">
    <el-form :model="form" label-width="100px" size="small">
      <el-form-item label="设备ID">
        <el-input v-model="form.deviceId" disabled />
      </el-form-item>
      <el-form-item label="固件文件" required>
        <el-upload action="#" :auto-upload="false" :on-change="handleFileChange"
          :limit="1" accept=".bin,.img,.zip">
          <el-button size="small" type="primary">选择固件</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item label="厂商">
        <el-input v-model="form.manufacturer" placeholder="设备厂商" />
      </el-form-item>
    </el-form>
    <!-- 升级结果通知区域 (后端改造项10: 设备软件升级结果通知) -->
    <el-alert v-if="upgradeResult" :title="upgradeResultTitle"
      :type="upgradeResult === 'OK' ? 'success' : 'error'" :closable="false" />
    <div slot="footer">
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="upgrading"
        @click="handleUpgrade" :disabled="!form.firmware">开始升级</el-button>
    </div>
  </el-dialog>
</template>
```

> **D5修复**: 设备升级涉及文件上传。流程为：(1) 用户选择本地固件文件 → (2) 调用文件上传 API `/api/device/control/upload_firmware` 上传到服务器 → (3) 服务器返回文件 URL → (4) 调用 `deviceUpgrade` API 传入 `fileUrl` 触发升级。需在 `frontEnd.js` 新增文件上传 API：
>
> ```javascript
> // D5修复: 固件文件上传 API
> export function uploadFirmware(deviceId, file) {
>   const formData = new FormData()
>   formData.append('file', file)
>   return request({
>     method: 'post',
>     url: `/api/device/control/upload_firmware/${deviceId}`,
>     data: formData
>     // 注意: 不使用手动 Content-Type，由 axios 自动设置 boundary
>   })
> }
> ```
> `handleUpgrade` 方法中先调用 `uploadFirmware` 获取 `fileUrl`，再调用 `deviceUpgrade`。

### 3.5 F5: 新增查询页面

**后端改造项**: 改造项11（看守位）、改造项12（巡航轨迹）、改造项13（PTZ 精准状态）
**新增文件**: `web/src/views/device/common/QueryResultDialog.vue`

统一查询结果对话框，通过 tab 切换：

```vue
<!-- QueryResultDialog.vue — 2022版新增查询结果 -->
<!-- 来源: 改造项11(看守位查询) + 改造项12(巡航轨迹查询) + 改造项13(PTZ精准状态查询) -->
<template>
  <el-dialog title="设备信息查询" :visible.sync="visible" width="700px">
    <el-tabs v-model="activeTab">
      <!-- 改造项11: 看守位信息查询 (2022版A.2.4.10) -->
      <el-tab-pane label="看守位信息" name="homePosition">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="启用状态">
            {{ homePositionInfo.enabled ? '已启用' : '未启用' }}
          </el-descriptions-item>
          <el-descriptions-item label="预置位ID">{{ homePositionInfo.presetIndex }}</el-descriptions-item>
          <el-descriptions-item label="复位时间">{{ homePositionInfo.resetTime }}秒</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <!-- 改造项12: 巡航轨迹查询 (2022版A.2.4.11) -->
      <el-tab-pane label="巡航轨迹" name="cruiseTrack">
        <el-table :data="cruiseTrackList" size="small" border>
          <el-table-column prop="id" label="轨迹ID" width="80" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="presetList" label="预置位序列" />
        </el-table>
      </el-tab-pane>
      <!-- 改造项13: PTZ精准状态查询 (2022版A.2.4.13) -->
      <el-tab-pane label="PTZ精准状态" name="ptzPreciseStatus">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="水平角(Pan)">{{ ptzStatus.pan }}°</el-descriptions-item>
          <el-descriptions-item label="垂直角(Tilt)">{{ ptzStatus.tilt }}°</el-descriptions-item>
          <el-descriptions-item label="变倍(Zoom)">{{ ptzStatus.zoom }}x</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>
```

> **R3修复**: QueryResultDialog.vue 需包含 `data() { return { visible: false, activeTab: 'homePosition', homePositionInfo: {}, cruiseTrackList: [], ptzStatus: {} } }` 和 methods: `queryHomePosition()`、`queryCruiseTrack()`、`queryPtzPreciseStatus()`。在 `<el-tabs @tab-click="handleTabClick">` 上监听 tab 切换事件触发对应查询方法。

### 3.6 F6: 设备列表新增"协议版本"列

**后端改造项**: 改造项1（X-GB-ver 版本协商）
**改造文件**: `web/src/views/device/list.vue`

在设备列表表格中新增一列：

```vue
<!-- === F6改造: 设备列表新增协议版本列 === -->
<!-- 来源: 后端改造项1, 设计文档第13.9节, 2022版附录I -->
<!-- 说明: 显示X-GB-ver版本协商结果, "2.0"=2022版, "1.0"或空=2016版 -->
<el-table-column label="协议版本" min-width="100">
  <template slot-scope="scope">
    <el-tag :type="(scope.row.protocolVersion || '').startsWith('2.') ? 'success' : 'info'" size="small">
      {{ (scope.row.protocolVersion || '').startsWith('2.') ? '2022' : '2016' }}
    </el-tag>
  </template>
</el-table-column>
```

### 3.7 F7: 图像抓拍配置

**后端改造项**: 改造项15（图像抓拍功能，设计文档第12.2节，2022版9.14）
**新增文件**: `web/src/views/device/common/SnapshotConfigDialog.vue`

```vue
<!-- SnapshotConfigDialog.vue — 图像抓拍配置 -->
<!-- 来源: 后端改造项15, 设计文档第12.2节, 2022版9.14 -->
<!-- 注: interval/uploadUrl/sessionId 由前端自动生成(sessionId=UUID)或使用系统默认值 -->
<!-- 规范要求: 2022版新增图像抓拍, Resolution 枚举值由后端 SnapshotConfigMessageHandler 定义 -->
<template>
  <el-dialog title="图像抓拍" :visible.sync="visible" width="500px">
    <el-form :model="form" label-width="100px" size="small">
      <el-form-item label="分辨率">
        <el-select v-model="form.resolution" placeholder="选择分辨率">
          <el-option label="CIF (352×288)" :value="0" />
          <el-option label="4CIF (704×576)" :value="1" />
          <el-option label="D1" :value="2" />
          <el-option label="720P" :value="3" />
          <el-option label="1080P" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="抓拍数量">
        <el-input-number v-model="form.snapNum" :min="1" :max="10" />
      </el-form-item>
    </el-form>
    <!-- 抓拍结果 (后端改造项15: 抓拍图像传输完成通知) -->
    <el-table v-if="snapshotList.length" :data="snapshotList" size="small" border>
      <el-table-column prop="index" label="序号" width="60" />
      <el-table-column prop="fileId" label="文件ID" />
    </el-table>
    <div slot="footer">
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="capturing" @click="handleSnapshot">抓拍</el-button>
    </div>
  </el-dialog>
</template>
```

> **R3修复**: SnapshotConfigDialog.vue 需包含 `data() { return { visible: false, form: { resolution: 3, snapNum: 1 }, snapshotList: [], capturing: false } }` 和 methods: `handleSnapshot()`（调用 `snapshotConfig` API）、`handleClose()`。

### 3.8 F8: 系统配置新增 SM3/TLS 开关

**后端改造项**: 改造项2（SM3）、改造项3（注册重定向）、改造项26（TCP重连）、改造项30（TLS）、改造项35（字符集）
**改造文件**: 新增 `web/src/views/operations/securityConfig.vue`

> **B3修复**: `operations/` 目录下现有页面仅含日志和系统信息（`historyLog.vue`/`realLog.vue`/`showLog.vue`/`systemInfo.vue`），无配置编辑页面。因此新增独立的 `securityConfig.vue` 配置页面，并在路由中注册。

在新增配置页面中添加安全配置区域：

```vue
<!-- === F8改造: 安全配置 === -->
<!-- 来源: 后端改造项2(SM3, 设计文档第8.3节) + 改造项3(注册重定向) + 改造项26(TCP重连) + 改造项30(TLS, 设计文档第8.2节) + 改造项35(字符集) -->
<el-card header="安全配置（GB/T 28181-2022）">
  <el-form label-width="180px" size="small">
    <el-form-item label="SM3摘要算法">
      <el-switch v-model="config.sm3DigestEnabled" />
      <span class="config-hint">开启后摘要认证支持SM3算法（保留MD5兼容）</span>
    </el-form-item>
    <el-form-item label="TLS加密传输">
      <el-switch v-model="config.sipTlsEnabled" />
      <span class="config-hint">开启后SIP信令通过TLS加密传输</span>
    </el-form-item>
    <el-form-item label="SIP字符集">
      <el-select v-model="config.sipCharset">
        <el-option label="GB 18030（2022版）" value="gb18030" />
        <el-option label="GB 2312（2016版）" value="gb2312" />
      </el-select>
    </el-form-item>
    <el-form-item label="注册重定向">
      <el-switch v-model="config.registerRedirectEnabled" />
      <span class="config-hint">开启后支持302注册重定向</span>
    </el-form-item>
    <el-form-item label="TCP媒体重连">
      <el-switch v-model="config.tcpReconnectEnabled" />
    </el-form-item>
    <!-- D6修复: 补充保存按钮 -->
    <el-form-item>
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </el-form-item>
  </el-form>
</el-card>
```

> **D6修复**: 补充保存配置功能。需在 `frontEnd.js` 新增保存 API：
>
> ```javascript
> // D6修复: 保存安全配置 API
> export function saveSecurityConfig(data) {
>   return request({
>     method: 'post',
>     url: '/api/device/config/save_security',
>     data: data
>   })
> }
> ```
> `handleSave` 方法调用 `saveSecurityConfig(this.config)`，保存成功后提示"配置已保存，部分配置重启后生效"。

---

## 四、新增前端 API 汇总

所有新增 API 添加到 `web/src/api/frontEnd.js`：

> **A1修复**: API 路径统一使用 `/api/device/control/` 前缀，与现有 `device.js` 中的控制接口保持一致（如 `/api/device/control/teleboot`、`/api/device/control/drag_zoom/zoom_in`）。避免使用后端不存在的 `/api/front-end/` 前缀。

```javascript
// === GB/T 28181-2022 前端新增 API ===

// 改造项7: PTZ精准控制 (2022版A.2.3.1.11)
export function ptzPrecise({ deviceId, channelId, pan, tilt, zoom }) {
  return request({
    method: 'get',
    url: `/api/device/control/ptz_precise/${deviceId}/${channelId}`,
    params: { pan, tilt, zoom }
  })
}

// 改造项8: 存储卡格式化 (2022版A.2.3.1.13)
// B1修复: 破坏性操作使用 POST 方法
export function formatStorageCard(deviceId, channelId) {
  return request({
    method: 'post',
    url: `/api/device/control/format_sdcard/${deviceId}/${channelId}`
  })
}

// 改造项9: 目标跟踪 (2022版A.2.3.1.14)
export function targetTrack({ deviceId, channelId, action }) {
  return request({
    method: 'get',
    url: `/api/device/control/target_track/${deviceId}/${channelId}`,
    params: { action }  // Auto | Manual（停止跟踪由后端超时自动处理，非规范定义值）
  })
}

// 改造项10: 设备软件升级 (2022版A.2.3.1.12)
// B2修复: 补充 sessionID 参数（前端通过 crypto.randomUUID() 或 uuid 库生成）
export function deviceUpgrade({ deviceId, channelId, firmware, fileUrl, manufacturer, sessionId }) {
  return request({
    method: 'post',
    url: `/api/device/control/device_upgrade/${deviceId}/${channelId}`,
    data: { firmware, fileUrl, manufacturer, sessionId }
  })
}

// 改造项11: 看守位信息查询 (2022版A.2.4.10)
export function queryHomePosition(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/home_position_query/${deviceId}/${channelId}`
  })
}

// 改造项12: 巡航轨迹查询 (2022版A.2.4.11)
export function queryCruiseTrack(deviceId, channelId, trackListId) {
  return request({
    method: 'get',
    url: `/api/device/control/cruise_track_query/${deviceId}/${channelId}`,
    params: { trackListId }
  })
}

// 改造项13: PTZ精准状态查询 (2022版A.2.4.13)
export function queryPtzPreciseStatus(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/ptz_precise_status_query/${deviceId}/${channelId}`
  })
}

// 改造项14: 存储卡状态查询 (2022版A.2.4.14)
export function queryStorageCardStatus(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/storage_card_status_query/${deviceId}/${channelId}`
  })
}

// 改造项15: 图像抓拍配置 (2022版9.14)
// 注: resolution 为后端 SnapshotConfigMessageHandler 支持的枚举值(0-4)
export function snapshotConfig({ deviceId, channelId, resolution, snapNum, interval, uploadUrl, sessionId }) {
  return request({
    method: 'post',
    url: `/api/device/control/snapshot_config/${deviceId}/${channelId}`,
    data: { resolution, snapNum, interval, uploadUrl, sessionId }  // POST 请求体传参
  })
}

// 改造项10配套: 固件文件上传 (D5修复)
export function uploadFirmware(deviceId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    method: 'post',
    url: `/api/device/control/upload_firmware/${deviceId}`,
    data: formData,
    // 注意: 不使用手动 Content-Type，由 axios 自动设置 boundary
  })
}

// 改造项2,3,26,30,35配套: 保存安全配置 (D6修复)
export function saveSecurityConfig(data) {
  return request({
    method: 'post',
    url: '/api/device/config/save_security',
    data: data
  })
}
```

---

## 五、新增文件清单

| 文件路径 | 类型 | 说明 |
|---|---|---|
| `web/src/views/device/common/StorageCardDialog.vue` | 新增 | 存储卡格式化+状态查询对话框 |
| `web/src/views/device/common/DeviceUpgradeDialog.vue` | 新增 | 设备软件升级对话框 |
| `web/src/views/device/common/QueryResultDialog.vue` | 新增 | 看守位/巡航轨迹/PTZ精准状态查询结果 |
| `web/src/views/device/common/SnapshotConfigDialog.vue` | 新增 | 图像抓拍配置对话框 |
| `web/src/api/frontEnd.js` | 修改 | 新增 11 个 API 方法（9 个控制 API + uploadFirmware + saveSecurityConfig） |
| `web/src/views/common/ptzControls.vue` | 修改 | 新增精准控制面板+目标跟踪按钮 |
| `web/src/views/device/list.vue` | 修改 | 新增"协议版本"列 |
| `web/src/views/operations/securityConfig.vue` | 新增 | 安全配置页（SM3/TLS/字符集开关） |

---

## 六、改造实施计划

| 阶段 | 改造项 | 工作量 | 优先级 |
|---|---|---|---|
| 第一阶段 | F1(PTZ精准) + F3(目标跟踪) + F6(协议版本列) | 2 人天 | 高 |
| 第二阶段 | F2(存储卡) + F4(设备升级) + F5(查询页面) | 3 人天 | 高 |
| 第三阶段 | F7(抓拍配置) + F8(系统配置) | 1 人天 | 中 |

> **说明**：F6（协议版本列）虽属\"建议改造\"，但改动极小（仅新增一列），放在第一阶段可快速见效。F2/F4/F5 虽属\"必须改造\"，但涉及新增对话框组件，工作量较大，安排在第二阶段。

---

## 七、后端 Controller 配套改造

前端新增 API 需要后端 Controller 提供对应接口。以下接口需在现有 `ApiControlController.java`（`@RequestMapping("/api/v1/control")`）或 `ApiDeviceController.java`（`@RequestMapping("/api/v1/device")`）中新增对应方法。建议在 `ApiDeviceController.java` 中新增，与现有 `/api/device/control/` 路径前缀一致。

> **A3修复说明**: 这些 Controller 接口不在《WVP 合规性升级改造开发方案》的 38 项中，需要额外开发。Controller 内部调用已有的 `SIPCommander` 方法下发 SIP 命令到设备。以下为接口定义，Controller 实现代码需另行开发。

> **D3修复说明**: WVP 后端 `ApiDeviceController` 的 `@RequestMapping` 是 `/api/v1/device`，但前端 `device.js` 调用的是 `/api/device/control/...` 路径。这说明 WVP 可能有 Spring Boot 路径映射配置（如 `server.servlet.context-path` 或 `@RequestMapping` 级别调整）将 `/api/device/` 映射到 `ApiDeviceController`。新增接口应遵循同样的路径模式。如路径不通，需检查 WVP 的 Spring Boot 路由配置或在前端调整 API 前缀。

> **D4修复说明**: Controller 示例中调用 `cmder.ptzPreciseCmd()` 等方法，但 SIPCommander 中尚无这些方法。需在 `SIPCommander.java` 中新增以下 9 个方法（不在 38 项改造中，需额外开发）：
> - `ptzPreciseCmd(Device, String channelId, double pan, double tilt, double zoom)` — 改造项7
> - `formatSdcardCmd(Device, String channelId)` — 改造项8
> - `targetTrackCmd(Device, String channelId, String action)` — 改造项9
> - `deviceUpgradeCmd(Device, String channelId, String firmware, String fileUrl, String manufacturer, String sessionId)` — 改造项10
> - `homePositionQueryCmd(Device, String channelId)` — 改造项11
> - `cruiseTrackQueryCmd(Device, String channelId, Integer trackListId)` — 改造项12
> - `ptzPreciseStatusQueryCmd(Device, String channelId)` — 改造项13
> - `storageCardStatusQueryCmd(Device, String channelId)` — 改造项14
> - `snapshotConfigCmd(Device, String channelId, int resolution, int snapNum, int interval, String uploadUrl, String sessionId)` — 改造项15
>
> 这些方法内部构造 XML 消息（如 `<PTzPrecisectrl><pan>...</pan><Tilt>...</Tilt><zoom>...</zoom></PTzPrecisectrl>`），通过 SIP 发送到设备。

| API 路径 | 方法 | 对应后端改造项 | 说明 |
|---|---|---|---|
| `/api/device/control/ptz_precise/{deviceId}/{channelId}` | GET | 改造项7 | PTZ 精准控制 |
| `/api/device/control/format_sdcard/{deviceId}/{channelId}` | POST | 改造项8 | 存储卡格式化（破坏性操作用 POST） |
| `/api/device/control/target_track/{deviceId}/{channelId}` | GET | 改造项9 | 目标跟踪 |
| `/api/device/control/device_upgrade/{deviceId}/{channelId}` | POST | 改造项10 | 设备软件升级 |
| `/api/device/control/home_position_query/{deviceId}/{channelId}` | GET | 改造项11 | 看守位查询 |
| `/api/device/control/cruise_track_query/{deviceId}/{channelId}` | GET | 改造项12 | 巡航轨迹查询 |
| `/api/device/control/ptz_precise_status_query/{deviceId}/{channelId}` | GET | 改造项13 | PTZ 精准状态查询 |
| `/api/device/control/storage_card_status_query/{deviceId}/{channelId}` | GET | 改造项14 | 存储卡状态查询 |
| `/api/device/control/snapshot_config/{deviceId}/{channelId}` | POST | 改造项15 | 图像抓拍配置 |
| `/api/device/control/upload_firmware/{deviceId}` | POST | 改造项10配套 | 固件文件上传 |
| `/api/device/config/save_security` | POST | 改造项2,3,26,30,35配套 | 安全配置保存 |

> **R5修复**: F4 模板中引用了 `handleFileChange` 方法但未说明。该方法需在 DeviceUpgradeDialog.vue 的 methods 中定义：`handleFileChange(file) { this.form.firmware = file.name; this.form.rawFile = file.raw; }`，将用户选择的文件信息存入 form。

**Controller 实现示例**（以 PTZ 精准控制为例）：

```java
// === 改造项7 Controller: PTZ精准控制 ===
// 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11
// 说明: 接收前端请求, 调用 SIPCommander 下发 PTZ 精准控制命令到设备
@GetMapping("/control/ptz_precise/{deviceId}/{channelId}")
@ResponseBody
public Result ptzPrecise(
        @PathVariable String deviceId,
        @PathVariable String channelId,
        @RequestParam Double pan,
        @RequestParam Double tilt,
        @RequestParam Double zoom) {
    Device device = deviceService.getDevice(deviceId);
    if (device == null) {
        return Result.fail(400, "设备不存在");
    }
    // 调用 SIPCommander 下发 PTZ 精准控制命令
    // SIPCommander 内部构造 XML: <PTzPrecisectrl><pan>...</pan><Tilt>...</Tilt><zoom>...</zoom></PTzPrecisectrl>
    cmder.ptzPreciseCmd(device, channelId, pan, tilt, zoom);
    return Result.success();
}
```

---

## 八、版本兼容性

| 设备版本 | 前端行为 |
|---|---|
| 2022 版（`protocolVersion === '2.0'`） | 显示全部新功能按钮 |
| 2016 版（`protocolVersion` 为空或 `'1.0'`） | 隐藏 2022 新功能按钮，仅显示原有功能 |
| 未知（未注册） | 隐藏 2022 新功能按钮，注册后自动判断 |

通过 `device.isVersion2022()` 判断设备版本，前端通过 API 响应中的 `protocolVersion` 字段判断。

> **A5修复说明**: `protocolVersion` 字段由后端改造项1（Device.java 补丁 04-Device.patch）新增。设备列表接口 `/api/device/query/devices` 的响应中需要包含此字段。由于 `Device.java` 已添加 `protocolVersion` 属性，Spring/Jackson 会自动序列化到 JSON 响应中，但需确认设备列表查询 SQL 是否包含此列。如果数据库表中尚未添加 `protocolVersion` 列，需执行 `ALTER TABLE device ADD COLUMN protocol_version VARCHAR(10) DEFAULT NULL`。

---

## 九、规范来源说明

| 来源 | 说明 |
|---|---|
| 《WVP 合规性升级改造开发方案》 | 后端 38 项改造，本方案为其前端配套 |
| 《GB/T 28181—2022》 | 2022 版规范原文 |
| WVP-GB28181-pro web/ 目录 | 前端基线代码 |
