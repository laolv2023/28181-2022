package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.header.Header;
import javax.sip.header.ExtensionHeader;
import javax.sip.message.Request;
import javax.sip.SipFactory;

/**
 * GB/T 28181 协议版本协商工具类
 * <p>
 * 改造项1：协议版本协商（X-GB-ver 头部）
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第4节（X-GB-ver 头部协商机制），2022版 7.1 协议版本协商。<br>
 * 协议要求：所有 SIP 请求/响应消息应携带 X-GB-ver 头部，用于版本协商与回退。
 * </p>
 * <p>
 * 版本号格式：主版本.次版本，例如 "2.0" 表示 GB/T 28181-2022，"1.0" 表示 GB/T 28181-2016。
 * </p>
 *
 * @author wvp-upgrade
 */
public final class GBProtocolVersionHelper {

    private static final Logger logger = LoggerFactory.getLogger(GBProtocolVersionHelper.class);

    /**
     * X-GB-ver 头部名称
     */
    public static final String HEADER_X_GB_VER = "X-GB-ver";

    /**
     * GB/T 28181-2022 协议版本号
     * <p>
     * 改造项1：默认协议版本号升级为 "2.0"
     * </p>
     */
    public static final String GB_PROTOCOL_VERSION = "2.0";

    /**
     * GB/T 28181-2016 协议版本号（用于回退兼容判断）
     */
    public static final String GB_PROTOCOL_VERSION_2016 = "1.0";

    /**
     * 默认构造方法私有化，禁止实例化
     */
    private GBProtocolVersionHelper() {
        throw new IllegalStateException("工具类禁止实例化");
    }

    /**
     * 获取当前服务端使用的协议版本
     * <p>
     * 改造项1：对外暴露当前协议版本号，供调用方判断是否走 2022 新流程。
     * </p>
     *
     * @return 协议版本号字符串，例如 "2.0"
     */
    public static String getProtocolVersion() {
        return GB_PROTOCOL_VERSION;
    }

    /**
     * 判断目标版本是否为 2022 版（2.0）
     *
     * @param version 版本号字符串
     * @return 若为主版本号 2 返回 true，否则 false
     */
    public static boolean isVersion2022(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        // 审计修复P2-04: 严格匹配 "2.0", 避免误判 "2.1"/"20" 等异常版本号
        return version.trim().equals("2.0");
    }

    /**
     * 判断目标版本是否为 2016 版（1.0）
     *
     * @param version 版本号字符串
     * @return 若为主版本号 1 返回 true，否则 false
     */
    public static boolean isVersion2016(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        return version.trim().startsWith("1");
    }

    /**
     * 给 SIP 请求追加 X-GB-ver 头部
     * <p>
     * 改造项1：所有外发请求必须携带 X-GB-ver 头部。
     * 来源：设计文档第4节，2022版 7.1 协议版本协商。
     * </p>
     * <p>
     * 实现说明：若请求已存在该头部，则先移除后追加，避免重复。
     * </p>
     *
     * @param request SIP 请求对象
     */
    public static void addGbVerHeader(Request request) {
        if (request == null) {
            logger.warn("[协议版本] 请求对象为空，跳过 X-GB-ver 头部追加");
            return;
        }
        try {
            // 移除已存在的 X-GB-ver 头部，避免重复
            Header existHeader = request.getHeader(HEADER_X_GB_VER);
            if (existHeader != null) {
                request.removeHeader(HEADER_X_GB_VER);
            }
            // 追加当前协议版本头部
            Header gbVerHeader = SipFactory.getInstance().createHeaderFactory().createHeader(HEADER_X_GB_VER, GB_PROTOCOL_VERSION); request.addHeader(gbVerHeader);
        } catch (Exception e) {
            // JAIN-SIP 的 createHeader 通常不会抛出受检异常，这里兜底捕获
            logger.error("[协议版本] 追加 X-GB-ver 头部异常", e);
        }
    }

    /**
     * 从 SIP 请求中解析 X-GB-ver 头部
     * <p>
     * 改造项1：根据对端上报的 X-GB-ver 头部，判断对端协议版本。
     * 来源：设计文档第4节，2022版 7.1 协议版本协商。
     * </p>
     * <p>
     * 协商策略：
     * <ul>
     *     <li>未携带 X-GB-ver：默认对端为 2016 版（1.0）</li>
     *     <li>主版本号 2：2022 版</li>
     *     <li>主版本号 1：2016 版</li>
     * </ul>
     * </p>
     *
     * @param request SIP 请求对象
     * @return 对端协议版本号字符串；未携带则返回 GB_PROTOCOL_VERSION_2016
     */
    public static String parseGbVerHeader(Request request) {
        if (request == null) {
            return GB_PROTOCOL_VERSION_2016;
        }
        try {
            Header header = request.getHeader(HEADER_X_GB_VER);
            if (header == null) {
                // 未携带 X-GB-ver 头部，按规范默认回退到 2016 版
                return GB_PROTOCOL_VERSION_2016;
            }
            String value = ((ExtensionHeader) header).getValue();
            if (value == null || value.trim().isEmpty()) {
                return GB_PROTOCOL_VERSION_2016;
            }
            return value.trim();
        } catch (Exception e) {
            logger.warn("[协议版本] 解析 X-GB-ver 头部异常，默认回退到 2016 版", e);
            return GB_PROTOCOL_VERSION_2016;
        }
    }

    /**
     * 协商最终的协议版本
     * <p>
     * 改造项1：根据本端版本与对端版本协商，取较小主版本号作为最终版本，确保兼容性。
     * </p>
     * <p>
     * 审计修复P3-02: 合并重复的 negotiate 方法, 统一使用 negotiateVersion
     * </p>
     *
     * @param remoteVersion 对端协议版本号
     * @return 协商后协议版本号（"2.0" 或 "1.0"）
     * @see #negotiateVersion(String)
     */
    @Deprecated
    public static String negotiate(String remoteVersion) {
        return negotiateVersion(remoteVersion);
    }

    // === 改造项27: Monitor-user-Identity头域支持 ===
    // 来源: 设计文档第8.3节(第697~718行), 2022版8.3
    // 规范要求: 跨域访问使用Monitor-user-Identity头域(小写u)
    // 改造说明: 2016版为Monitor-User-Identity(大写U), 2022版改为Monitor-user-Identity(小写u)
    public static final String HEADER_MONITOR_USER_IDENTITY = "Monitor-user-Identity";

    /**
     * 添加Monitor-user-Identity头域到SIP请求
     * <p>
     * 来源: 设计文档第8.3节, 2022版8.3跨域访问控制
     * 规范原文: 跨域访问时应携带Monitor-user-Identity头域(注意小写u)
     *
     * @param request SIP请求对象
     * @param userIdentity 用户身份标识
     */
    public static void addMonitorUserIdentityHeader(javax.sip.message.Request request, String userIdentity) {
        if (request == null || userIdentity == null) return;
        try {
            javax.sip.header.Header header = javax.sip.SipFactory.getInstance()
                    .createHeaderFactory()
                    .createHeader(HEADER_MONITOR_USER_IDENTITY, userIdentity);
            request.addHeader(header);
        } catch (Exception e) {
            logger.warn("[Monitor-user-Identity] 添加头域失败: {}", e.getMessage());
        }
    }

    /**
     * 版本协商
     * <p>
     * 来源: 改造项1, 设计文档第13.9节, 2022版附录I
     * 规则: 双方都为 2.0 才走 2022 流程, 否则降级为 2016
     * </p>
     *
     * @param remoteVersion 对端声明的协议版本（从X-GB-ver头域解析）
     * @return 协商后的协议版本："2.0" 或 "1.0"
     */
    public static String negotiateVersion(String remoteVersion) {
        if (remoteVersion == null || remoteVersion.trim().isEmpty()) {
            return GB_PROTOCOL_VERSION_2016;
        }
        // 双方都为 2.0 才走 2022 流程，否则降级
        if (isVersion2022(remoteVersion) && isVersion2022(GB_PROTOCOL_VERSION)) {
            return GB_PROTOCOL_VERSION;
        }
        return GB_PROTOCOL_VERSION_2016;
    }
}
