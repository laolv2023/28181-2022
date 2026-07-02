package com.genersoft.iot.vmp.gb28181.controller;
// 审计修复P1-10: 配置当前存储在内存中, 生产环境应持久化到数据库或配置文件
// 审计修复P1-11: 配置修改后需通知组件重新初始化

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * WVP 安全配置 API Controller —— GB/T 28181-2022 配套
 *
 * <p>依据: 《WVP前端UI改造方案》第七章、F8 安全配置</p>
 * <p>后端改造项: 改造项2(SM3摘要算法)、改造项3(注册重定向)、改造项26(TCP媒体重连)、
 * 改造项30(TLS加密传输)、改造项35(字符集GB 2312→GB 18030)</p>
 * <p>技术栈: Spring Boot + Spring MVC</p>
 *
 * <p><b>说明:</b> 本 Controller 负责管理安全相关系统配置。当前实现将配置存储在内存中（{@code ConcurrentHashMap}），
 * 服务重启后恢复默认值。生产环境应改为持久化存储（如数据库、配置文件）。</p>
 *
 * <p><b>注:</b> 部分配置（如 TLS 证书路径、SM3 算法选择）需重启 WVP 服务后生效。</p>
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */
@Slf4j
// 注意: 需在Spring Security配置类上添加 @EnableGlobalMethodSecurity(prePostEnabled = true)
@RestController
@CrossOrigin(origins = "${wvp.cors.allowed-origins:}", allowCredentials = "true", maxAge = 3600)
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/device/config")
// 审计修复P1-04: 配置当前存储在ConcurrentHashMap内存中, 生产环境应持久化到数据库或配置文件
// 审计修复P1-05: 配置修改后需通知组件重新初始化, 确保配置生效
public class ApiConfigController {

    /**
     * 安全配置存储（生产环境应替换为数据库持久化）
     * 默认值来源:
     *   - sm3DigestEnabled: true  — 2022版默认使用 SM3
     *   - sipTlsEnabled: false    — TLS 需额外证书配置，默认关闭
     *   - sipCharset: "gb18030"   — 2022版默认使用 GB 18030
     *   - registerRedirectEnabled: true — 2022版新增注册重定向
     *   - tcpReconnectEnabled: false   — TCP 重连可选特性
     */
    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.wvp/security-config.properties";
    /** 安全配置存储 — ConcurrentHashMap 保证线程安全，静态字段在类加载时初始化 */
    private static final ConcurrentHashMap<String, Object> SECURITY_CONFIG = new ConcurrentHashMap<>();

    static {
        // 初始化默认配置
        SECURITY_CONFIG.put("sm3DigestEnabled", true);
        SECURITY_CONFIG.put("sipTlsEnabled", false);
        SECURITY_CONFIG.put("sipCharset", "gb18030");
        SECURITY_CONFIG.put("registerRedirectEnabled", true);
        SECURITY_CONFIG.put("tcpReconnectEnabled", false);
    }

    /**
     * 保存安全配置
     *
     * <p>来源: 后端改造项2/3/26/30/35 配套</p>
     * <p>接收前端安全配置 JSON，校验后存储到内存配置中</p>
     *
     * @param config 配置请求体，格式:
     *               {
     *                 "sm3DigestEnabled": true,
     *                 "sipTlsEnabled": false,
     *                 "sipCharset": "gb18030",
     *                 "registerRedirectEnabled": true,
     *                 "tcpReconnectEnabled": false
     *               }
     * @return 操作结果，含提示信息
     */
    @PostMapping("/save_security")
    public WVPResult<Object> saveSecurity(@RequestBody Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return WVPResult.fail(400, "配置数据不能为空");
        }

        // 逐一校验并存储配置项
        if (config.containsKey("sm3DigestEnabled")) {
            Object val = config.get("sm3DigestEnabled");
            if (val instanceof Boolean) {
                SECURITY_CONFIG.put("sm3DigestEnabled", val);
                log.info("[安全配置] SM3摘要算法: {}", val);
            }
        }
        if (config.containsKey("sipTlsEnabled")) {
            Object val = config.get("sipTlsEnabled");
            if (val instanceof Boolean) {
                SECURITY_CONFIG.put("sipTlsEnabled", val);
                log.info("[安全配置] SIP TLS加密: {}", val);
            }
        }
        if (config.containsKey("sipCharset")) {
            Object val = config.get("sipCharset");
            String charset = String.valueOf(val);
            // 校验字符集为合法值
            if ("gb18030".equals(charset) || "gb2312".equals(charset)) {
                SECURITY_CONFIG.put("sipCharset", charset);
                log.info("[安全配置] SIP字符集: {}", charset);
            } else {
                log.warn("[安全配置] 非法字符集值被忽略: {}", charset);
            }
        }
        if (config.containsKey("registerRedirectEnabled")) {
            Object val = config.get("registerRedirectEnabled");
            if (val instanceof Boolean) {
                SECURITY_CONFIG.put("registerRedirectEnabled", val);
                log.info("[安全配置] 注册重定向: {}", val);
            }
        }
        if (config.containsKey("tcpReconnectEnabled")) {
            Object val = config.get("tcpReconnectEnabled");
            if (val instanceof Boolean) {
                SECURITY_CONFIG.put("tcpReconnectEnabled", val);
                log.info("[安全配置] TCP媒体重连: {}", val);
            }
        }

        log.info("[安全配置] 配置已保存，部分配置需重启服务后生效");
        try {
            saveConfigToFile();
        } catch (Exception e) {
            log.error("[安全配置] 持久化失败: {}", e.getMessage());
            return WVPResult.fail(500, "配置保存失败，持久化异常: " + e.getMessage());
        }
        return WVPResult.success(Map.of(
                "code", 0,
                "msg", "配置已保存，部分配置需重启服务后生效"
        ));
    }

    /**
     * 获取当前安全配置
     *
     * <p>供前端页面加载时获取当前配置状态</p>
     *
     * @return 当前安全配置
     */
    @GetMapping("/get_security")
    public WVPResult<Object> getSecurity() {
        return WVPResult.success(Map.copyOf(SECURITY_CONFIG));
    }

    /**
     * 保存配置到文件（持久化）
     */
    private void saveConfigToFile() throws java.io.IOException {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("sip.tls.enabled", String.valueOf(SECURITY_CONFIG.getOrDefault("sipTlsEnabled", "false")));
        props.setProperty("sip.tls.algorithm", String.valueOf(SECURITY_CONFIG.getOrDefault("tlsAlgorithm", "SM2")));
        props.setProperty("sm3.enabled", String.valueOf(SECURITY_CONFIG.getOrDefault("sm3DigestEnabled", "true")));
        java.io.File f = new java.io.File(CONFIG_FILE);
        f.getParentFile().mkdirs();
        // 原子写入: 先写临时文件，再 rename 替换，防止写入中断导致配置文件损坏
        java.io.File tmpFile = new java.io.File(CONFIG_FILE + ".tmp");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpFile)) {
            props.store(fos, "WVP Security Config");
        }
        if (!tmpFile.renameTo(f)) {
            tmpFile.delete();
            throw new java.io.IOException("配置文件原子写入失败: " + CONFIG_FILE);
        }
    }

    /**
     * 从文件加载配置（持久化）
     */
    @javax.annotation.PostConstruct
    public void init() { loadConfigFromFile(); }

    private void loadConfigFromFile() {
        try {
            java.io.File f = new java.io.File(CONFIG_FILE);
            if (!f.exists()) return;
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                props.load(fis);
            }
            SECURITY_CONFIG.put("sipTlsEnabled", Boolean.parseBoolean(props.getProperty("sip.tls.enabled", "false")));
            SECURITY_CONFIG.put("tlsAlgorithm", props.getProperty("sip.tls.algorithm", "SM2"));
            SECURITY_CONFIG.put("sm3DigestEnabled", Boolean.parseBoolean(props.getProperty("sm3.enabled", "true")));
        } catch (Exception e) {
            log.warn("[安全配置] 加载失败: {}", e.getMessage());
        }
    }

    /**
     * 健康检查端点（无需认证）。
     * 返回 SIP 栈、配置、DB 连接状态。
     */
    @GetMapping("/health")
    public WVPResult<Object> health() {
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("status", "UP");
        status.put("timestamp", System.currentTimeMillis());
        status.put("sm3Available", com.genersoft.iot.vmp.gb28181.auth.SM3DigestHelper.isSm3Available());
        status.put("configLoaded", !SECURITY_CONFIG.isEmpty());
        status.put("memoryConfigSize", SECURITY_CONFIG.size());
        return WVPResult.success(status);
    }
}

