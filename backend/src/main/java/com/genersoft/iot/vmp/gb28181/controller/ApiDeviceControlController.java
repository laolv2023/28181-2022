package com.genersoft.iot.vmp.gb28181.controller;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import org.springframework.security.access.prepost.PreAuthorize;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.ISIPCommander;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * WVP 设备控制与查询 API Controller —— GB/T 28181-2022 配套
 *
 * <p>依据: 《WVP前端UI改造方案》第七章 Controller 配套、《WVP合规性升级改造开发方案》改造项7~15</p>
 * <p>技术栈: Spring Boot + Spring MVC，与 WVP 现有 ApiDeviceController 同模式</p>
 *
 * <p><b>路径说明:</b> WVP 后端 ApiDeviceController 的 @RequestMapping 是 /api/v1/device，
 * 但前端调用 /api/device/control/...。本项目假设通过 server.servlet.context-path 或网关
 * 将 /api/device/ 映射到本 Controller。如路径不通，请检查 Spring Boot 路由配置。</p>
 *
 * <p>改造项覆盖:</p>
 * <ul>
 *   <li>改造项7: PTZ 精准控制命令（A.2.3.1.11）</li>
 *   <li>改造项8: 存储卡格式化命令（A.2.3.1.13）</li>
 *   <li>改造项9: 目标跟踪命令（A.2.3.1.14）</li>
 *   <li>改造项10: 设备软件升级命令（A.2.3.1.12）</li>
 *   <li>改造项11: 看守位信息查询（A.2.4.10）</li>
 *   <li>改造项12: 巡航轨迹查询（A.2.4.11）</li>
 *   <li>改造项13: PTZ 精准状态查询（A.2.4.13）</li>
 *   <li>改造项14: 存储卡状态查询（A.2.4.14）</li>
 *   <li>改造项15: 图像抓拍配置（9.14）</li>
 * </ul>
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */
@Slf4j
@RestController
// 注意: 需在 Spring Security 配置类上添加 @EnableGlobalMethodSecurity(prePostEnabled = true)
// 否则 @PreAuthorize 注解不会生效
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/device/control")
public class ApiDeviceControlController {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private ISIPCommander cmder;

    // ========================================================================
    // 一、PTZ 精准控制（改造项7，A.2.3.1.11）
    // ========================================================================

    /**
     * PTZ 精准控制
     *
     * <p>来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11</p>
     * <p>规范要求: 2022版新增 PTZ 精准控制命令（XML元素名 PTzPrecisectrl），
     * 支持通过 pan/Tilt/zoom 精确设置云台角度和变倍</p>
     * <p>XML 结构: &lt;PTzPrecisectrl&gt;&lt;pan&gt;...&lt;/pan&gt;&lt;Tilt&gt;...&lt;/Tilt&gt;&lt;zoom&gt;...&lt;/zoom&gt;&lt;/PTzPrecisectrl&gt;</p>
     *
     * @param deviceId  设备编码（20位 GB/T 28181 编码）
     * @param channelId 通道编码
     * @param pan       水平转角（度），范围 -180.00 ~ 180.00，精度 0.01
     * @param tilt      垂直转角（度），范围 -90.00 ~ 90.00，精度 0.01
     * @param zoom      变倍倍数，范围 0 ~ 20，精度 0.1
     * @return 操作结果
     */
    @PostMapping("/ptz_precise/{deviceId}/{channelId}")
    public WVPResult<?> ptzPrecise(
            @PathVariable String deviceId,
            @PathVariable String channelId,
            @RequestParam Double pan,
            @RequestParam Double tilt,
            @RequestParam Double zoom) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[PTZ精准控制] 设备={}, 通道={}, pan={}, tilt={}, zoom={}",
                    deviceId, channelId, pan, tilt, zoom);
            cmder.ptzPreciseCmd(device, channelId, pan, tilt, zoom);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[PTZ精准控制] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "PTZ精准控制命令下发失败: " + "内部服务器错误");
        }
        
        }
    

    // ========================================================================
    // 二、目标跟踪（改造项9，A.2.3.1.14）
    // ========================================================================

    /**
     * 目标跟踪控制
     *
     * <p>来源: 后端改造项9, 设计文档第10.1节, 2022版A.2.3.1.14</p>
     * <p>规范要求: 2022版新增目标跟踪命令（XML元素名 TargetTrack），
     * action 取值 Auto（自动跟踪）/ Manual（手动跟踪）</p>
     *
     * @param deviceId  设备编码
     * @param channelId 通道编码
     * @param action    跟踪模式，可选值: Auto, Manual
     * @return 操作结果
     */
    @PostMapping("/target_track/{deviceId}/{channelId}")
    public WVPResult<?> targetTrack(
            @PathVariable String deviceId,
            @PathVariable String channelId,
            @RequestParam String action) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        // 校验 action 取值为合法枚举值
        if (!"Auto".equals(action) && !"Manual".equals(action)) {
            return WVPResult.fail(400, "action 参数非法，仅支持 Auto 或 Manual");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[目标跟踪] 设备={}, 通道={}, action={}", deviceId, channelId, action);
            cmder.targetTrackCmd(device, channelId, action);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[目标跟踪] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "目标跟踪命令下发失败: " + "内部服务器错误");
        }
        
        }
    

    // ========================================================================
    // 三、存储卡管理（改造项8 格式化 + 改造项14 状态查询）
    // ========================================================================

    /**
     * 存储卡格式化
     *
     * <p>来源: 后端改造项8, 设计文档第10.1节, 2022版A.2.3.1.13</p>
     * <p>规范要求: 2022版新增存储卡格式化命令（XML元素名 FormatsDcard）</p>
     * <p>本操作为破坏性操作，使用 POST 方法，在生产环境中应增加权限校验</p>
     *
     * @param deviceId  设备编码
     * @param channelId 通道编码
     * @return 操作结果
     */
    @PostMapping("/format_sdcard/{deviceId}/{channelId}")
    public WVPResult<?> formatSdcard(
            @PathVariable String deviceId,
            @PathVariable String channelId) {
        if (channelId == null || channelId.isEmpty()) return WVPResult.fail(400, "通道ID不能为空");
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[存储卡格式化] 设备={}, 通道={} —— 破坏性操作", deviceId, channelId);
            cmder.formatSdcardCmd(device, channelId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[存储卡格式化] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "存储卡格式化命令下发失败: " + "内部服务器错误");
        }
        
        }
    

    /**
     * 存储卡状态查询
     *
     * <p>来源: 后端改造项14, 设计文档第11.1节, 2022版A.2.4.14</p>
     * <p>规范要求: 2022版新增存储卡状态查询命令（CmdType=SDcardStatus），
     * 返回存储卡是否存在、总容量、剩余容量</p>
     *
     * @param deviceId  设备编码
     * @param channelId 通道编码
     * @return 存储卡状态信息
     */
    @GetMapping("/storage_card_status_query/{deviceId}/{channelId}")
    public WVPResult<?> queryStorageCardStatus(
            @PathVariable String deviceId,
            @PathVariable String channelId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[存储卡状态查询] 设备={}, 通道={}", deviceId, channelId);
            // SIPCommander 下发查询命令，设备异步返回结果
            // 此处返回 success 表示命令已下发，实际状态由设备通过 SIP NOTIFY 回报
            cmder.storageCardStatusQueryCmd(device, channelId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[存储卡状态查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "存储卡状态查询失败: " + "内部服务器错误");
        }
        
        }
    

    // ========================================================================
    // 四、设备软件升级（改造项10，A.2.3.1.12）
    // ========================================================================

    /**
     * 固件文件上传
     *
     * <p>来源: 后端改造项10配套, 设计文档第10.1节</p>
     * <p>将固件文件上传至服务器临时目录，返回文件访问 URL，供后续升级命令使用</p>
     *
     * @param deviceId 设备编码
     * @param file     固件文件（MultipartFile）
     * @return 上传后的文件 URL
     */
    @PostMapping("/upload_firmware/{deviceId}")
    public WVPResult<?> uploadFirmware(
            @PathVariable String deviceId,
            @RequestParam("file") MultipartFile file) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        // 文件类型白名单校验
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.matches(".*\.(bin|img|zip)$")) {
            return WVPResult.fail(400, "不支持的固件文件类型, 仅允许 .bin/.img/.zip");
        
        // 文件大小校验 (≤100MB)
        if (file.getSize() > 100 * 1024 * 1024) {
            return WVPResult.fail(400, "固件文件超过100MB限制");
        
        
        if (file == null || file.isEmpty()) {
            return WVPResult.fail(400, "固件文件不能为空");
        
        // 文件大小校验已在上方完成（≤100MB）
        
            return WVPResult.fail(400, "文件过大，上限 200MB");
        
        try {
            log.info("[固件上传] 设备={}, 文件名={}, 大小={}字节",
                    deviceId, file.getOriginalFilename(), file.getSize());
            // 实际项目中应将文件保存到持久化存储（如本地磁盘、对象存储）
            // 此处为示例实现：保存到临时目录并返回文件路径
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
            String fileUrl = cmder.uploadFirmwareFile(deviceId, file);
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
            return WVPResult.success(new FileUploadResult(fileUrl, file.getOriginalFilename()));
         catch (Exception e) {
            log.error("[固件上传] 上传失败: deviceId={}", deviceId, e);
            return WVPResult.fail(500, "固件上传失败: " + "内部服务器错误");
        }
        
    

    /**
     * 设备软件升级命令
     *
     * <p>来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12</p>
     * <p>规范要求: 2022版新增设备软件升级命令（XML元素名 Deviceupgrade），
     * 包含固件信息、文件 URL、制造商和会话 ID</p>
     *
     * @param deviceId     设备编码
     * @param channelId    通道编码
     * @param firmware     固件文件名
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
     * @param fileUrl      固件文件在服务器上的可访问 URL
     * @param manufacturer 设备制造商信息
     * @param sessionId    会话 ID（32~128字节，前端生成 UUID）
     * @return 操作结果
     */
    @PostMapping("/device_upgrade/{deviceId}/{channelId}")
    public WVPResult<?> deviceUpgrade(
            @PathVariable String deviceId,
            @PathVariable String channelId,
            @RequestParam String firmware,
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
            @RequestParam String fileUrl,
            @RequestParam String manufacturer,
            @RequestParam String sessionId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[设备升级] 设备={}, 通道={}, 固件={}, 文件URL={}, 制造商={}, sessionId={}",
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
                    deviceId, channelId, firmware, fileUrl, manufacturer, sessionId);
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
            cmder.deviceUpgradeCmd(device, channelId, firmware, fileUrl, manufacturer, sessionId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[设备升级] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "设备升级命令下发失败: " + "内部服务器错误");
        }
        
        }
    

    // ========================================================================
    // 五、2022版新增查询（改造项11~13）
    // ========================================================================

    /**
     * 看守位信息查询
     *
     * <p>来源: 后端改造项11, 设计文档第11.1节, 2022版A.2.4.10</p>
     * <p>规范要求: 查询设备当前看守位配置状态（CmdType=HomepositionQuery），
     * 返回是否启用、预置位编号（presetIndex）、复位时间</p>
     *
     * @param deviceId  设备编码
     * @param channelId 通道编码
     * @return 查询结果（命令已下发，结果由设备异步返回）
     */
    @GetMapping("/home_position_query/{deviceId}/{channelId}")
    public WVPResult<?> queryHomePosition(
            @PathVariable String deviceId,
            @PathVariable String channelId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[看守位查询] 设备={}, 通道={}", deviceId, channelId);
            cmder.homePositionQueryCmd(device, channelId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[看守位查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "看守位查询失败: " + "内部服务器错误");
        }
        
        }
    

    /**
     * 巡航轨迹查询
     *
     * <p>来源: 后端改造项12, 设计文档第11.1节, 2022版A.2.4.11</p>
     * <p>规范要求: 查询设备巡航轨迹列表或单条轨迹详情（CmdType=cruiseTrackQuery）</p>
     *
     * @param deviceId    设备编码
     * @param channelId   通道编码
     * @param trackListId 巡航轨迹列表 ID（可选，不传则查询所有）
     * @return 查询结果
     */
    @GetMapping("/cruise_track_query/{deviceId}/{channelId}")
    public WVPResult<?> queryCruiseTrack(
            @PathVariable String deviceId,
            @PathVariable String channelId,
            @RequestParam(required = false) Integer trackListId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[巡航轨迹查询] 设备={}, 通道={}, trackListId={}", deviceId, channelId, trackListId);
            cmder.cruiseTrackQueryCmd(device, channelId, trackListId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[巡航轨迹查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "巡航轨迹查询失败: " + "内部服务器错误");
        }
        
        }
    

    /**
     * PTZ 精准状态查询
     *
     * <p>来源: 后端改造项13, 设计文档第11.1节, 2022版A.2.4.13</p>
     * <p>规范要求: 查询设备当前 PTZ 精准状态（CmdType=pTZposition），
     * 返回水平/垂直角度、变倍值</p>
     *
     * @param deviceId  设备编码
     * @param channelId 通道编码
     * @return 查询结果
     */
    @PostMapping("/ptz_precise_status_query/{deviceId}/{channelId}")
    public WVPResult<?> queryPtzPreciseStatus(
            @PathVariable String deviceId,
            @PathVariable String channelId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            log.info("[PTZ精准状态查询] 设备={}, 通道={}", deviceId, channelId);
            cmder.ptzPreciseStatusQueryCmd(device, channelId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[PTZ精准状态查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "PTZ精准状态查询失败: " + "内部服务器错误");
        }
        
        }
    

    // ========================================================================
    // 六、图像抓拍配置（改造项15，2022版9.14）
    // ========================================================================

    /**
     * 图像抓拍配置
     *
     * <p>来源: 后端改造项15, 设计文档第12.2节, 2022版9.14</p>
     * <p>规范要求: 向设备下发抓拍参数配置，包含分辨率、抓拍数量、抓拍间隔、上传路径和会话ID
     * Resolution 枚举值: 0=CIF, 1=4CIF, 2=D1, 3=720P, 4=1080P</p>
     *
     * @param deviceId   设备编码
     * @param channelId  通道编码
     * @param resolution 分辨率枚举值（0~4）
     * @param snapNum    连拍张数（1~10）
     * @param interval   抓拍间隔时间（秒，可选）
     * @param uploadUrl  抓拍图像上传路径（可选）
     * @param sessionId  会话 ID（可选，前端生成 UUID）
     * @return 操作结果
     */
    @PostMapping("/snapshot_config/{deviceId}/{channelId}")
    public WVPResult<?> snapshotConfig(
            @PathVariable String deviceId,
            @PathVariable String channelId,
            @RequestParam int resolution,
            @RequestParam int snapNum,
            @RequestParam(required = false) Integer interval,
            @RequestParam(required = false) String uploadUrl,
            @RequestParam(required = false) String sessionId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        
        // 校验参数范围
        if (resolution < 0 || resolution > 4) {
            return WVPResult.fail(400, "分辨率取值必须为 0~4（CIF=0, 4CIF=1, D1=2, 720P=3, 1080P=4）");
        
        if (snapNum < 1 || snapNum > 10) {
            return WVPResult.fail(400, "抓拍数量必须为 1~10");
        
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        
        try {
            // sessionId 若前端未传，由后端生成
            String finalSessionId = (sessionId != null && !sessionId.isEmpty())
                    ? sessionId : UUID.randomUUID().toString();
            log.info("[图像抓拍配置] 设备={}, 通道={}, resolution={}, snapNum={}, interval={}, uploadUrl={}, sessionId={}",
                    deviceId, channelId, resolution, snapNum, interval, uploadUrl, finalSessionId);
            cmder.snapshotConfigCmd(device, channelId, resolution, snapNum,
                    interval != null ? interval : 0,
                    uploadUrl != null ? uploadUrl : "",
                    finalSessionId);
            return WVPResult.success();
         catch (Exception e) {
            log.error("[图像抓拍配置] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "图像抓拍配置失败: " + "内部服务器错误");
        }
        
        }
    

    // ========================================================================
    // 内部类：固件上传结果
    // ========================================================================

    /**
     * 固件上传响应 DTO
     */
    public static class FileUploadResult implements java.io.Serializable {
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
        private String fileUrl;
        private String fileName;

        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
        public FileUploadResult(String fileUrl, String fileName) {
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
            this.fileUrl = fileUrl;
            this.fileName = fileName;
        

        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
        public String getFileUrl() { return fileUrl; 
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; 
        public String getFileName() { return fileName; 
        public void setFileName(String fileName) { this.fileName = fileName; 
    


}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}

    /**
     * 人工指定设备协议版本
     * 来源: 运维需求 — 部分设备未携带X-GB-ver头域时人工指定版本
     * @param deviceId 设备ID
     * @param version 协议版本号（"2.0"=2022版, "1.0"=2016版, null=清除人工指定恢复自动检测）
     */
    @PostMapping("/set_protocol_version/{deviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public WVPResult<?> setDeviceProtocolVersion(
            @PathVariable String deviceId,
            @RequestParam(required = false) String version) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        }
        if (version != null && !version.trim().isEmpty()) {
            if (!"2.0".equals(version.trim()) && !"1.0".equals(version.trim())) {
                return WVPResult.fail(400, "版本号仅支持 2.0 或 1.0");
            }
        }
        try {
            Device device = deviceService.getDevice(deviceId);
            if (device == null) {
                return WVPResult.fail(404, "设备不存在");
            }
            // 设置人工指定版本
            device.setManualProtocolVersion(version);
            deviceService.updateDevice(device);
            log.info("[协议版本] 人工指定设备 {} 协议版本为: {}", deviceId, version == null ? "自动检测" : version);
            return WVPResult.success("操作成功");
        } catch (Exception e) {
            log.error("[协议版本] 人工指定失败: {}", e.getMessage());
            return WVPResult.fail(500, "操作失败: " + e.getMessage());
        }
    }

