# WVP 前端 UI 升级改造 —— GB/T 28181-2022 配套

## 概述

本目录包含 WVP-GB28181-pro 前端升级改造的全部新增代码和补丁说明。

**依据**: 《WVP前端UI改造方案》（`doc/WVP前端UI改造方案.md`）
**后端配套**: 《WVP合规性升级改造开发方案》（`doc/WVP合规性升级改造开发方案.md`）
**技术栈**: Vue 2.6.10 + Element UI ^2.15.14 + axios ^0.24.0

## 目录结构

```
ui/
├── api/
│   └── frontEnd.js              ← 新增 11 个前端 API 函数
├── components/
│   ├── StorageCardDialog.vue    ← F2: 存储卡格式化与状态查询
│   ├── DeviceUpgradeDialog.vue  ← F4: 设备软件升级
│   ├── QueryResultDialog.vue    ← F5: 看守位/巡航轨迹/PTZ状态查询
│   ├── SnapshotConfigDialog.vue ← F7: 图像抓拍配置
│   └── SecurityConfig.vue       ← F8: SM3/TLS/字符集安全配置页
├── patches/
│   ├── ptzControls-patch.md     ← F1+F3: PTZ精准控制 + 目标跟踪（ptzControls.vue 补丁）
│   └── deviceList-patch.md      ← F6: 协议版本列（list.vue 补丁）
└── README.md                    ← 本文件
```

## 改造项清单

| 编号 | 功能 | 类型 | 文件 |
|------|------|------|------|
| F1 | PTZ 精准控制面板 | 补丁 | `patches/ptzControls-patch.md` |
| F2 | 存储卡格式化+状态查询 | 新组件 | `components/StorageCardDialog.vue` |
| F3 | 目标跟踪控制 | 补丁 | `patches/ptzControls-patch.md` |
| F4 | 设备软件升级 | 新组件 | `components/DeviceUpgradeDialog.vue` |
| F5 | 新增查询页面 | 新组件 | `components/QueryResultDialog.vue` |
| F6 | 设备列表协议版本列 | 补丁 | `patches/deviceList-patch.md` |
| F7 | 图像抓拍配置 | 新组件 | `components/SnapshotConfigDialog.vue` |
| F8 | 安全配置页 | 新组件 | `components/SecurityConfig.vue` |

## 集成步骤

### 1. 复制 API 文件

将 `api/frontEnd.js` 复制到项目的 `web/src/api/frontEnd.js`。

> **注意**: 文件中的 `import request from '@/utils/request'` 假设 axios 实例位于 `@/utils/request`。
> 如果项目实际路径不同（如 `@/utils/http`），请对应修改 import 路径。

### 2. 复制新增组件

将 `components/` 目录下的 5 个 `.vue` 文件复制到对应位置：

| 组件文件 | 目标路径 |
|----------|----------|
| `StorageCardDialog.vue` | `web/src/views/device/common/StorageCardDialog.vue` |
| `DeviceUpgradeDialog.vue` | `web/src/views/device/common/DeviceUpgradeDialog.vue` |
| `QueryResultDialog.vue` | `web/src/views/device/common/QueryResultDialog.vue` |
| `SnapshotConfigDialog.vue` | `web/src/views/device/common/SnapshotConfigDialog.vue` |
| `SecurityConfig.vue` | `web/src/views/operations/securityConfig.vue` |

### 3. 应用补丁修改

按照 `patches/` 目录下的两个补丁说明文件，对现有文件进行增量修改：

- `ptzControls-patch.md` → 修改 `web/src/views/common/ptzControls.vue`
- `deviceList-patch.md` → 修改 `web/src/views/device/list.vue`

### 4. 注册路由

在 `web/src/router/index.js` 中新增安全配置页路由：

```javascript
{
  path: '/operations/security',
  name: 'SecurityConfig',
  component: () => import('@/views/operations/securityConfig.vue'),
  meta: { title: '安全配置', icon: 'lock' }
}
```

### 5. 在父组件中集成对话框

在使用这些对话框的父组件中（如 `web/src/views/device/live/index.vue`）：

```vue
<template>
  <div>
    <!-- 现有内容 -->

    <!-- 存储卡管理 -->
    <storage-card-dialog
      :visible.sync="showStorageCardDialog"
      :device-id="device.deviceId"
      :channel-id="channel.channelId"
      @formatted="onCardFormatted"
    />

    <!-- 设备升级 -->
    <device-upgrade-dialog
      :visible.sync="showUpgradeDialog"
      :device-id="device.deviceId"
      :channel-id="channel.channelId"
      @upgraded="onDeviceUpgraded"
    />

    <!-- 信息查询 -->
    <query-result-dialog
      :visible.sync="showQueryDialog"
      :device-id="device.deviceId"
      :channel-id="channel.channelId"
    />

    <!-- 图像抓拍 -->
    <snapshot-config-dialog
      :visible.sync="showSnapshotDialog"
      :device-id="device.deviceId"
      :channel-id="channel.channelId"
    />
  </div>
</template>

<script>
import StorageCardDialog from '@/views/device/common/StorageCardDialog.vue'
import DeviceUpgradeDialog from '@/views/device/common/DeviceUpgradeDialog.vue'
import QueryResultDialog from '@/views/device/common/QueryResultDialog.vue'
import SnapshotConfigDialog from '@/views/device/common/SnapshotConfigDialog.vue'

export default {
  components: {
    StorageCardDialog,
    DeviceUpgradeDialog,
    QueryResultDialog,
    SnapshotConfigDialog
  }
  // ...
}
</script>
```

### 6. PTZ 控制面板集成

在 `live/index.vue` 的 ptzControls 使用处添加事件监听和版本 prop：

```vue
<ptz-controls
  @ptz-precise="handlePtzPrecise"
  @target-track="handleTargetTrack"
  :is-version2022="device.isVersion2022()"
/>
```

## API 汇总

| API 路径 | 方法 | 对应改造项 | 说明 |
|----------|------|-----------|------|
| `/api/device/control/ptz_precise/{deviceId}/{channelId}` | GET | 改造项7 | PTZ 精准控制 |
| `/api/device/control/format_sdcard/{deviceId}/{channelId}` | POST | 改造项8 | 存储卡格式化 |
| `/api/device/control/target_track/{deviceId}/{channelId}` | GET | 改造项9 | 目标跟踪 |
| `/api/device/control/device_upgrade/{deviceId}/{channelId}` | POST | 改造项10 | 设备软件升级 |
| `/api/device/control/home_position_query/{deviceId}/{channelId}` | GET | 改造项11 | 看守位查询 |
| `/api/device/control/cruise_track_query/{deviceId}/{channelId}` | GET | 改造项12 | 巡航轨迹查询 |
| `/api/device/control/ptz_precise_status_query/{deviceId}/{channelId}` | GET | 改造项13 | PTZ 状态查询 |
| `/api/device/control/storage_card_status_query/{deviceId}/{channelId}` | GET | 改造项14 | 存储卡状态查询 |
| `/api/device/control/snapshot_config/{deviceId}/{channelId}` | POST | 改造项15 | 图像抓拍配置 |
| `/api/device/control/upload_firmware/{deviceId}` | POST | 改造项10配套 | 固件文件上传 |
| `/api/device/config/save_security` | POST | 改造项2-35配套 | 安全配置保存 |

## 注意事项

1. **版本判断**: 所有 2022 新功能通过 `isVersion2022` prop 控制显示，仅 2022 版设备可见
2. **破坏性操作**: 存储卡格式化和设备升级需用户二次确认
3. **Content-Type**: 固件上传使用 FormData，由 axios 自动设置 boundary，不手动指定
4. **sessionId**: 升级和抓拍操作的 sessionId 由前端使用 `crypto.randomUUID()` 生成
5. **错误处理**: 所有 API 调用均有 try-catch 和用户提示
6. **中文注释**: 所有代码包含详细的中文注释，标注来源（后端改造项编号、设计文档章节、2022规范附录编号）
