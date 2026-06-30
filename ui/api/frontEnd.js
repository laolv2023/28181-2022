/**
 * WVP 前端 API 扩展 —— GB/T 28181-2022 配套接口
 *
 * 依据: 《WVP前端UI改造方案》第四节 API 汇总
 * 后端改造项覆盖: 改造项7~15, 改造项2/3/26/30/35
 * 技术栈: axios ^0.24.0
 *
 * 使用方法:
 *   import { ptzPrecise, formatStorageCard, ... } from '@/api/frontEnd'
 *
 * 注意: 本文件依赖项目已有的 axios request 实例（通常位于 @/utils/request）
 *       如果项目实际路径不同，请对应修改 import 路径。
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */

import request from '@/utils/request'

// ============================================================================
// 一、PTZ 精准控制（改造项7，A.2.3.1.11）
// ============================================================================

/**
 * PTZ 精准控制
 * 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11
 * 规范要求: 2022版新增PTZ精准控制命令（XML元素名 PTzPrecisectrl），
 *           支持通过 pan/Tilt/zoom 精确设置云台角度和变倍
 *
 * @param {Object} params - 请求参数
 * @param {string} params.deviceId - 设备编码（20位）
 * @param {string} params.channelId - 通道编码
 * @param {number} params.pan       - 水平转角（度），范围 -180.00 ~ 180.00，精度 0.01
 * @param {number} params.tilt      - 垂直转角（度），范围 -90.00 ~ 90.00，精度 0.01
 * @param {number} params.zoom      - 变倍倍数，范围 0 ~ 20，精度 0.1
 * @returns {Promise} 后端响应
 */
export function ptzPrecise({ deviceId, channelId, pan, tilt, zoom } = {}) {
  if (!deviceId || !channelId) throw new Error('[ptzPrecise] deviceId 和 channelId 为必填参数')
  return request({
    method: 'get',
    url: `/api/device/control/ptz_precise/${deviceId}/${channelId}`,
    params: { pan: pan ?? null, tilt: tilt ?? null, zoom: zoom ?? null },
    timeout: 10000  // PTZ 控制命令 10s 超时
  })
}

// ============================================================================
// 二、目标跟踪（改造项9，A.2.3.1.14）
// ============================================================================

/**
 * 目标跟踪控制
 * 来源: 后端改造项9, 设计文档第10.1节, 2022版A.2.3.1.14
 * 规范要求: 2022版新增目标跟踪命令（XML元素名 TargetTrack），
 *           action 取值 Auto（自动跟踪）/ Manual（手动跟踪）
 *           停止跟踪由后端超时自动处理，非规范定义的 action 值
 *
 * @param {Object} params
 * @param {string} params.deviceId  - 设备编码
 * @param {string} params.channelId - 通道编码
 * @param {string} params.action    - 跟踪模式，'Auto'（自动跟踪）或 'Manual'（手动跟踪）
 * @returns {Promise}
 */
export function targetTrack({ deviceId, channelId, action } = {}) {
  if (!deviceId || !channelId) throw new Error('[targetTrack] deviceId 和 channelId 为必填参数')
  if (!['Auto', 'Manual'].includes(action)) throw new Error(`[targetTrack] action 必须为 Auto 或 Manual，实际值: ${action}`)
  return request({
    method: 'get',
    url: `/api/device/control/target_track/${deviceId}/${channelId}`,
    params: { action },
    timeout: 10000
  })
}

// ============================================================================
// 三、存储卡管理（改造项8 格式化 + 改造项14 状态查询）
// ============================================================================

/**
 * 存储卡状态查询
 * 来源: 后端改造项14, 设计文档第11.1节, 2022版A.2.4.14
 * 规范要求: 2022版新增存储卡状态查询命令（CmdType=SDcardStatus），
 *           返回存储卡是否存在、总容量、剩余容量
 *
 * @param {string} deviceId  - 设备编码
 * @param {string} channelId - 通道编码
 * @returns {Promise} 响应包含 storageCard 对象 { status, capacity, remainCapacity }
 */
export function queryStorageCardStatus(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/storage_card_status_query/${deviceId}/${channelId}`,
    timeout: 15000
  })
}

/**
 * 存储卡格式化
 * 来源: 后端改造项8, 设计文档第10.1节, 2022版A.2.3.1.13
 * 规范要求: 2022版新增存储卡格式化命令（CmdType=DeviceControl → 内层 FormatSdcard）
 *           本操作为破坏性操作，使用 POST 方法
 *
 * @param {string} deviceId  - 设备编码
 * @param {string} channelId - 通道编码
 * @returns {Promise}
 */
export function formatStorageCard(deviceId, channelId) {
  return request({
    method: 'post',
    url: `/api/device/control/format_sdcard/${deviceId}/${channelId}`,
    timeout: 30000
  })
}

// ============================================================================
// 四、设备软件升级（改造项10，A.2.3.1.12）
// ============================================================================

/**
 * 设备软件升级命令
 * 来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12
 * 规范要求: 2022版新增设备软件升级命令（XML元素名 Deviceupgrade），
 *           升级文件需先通过 uploadFirmware 上传获取 fileUrl
 *
 * @param {Object} params
 * @param {string} params.deviceId     - 设备编码
 * @param {string} params.channelId    - 通道编码
 * @param {string} params.firmware     - 固件文件名（如 "IPC_V3.0.1.bin"）
 * @param {string} params.fileUrl      - 固件文件在服务器上的可访问 URL（由 uploadFirmware 返回）
 * @param {string} params.manufacturer - 设备制造商信息
 * @param {string} params.sessionId    - 会话 ID（32~128字节，前端通过 crypto.randomUUID() 生成）
 * @returns {Promise}
 */
export function deviceUpgrade({ deviceId, channelId, firmware, fileUrl, manufacturer, sessionId }) {
  return request({
    method: 'post',
    url: `/api/device/control/device_upgrade/${deviceId}/${channelId}`,
    data: { firmware, fileUrl, manufacturer, sessionId },
    timeout: 30000
  })
}

/**
 * 固件文件上传
 * 来源: 后端改造项10配套, 设计文档第10.1节
 * 规范说明: 设备升级前需先将固件文件上传至服务器，获取服务器端可访问的文件 URL
 *           注意: 不要手动设置 Content-Type，由 axios 自动设置 multipart/form-data 的 boundary
 *
 * @param {string} deviceId - 设备编码
 * @param {File}   file     - 固件文件对象（来自 <input type="file"> 或 el-upload）
 * @returns {Promise} 响应包含 { fileUrl: "http://..." }
 */
export function uploadFirmware(deviceId, file) {
  if (!file || !(file instanceof File)) {
    return Promise.reject(new Error('[uploadFirmware] 无效的文件对象'))
  }
  if (file.size === 0) {
    return Promise.reject(new Error('[uploadFirmware] 不允许上传空文件'))
  }
  const formData = new FormData()
  formData.append('file', file)
  return request({
    method: 'post',
    url: `/api/device/control/upload_firmware/${deviceId}`,
    data: formData,
    timeout: 120000  // 固件上传 120s 超时（大文件场景）
    // 注意: 不使用手动 Content-Type，由 axios 自动设置 boundary，确保 multipart 正确解析
  })
}

// ============================================================================
// 五、2022版新增查询（改造项11~13）
// ============================================================================

/**
 * 看守位信息查询
 * 来源: 后端改造项11, 设计文档第11.1节, 2022版A.2.4.10
 * 规范要求: 查询设备当前看守位配置状态，返回是否启用、预置位编号、复位时间
 *
 * @param {string} deviceId  - 设备编码
 * @param {string} channelId - 通道编码
 * @returns {Promise} 响应包含 HomePosition 对象 { enabled, presetIndex, resetTime }
 */
export function queryHomePosition(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/home_position_query/${deviceId}/${channelId}`,
    timeout: 15000
  })
}

/**
 * 巡航轨迹查询
 * 来源: 后端改造项12, 设计文档第11.1节, 2022版A.2.4.11
 * 规范要求: 查询设备巡航轨迹列表或单条轨迹详情，CmdType=cruiseTrackQuery
 *
 * @param {string}  deviceId    - 设备编码
 * @param {string}  channelId   - 通道编码
 * @param {number} [trackListId] - 巡航轨迹列表 ID（可选，不传则查询所有）
 * @returns {Promise} 响应包含 CruiseTrackList 数组
 */
export function queryCruiseTrack(deviceId, channelId, trackListId) {
  return request({
    method: 'get',
    url: `/api/device/control/cruise_track_query/${deviceId}/${channelId}`,
    params: { trackListId },
    timeout: 15000
  })
}

/**
 * PTZ 精准状态查询
 * 来源: 后端改造项13, 设计文档第11.1节, 2022版A.2.4.13
 * 规范要求: 查询设备当前 PTZ 精准状态，返回水平/垂直角度、变倍值，CmdType=pTZposition
 *
 * @param {string} deviceId  - 设备编码
 * @param {string} channelId - 通道编码
 * @returns {Promise} 响应包含 { pan, tilt, zoom }
 */
export function queryPtzPreciseStatus(deviceId, channelId) {
  return request({
    method: 'get',
    url: `/api/device/control/ptz_precise_status_query/${deviceId}/${channelId}`,
    timeout: 15000
  })
}

// ============================================================================
// 六、图像抓拍配置（改造项15，2022版9.14）
// ============================================================================

/**
 * 图像抓拍配置
 * 来源: 后端改造项15, 设计文档第12.2节, 2022版9.14
 * 规范要求: 向设备下发抓拍参数配置，包括分辨率、抓拍数量、抓拍间隔、上传路径和会话ID
 *           resolution 枚举值由后端 SnapshotConfigMessageHandler 定义（0=CIF, 1=4CIF, 2=D1, 3=720P, 4=1080P）
 *
 * @param {Object}  params
 * @param {string}  params.deviceId   - 设备编码
 * @param {string}  params.channelId  - 通道编码
 * @param {number}  params.resolution - 分辨率枚举值（0~4）
 * @param {number}  params.snapNum    - 连拍张数（1~10）
 * @param {number} [params.interval]  - 抓拍间隔时间（秒，可选）
 * @param {string} [params.uploadUrl] - 抓拍图像上传路径（可选，不传由后端使用默认值）
 * @param {string} [params.sessionId] - 会话 ID（可选，不传由前端自动生成 UUID）
 * @returns {Promise}
 */
export function snapshotConfig({ deviceId, channelId, resolution, snapNum, interval, uploadUrl, sessionId }) {
  return request({
    method: 'post',
    url: `/api/device/control/snapshot_config/${deviceId}/${channelId}`,
    data: { resolution, snapNum, interval, uploadUrl, sessionId },
    timeout: 15000
  })
}

// ============================================================================
// 七、安全配置（改造项2/3/26/30/35 配套）
// ============================================================================

/**
 * 保存安全配置
 * 来源: 后端改造项2(SM3), 改造项3(注册重定向), 改造项26(TCP重连),
 *        改造项30(TLS), 改造项35(字符集) 配套
 *
 * @param {Object} config - 安全配置对象
 * @param {boolean} config.sm3DigestEnabled      - 是否启用 SM3 摘要算法
 * @param {boolean} config.sipTlsEnabled          - 是否启用 SIP TLS 加密
 * @param {string}  config.sipCharset             - SIP 信令字符集，'gb18030' 或 'gb2312'
 * @param {boolean} config.registerRedirectEnabled - 是否启用注册重定向
 * @param {boolean} config.tcpReconnectEnabled     - 是否启用 TCP 媒体重连
 * @returns {Promise}
 */
export function saveSecurityConfig(config) {
  return request({
    method: 'post',
    url: '/api/device/config/save_security',
    data: config,
    timeout: 10000
  })
}
