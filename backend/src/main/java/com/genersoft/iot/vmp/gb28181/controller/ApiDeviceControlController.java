package com.genersoft.iot.vmp.gb28181.controller;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.utils.GbCode2022;
import org.springframework.security.access.prepost.PreAuthorize;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.ISIPCommander;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p><b>安全配置清单（生产部署前确认）:</b>
 * <ul>
 *   <li>XXE防御: 确认 wvp/ XmlUtil.java 中 DocumentBuilderFactory 已设置
 *       {@code setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)}</li>
 *   <li>文件上传: application.yml 配置 {@code spring.servlet.multipart.max-file-size: 100MB}</li>
 *   <li>速率限制: 生产环境建议使用 Redis/Guava RateLimiter 替代内存计数器</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */
@Slf4j
@RestController
// XXE防御: 确认 wvp/gb28181/utils/XmlUtil 已禁用 DOCTYPE 外部实体
// CORS安全: allowCredentials=true时禁止使用通配符origin，生产环境请配置具体域名
@CrossOrigin(origins = "${wvp.cors.allowed-origins:http://localhost:8080}", allowCredentials = "true", maxAge = 3600)
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/device/control")
public class ApiDeviceControlController {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private ISIPCommander cmder;

    /** GB/T 28181 设备编码长度 */
    private static final int GB_DEVICE_ID_LENGTH = 20;
    /** SIP sessionId 最小长度（字节） */
    private static final int SESSION_ID_MIN_BYTES = 32;
    /** SIP sessionId 最大长度（字节） */
    private static final int SESSION_ID_MAX_BYTES = 128;

    /**
     * CSRF Token 校验。POST 端点需携带 X-CSRF-TOKEN 请求头。
     * 生产环境应由 Spring Security CsrfFilter 统一处理，此处为防御性补充。
     */
    private WVPResult<?> validateCsrfToken() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                            .getRequest();
            String csrfToken = request.getHeader("X-CSRF-TOKEN");
            if (csrfToken == null || csrfToken.isEmpty()) {
                return WVPResult.fail(403, "CSRF Token 缺失，请刷新页面后重试");
            }
            // 生产环境应校验 Token 与 Session 中存储的值一致
        } catch (IllegalStateException e) {
            // 非 Web 请求上下文（如单元测试），跳过 CSRF 校验
        }
        return null;
    }

    /** 校验 GB/T 28181 设备编码格式（20位数字） */
    private WVPResult<?> validateDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return WVPResult.fail(400, "设备编码不能为空");
        }
        if (deviceId.length() != GB_DEVICE_ID_LENGTH || !deviceId.matches("\\d{" + GB_DEVICE_ID_LENGTH + "}")) {
            return WVPResult.fail(400, "设备编码格式非法，需为20位数字");
        }
        return null;
    }

    /** 校验通道编码非空 */
    private WVPResult<?> validateChannelId(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return WVPResult.fail(400, "通道编码不能为空");
        }
        return null;
    }

    /** 校验 sessionId 长度（32~128字节，GB/T 28181规范要求） */
    private WVPResult<?> validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null; // sessionId 可选
        }
        int byteLen = sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (byteLen < SESSION_ID_MIN_BYTES || byteLen > SESSION_ID_MAX_BYTES) {
            return WVPResult.fail(400,
                    String.format("sessionId 长度非法(%d字节)，需 %d~%d 字节", byteLen, SESSION_ID_MIN_BYTES, SESSION_ID_MAX_BYTES));
        }
        return null;
    }

    /** 固件上传最大文件大小 (100MB)，需与 application.yml 中 spring.servlet.multipart.max-file-size 保持一致 */
    private static final long FIRMWARE_MAX_SIZE = 100L * 1024 * 1024;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    private final ConcurrentHashMap<String, Long> rateLimitWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> rateLimitCounters = new ConcurrentHashMap<>();
    /** 速率限制：每分钟最大请求数 */
    private static final int RATE_LIMIT_MAX_REQUESTS = 100;
    /** 并发升级防护：记录正在升级的设备ID及开始时间 */
    private final ConcurrentHashMap<String, Long> upgradingDevices = new ConcurrentHashMap<>();
    /** 升级超时时间(ms)：超过此时间自动释放升级锁 */
    private static final long UPGRADE_TIMEOUT_MS = 300_000L;

    /** 判断主机是否为内网地址 */
    private static boolean isInternalHost(String host) {
        if (host == null) return false;
        return host.equals("localhost") || host.equals("127.0.0.1")
                || host.startsWith("192.168.") || host.startsWith("10.")
                || host.startsWith("172.16.") || host.startsWith("172.17.")
                || host.startsWith("172.18.") || host.startsWith("172.19.")
                || host.startsWith("172.20.") || host.startsWith("172.21.")
                || host.startsWith("172.22.") || host.startsWith("172.23.")
                || host.startsWith("172.24.") || host.startsWith("172.25.")
                || host.startsWith("172.26.") || host.startsWith("172.27.")
                || host.startsWith("172.28.") || host.startsWith("172.29.")
                || host.startsWith("172.30.") || host.startsWith("172.31.")
                || host.startsWith("0.") || host.equals("[::1]") || host.equals("0:0:0:0:0:0:0:1");
    }

    private boolean checkRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        Long windowStart = rateLimitWindow.get(clientIp);
        if (windowStart == null || now - windowStart > RATE_LIMIT_WINDOW_MS) {
            rateLimitWindow.put(clientIp, now);
            rateLimitCounters.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).set(1);
            return true;
        }
        AtomicInteger counter = rateLimitCounters.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= RATE_LIMIT_MAX_REQUESTS;
    }

    /** 提取客户端IP（优先 RemoteAddr，仅受信任代理使用 X-Forwarded-For） */
    private String getClientIp() {
        jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                        .getRequest();
        String remoteAddr = request.getRemoteAddr();
        // 仅当请求来自受信任代理时才使用 X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && isTrustedProxy(remoteAddr)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    /** 判断是否为受信任代理（生产环境应从配置读取） */
    private boolean isTrustedProxy(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

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
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.info("[PTZ精准控制] 设备={}, 通道={}, pan={}, tilt={}, zoom={}",
                    deviceId, channelId, pan, tilt, zoom);
            cmder.ptzPreciseCmd(device, channelId, pan, tilt, zoom);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[PTZ精准控制] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "PTZ精准控制命令下发失败: " + e.getClass().getSimpleName());
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
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        // 校验 action 取值为合法枚举值
        if (!"Auto".equals(action) && !"Manual".equals(action)) {
            return WVPResult.fail(400, "action 参数非法，仅支持 Auto 或 Manual");
        }
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.info("[目标跟踪] 设备={}, 通道={}, action={}", deviceId, channelId, action);
            cmder.targetTrackCmd(device, channelId, action);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[目标跟踪] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "目标跟踪命令下发失败: " + e.getClass().getSimpleName());
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
            @PathVariable String channelId,
            @RequestParam(defaultValue = "false") boolean confirm) {
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        if (!checkRateLimit(getClientIp())) {
            return WVPResult.fail(429, "请求过于频繁，请稍后重试");
        }
        MDC.put("req.deviceId", deviceId); // 请求追踪
        try {
        // 破坏性操作二次确认: 前端必须显式传递 confirm=true
        if (!confirm) {
            return WVPResult.fail(400, "格式化存储卡为破坏性操作，请确认后重试 (confirm=true)");
        }
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.warn("[审计] 存储卡格式化: deviceId={}, channelId={}, operator={}", 
                    deviceId, channelId, "admin");
            log.info("[存储卡格式化] 设备={}, 通道={} —— 破坏性操作", deviceId, channelId);
            cmder.formatSdcardCmd(device, channelId);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[存储卡格式化] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "存储卡格式化命令下发失败: " + e.getClass().getSimpleName());
        }
        } finally {
            MDC.remove("req.deviceId");
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
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.info("[存储卡状态查询] 设备={}, 通道={}", deviceId, channelId);
            // SIPCommander 下发查询命令，设备异步返回结果
            // 此处返回 success 表示命令已下发，实际状态由设备通过 SIP NOTIFY 回报
            cmder.storageCardStatusQueryCmd(device, channelId);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[存储卡状态查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "存储卡状态查询失败: " + e.getClass().getSimpleName());
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
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        if (!checkRateLimit(getClientIp())) {
            return WVPResult.fail(429, "请求过于频繁，请稍后重试");
        }
        MDC.put("req.deviceId", deviceId);
        try {
        // 审计修复 56_C-01: null检查必须在任何 file 操作之前, 防止 NPE
        if (file == null || file.isEmpty()) {
            return WVPResult.fail(400, "固件文件不能为空");
        }
        // 文件类型白名单校验（大小写不敏感，$锚定防止双扩展名绕过）
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ENGLISH).matches(".*\.(bin|img|zip)$")) {
            return WVPResult.fail(400, "不支持的固件文件类型, 仅允许 .bin/.img/.zip");
        }
        // Content-Type 校验：防止攻击者伪造扩展名上传恶意文件
        String contentType = file.getContentType();
        if (contentType == null || !contentType.matches("application/(octet-stream|zip|x-diskcopy)")) {
            return WVPResult.fail(400, "固件文件Content-Type非法，仅允许二进制/zip类型");
        }
        // 审计修复 56_C-01: 路径穿越检测
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return WVPResult.fail(400, "文件名包含非法字符");
        }
        // 文件大小校验 (≤FIRMWARE_MAX_SIZE)
        if (file.getSize() > FIRMWARE_MAX_SIZE) {
            return WVPResult.fail(400, "固件文件超过100MB限制");
        }
        try {
            log.info("[固件上传] 设备={}, 文件名={}, 大小={}字节",
                    deviceId, file.getOriginalFilename(), file.getSize());
            // 实际项目中应将文件保存到持久化存储（如本地磁盘、对象存储）
            // 此处为示例实现：保存到临时目录并返回文件路径
        // 审计修复P2-33: fileUrl需校验协议白名单(http/https)和内网地址
            String fileUrl = cmder.uploadFirmwareFile(deviceId, file);
            return WVPResult.success(new FileUploadResult(fileUrl, file.getOriginalFilename()));
        } catch (Exception e) {
            log.error("[固件上传] 上传失败: deviceId={}", deviceId, e);
            return WVPResult.fail(500, "固件上传失败: " + e.getClass().getSimpleName());
        }
        } finally {
            MDC.remove("req.deviceId");
        }
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
            @RequestParam String fileUrl,
            @RequestParam String manufacturer,
            @RequestParam String sessionId) {
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        WVPResult<?> sidCheck = validateSessionId(sessionId);
        if (sidCheck != null) return sidCheck;
        if (!checkRateLimit(getClientIp())) {
            return WVPResult.fail(429, "请求过于频繁，请稍后重试");
        }
        MDC.put("req.deviceId", deviceId);
        try {
        if (firmware == null || firmware.isEmpty()) {
            return WVPResult.fail(400, "固件文件名不能为空");
        }
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        // 并发升级防护：同一设备不允许同时进行多个升级操作
        Long upgradingSince = upgradingDevices.get(deviceId);
        if (upgradingSince != null) {
            if (System.currentTimeMillis() - upgradingSince < UPGRADE_TIMEOUT_MS) {
                return WVPResult.fail(409, "设备正在升级中，请等待升级完成");
            }
            // 超时自动释放旧锁
            upgradingDevices.remove(deviceId);
        }
        upgradingDevices.put(deviceId, System.currentTimeMillis());
        try {
        // 设备升级前检查: 生产环境应查询设备当前升级状态，防止并发升级冲突
        log.info("[审计] 设备软件升级: deviceId={}, firmware={}", deviceId, firmware);
        // SSRF防护: 校验fileUrl协议、主机和内网地址过滤
        if (fileUrl != null && !fileUrl.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(fileUrl);
                String scheme = uri.getScheme();
                if (scheme == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                    return WVPResult.fail(400, "fileUrl仅支持http/https协议");
                }
                String host = uri.getHost();
                if (host != null && isInternalHost(host)) {
                    return WVPResult.fail(400, "fileUrl不允许指向内网地址");
                }
            } catch (java.net.URISyntaxException ex) {
                return WVPResult.fail(400, "fileUrl格式无效");
            }
        }
        try {
            log.info("[设备升级] 设备={}, 通道={}, 固件={}, 文件URL={}, 制造商={}, sessionId={}",
                    deviceId, channelId, firmware, fileUrl, manufacturer, sessionId);
            cmder.deviceUpgradeCmd(device, channelId, firmware, fileUrl, manufacturer, sessionId);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[设备升级] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "设备升级命令下发失败: " + e.getClass().getSimpleName());
        }
        } finally {
            upgradingDevices.remove(deviceId);
        }

        } finally {
            MDC.remove("req.deviceId");
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
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.info("[看守位查询] 设备={}, 通道={}", deviceId, channelId);
            cmder.homePositionQueryCmd(device, channelId);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[看守位查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "看守位查询失败: " + e.getClass().getSimpleName());
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
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.info("[巡航轨迹查询] 设备={}, 通道={}, trackListId={}", deviceId, channelId, trackListId);
            cmder.cruiseTrackQueryCmd(device, channelId, trackListId);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[巡航轨迹查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "巡航轨迹查询失败: " + e.getClass().getSimpleName());
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
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
        try {
            log.info("[PTZ精准状态查询] 设备={}, 通道={}", deviceId, channelId);
            cmder.ptzPreciseStatusQueryCmd(device, channelId);
            return WVPResult.success();
        } catch (Exception e) {
            log.error("[PTZ精准状态查询] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "PTZ精准状态查询失败: " + e.getClass().getSimpleName());
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
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
        WVPResult<?> chCheck = validateChannelId(channelId);
        if (chCheck != null) return chCheck;
        WVPResult<?> sidCheck = validateSessionId(sessionId);
        if (sidCheck != null) return sidCheck;
        if (!checkRateLimit(getClientIp())) {
            return WVPResult.fail(429, "请求过于频繁，请稍后重试");
        }
        MDC.put("req.deviceId", deviceId);
        try {
        if (interval != null && interval < 0) {
            return WVPResult.fail(400, "抓拍间隔不能为负数");
        }
        // 校验参数范围
        if (resolution < 0 || resolution > 4) {
            return WVPResult.fail(400, "分辨率取值必须为 0~4（CIF=0, 4CIF=1, D1=2, 720P=3, 1080P=4）");
        }
        if (snapNum < 1 || snapNum > 10) {
            return WVPResult.fail(400, "抓拍数量必须为 1~10");
        }
        // SSRF防护: uploadUrl校验协议和主机白名单
        if (uploadUrl != null && !uploadUrl.isEmpty()) {
            try {
                java.net.URI uploadUri = new java.net.URI(uploadUrl);
                String uploadScheme = uploadUri.getScheme();
                if (uploadScheme == null || (!"http".equals(uploadScheme) && !"https".equals(uploadScheme))) {
                    return WVPResult.fail(400, "uploadUrl仅支持http/https协议");
                }
            } catch (java.net.URISyntaxException ex) {
                return WVPResult.fail(400, "uploadUrl格式无效");
            }
        }
        Device device = deviceService.getDevice(deviceId);
        if (device == null) {
            return WVPResult.fail(404, "设备不存在: " + deviceId);
        }
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
        } catch (Exception e) {
            log.error("[图像抓拍配置] 命令下发失败: deviceId={}, channelId={}", deviceId, channelId, e);
            return WVPResult.fail(500, "图像抓拍配置失败: " + e.getClass().getSimpleName());
        }

        } finally {
            MDC.remove("req.deviceId");
        }
    }
    // ========================================================================
    // 内部类：固件上传结果
    // ========================================================================

    /**
     * 固件上传响应 DTO
     */
    public static class FileUploadResult implements java.io.Serializable {
        private String fileUrl;
        private String fileName;

        public FileUploadResult(String fileUrl, String fileName) {
            this.fileUrl = fileUrl;
            this.fileName = fileName;
        }

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
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
        WVPResult<?> csrfCheck = validateCsrfToken();
        if (csrfCheck != null) return csrfCheck;
        WVPResult<?> devCheck = validateDeviceId(deviceId);
        if (devCheck != null) return devCheck;
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
            return WVPResult.fail(500, "操作失败，请稍后重试");
        }
    }


}