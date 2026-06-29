# WVP 后端 Controller —— GB/T 28181-2022 配套

## 概述

本目录包含 WVP-GB28181-pro 前端 UI 升级改造所需的后端 HTTP API Controller 代码。

**前端配套**: `ui/` 目录
**依据**: 《WVP前端UI改造方案》第七章、《WVP合规性升级改造开发方案》

## 目录结构

```
backend/src/main/java/com/genersoft/iot/vmp/gb28181/
├── controller/
│   ├── ApiDeviceControlController.java  ← 10个设备控制/查询端点
│   └── ApiConfigController.java         ← 安全配置端点
└── transmit/cmd/
    └── SIPCommanderSupplement.java      ← SIPCommander 需新增的10个方法签名
```

## API 清单

| 端点 | 方法 | 文件 |
|------|------|------|
| `/api/device/control/ptz_precise/{deviceId}/{channelId}` | GET | ApiDeviceControlController |
| `/api/device/control/target_track/{deviceId}/{channelId}` | GET | ApiDeviceControlController |
| `/api/device/control/format_sdcard/{deviceId}/{channelId}` | POST | ApiDeviceControlController |
| `/api/device/control/device_upgrade/{deviceId}/{channelId}` | POST | ApiDeviceControlController |
| `/api/device/control/upload_firmware/{deviceId}` | POST | ApiDeviceControlController |
| `/api/device/control/home_position_query/{deviceId}/{channelId}` | GET | ApiDeviceControlController |
| `/api/device/control/cruise_track_query/{deviceId}/{channelId}` | GET | ApiDeviceControlController |
| `/api/device/control/ptz_precise_status_query/{deviceId}/{channelId}` | GET | ApiDeviceControlController |
| `/api/device/control/storage_card_status_query/{deviceId}/{channelId}` | GET | ApiDeviceControlController |
| `/api/device/control/snapshot_config/{deviceId}/{channelId}` | POST | ApiDeviceControlController |
| `/api/device/config/save_security` | POST | ApiConfigController |

## 集成步骤

### 1. 复制 Controller 文件

将 `controller/` 目录下的两个文件复制到 WVP 项目对应位置：

```
src/main/java/com/genersoft/iot/vmp/gb28181/controller/
├── ApiDeviceControlController.java
└── ApiConfigController.java
```

### 2. 在 ISIPCommander 接口中添加方法签名

`SIPCommanderSupplement.java` 中定义了 10 个需要实现的方法签名。将这些方法添加到：

- `ISIPCommander.java` 接口中
- `SIPCommander.java` 实现类中

### 3. 实现 ISIPCommander 方法

每个方法内部需要：
1. 构造 GB/T 28181-2022 规范的 XML 消息体
2. 通过 JAIN-SIP 的 `SipSender` 将 MESSAGE 下发到设备
3. 查询类命令异步等待设备 NOTIFY 响应

### 4. 配置 Spring Boot 路由

如果前端请求路径 `/api/device/control/` 与后端 `@RequestMapping` 不匹配，需要检查：

- `server.servlet.context-path` 配置
- 网关/反向代理的路径映射
- 或在前端 `frontEnd.js` 中调整 API 前缀

## 注意事项

1. **WVPResult**: 代码中使用 `WVPResult.fail()` / `WVPResult.success()`，需确认项目中的实际类名（可能是 `Result`、`BaseResult` 等）
2. **IDeviceService**: 假设存在 `getDevice(String deviceId)` 方法，请根据实际接口调整
3. **安全配置**: `ApiConfigController` 当前使用内存存储，生产环境应改为数据库持久化
4. **参数校验**: Controller 层包含基本参数范围校验，SIPCommander 层应再次校验
5. **日志记录**: 所有端点均包含操作日志（log.info）和错误日志（log.error）
6. **异常处理**: 所有方法均有 try-catch 并返回结构化错误信息
