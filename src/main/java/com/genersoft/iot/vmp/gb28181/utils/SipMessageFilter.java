package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import javax.sip.RequestEvent;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.Header;
import javax.sip.message.Request;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * SIP 消息过滤工具类
 * <p>
 * 补充1：SIP 端口防干扰——对到达 SIP 端口的请求进行合法性过滤，
 * 拦截非法方法、异常 Content-Type 与超大消息体，避免无效请求占用处理资源。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第4.3.2节（SIP 端口防干扰），2022版 4.3.2 信令交互要求。<br>
 * 协议要求：SIP 监听端口仅接受 GB/T 28181 协议规定的标准方法，
 * 非白名单方法应当直接拒绝（响应 405 Method Not Allowed），
 * 防止端口被非授权协议探测或恶意流量干扰。
 * </p>
 * <p>
 * <b>方法白名单（10 种）：</b>
 * <ul>
 *     <li>REGISTER：设备注册</li>
 *     <li>MESSAGE：消息收发（含目录、状态、控制、告警等 XML 体）</li>
 *     <li>INVITE：媒体会话邀请</li>
 *     <li>ACK：邀请确认</li>
 *     <li>BYE：会话结束</li>
 *     <li>CANCEL：邀请取消</li>
 *     <li>OPTIONS：能力查询</li>
 *     <li>INFO：会话中信息</li>
 *     <li>SUBSCRIBE：订阅</li>
 *     <li>NOTIFY：通知</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
public final class SipMessageFilter {

    private static final Logger logger = LoggerFactory.getLogger(SipMessageFilter.class);

    /**
     * SIP 方法白名单
     * <p>
     * 补充1：来源 设计文档第4.3.2节，2022版 4.3.2。<br>
     * 仅允许 GB/T 28181 协议规定的 10 种标准方法。
     * </p>
     */
    public static final Set<String> ALLOWED_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            Request.REGISTER,
            Request.MESSAGE,
            Request.INVITE,
            Request.ACK,
            Request.BYE,
            Request.CANCEL,
            Request.OPTIONS,
            Request.INFO,
            Request.SUBSCRIBE,
            Request.NOTIFY
    )));

    /**
     * 允许的 Content-Type 主类型
     * <p>补充1：来源 设计文档第4.3.2节</p>
     */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "APPLICATION",
            "TEXT",
            "MULTIPART"
    )));

    /**
     * 允许的 Content-Type 子类型
     */
    public static final Set<String> ALLOWED_CONTENT_SUBTYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "MANSCDP+xml",
            "SDP",
            "XML",
            "PLAIN",
            "MIXED",
            "MANSRTSP"
    )));

    /**
     * 最大 SIP 消息体字节数（默认 1MB）
     * <p>防止超大消息体导致内存溢出</p>
     */
    public static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    /**
     * 私有构造方法，禁止实例化
     */
    private SipMessageFilter() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 校验 SIP 请求是否合法
     * <p>
     * 补充1：来源 设计文档第4.3.2节，2022版 4.3.2。<br>
     * 校验维度：
     * <ol>
     *     <li>请求对象非空</li>
     *     <li>SIP 方法在白名单内</li>
     *     <li>Content-Type 合法（如携带消息体）</li>
     *     <li>消息体大小未超阈值</li>
     * </ol>
     * </p>
     *
     * @param evt SIP 请求事件
     * @return true 表示请求合法，应继续处理
     */
    public static boolean isSipMessageValid(RequestEvent evt) {
        if (evt == null || evt.getRequest() == null) {
            logger.warn("[SIP过滤] 请求事件或 Request 对象为空");
            return false;
        }
        Request request = evt.getRequest();
        String method = request.getMethod();

        // 1. 校验方法白名单
        if (!isMethodAllowed(method)) {
            logger.warn("[SIP过滤] 非白名单 SIP 方法: {}", method);
            return false;
        }

        // 2. 校验 Content-Type（如携带消息体）
        try {
            ContentTypeHeader contentTypeHeader = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
            if (contentTypeHeader != null) {
                String contentType = contentTypeHeader.getContentType();
                String contentSubType = contentTypeHeader.getContentSubType();
                if (!isContentTypeAllowed(contentType, contentSubType)) {
                    logger.warn("[SIP过滤] 非法 Content-Type: {}/{}", contentType, contentSubType);
                    return false;
                }
            }
        } catch (Throwable t) {
            // 解析异常不阻断流程，仅记录告警
            logger.warn("[SIP过滤] Content-Type 解析异常: {}", t.getMessage());
        }

        // 3. 校验消息体大小
        try {
            byte[] rawContent = request.getRawContent();
            if (rawContent != null && rawContent.length > MAX_CONTENT_LENGTH) {
                logger.warn("[SIP过滤] 消息体过大: {} bytes, 上限 {}", rawContent.length, MAX_CONTENT_LENGTH);
                return false;
            }
        } catch (Throwable t) {
            logger.debug("[SIP过滤] 读取消息体异常: {}", t.getMessage());
        }

        // 4. 校验 X-GB-ver 头部（改造项1，2022版 7.1，可选）
        try {
            Header gbVerHeader = request.getHeader(GBProtocolVersionHelper.HEADER_X_GB_VER);
            if (gbVerHeader != null) {
                // 头部存在则记录版本号，便于后续协议版本协商
                logger.debug("[SIP过滤] X-GB-ver: {}", gbVerHeader);
            }
        } catch (Throwable t) {
            logger.debug("[SIP过滤] X-GB-ver 头部解析异常: {}", t.getMessage());
        }

        return true;
    }

    /**
     * 校验 SIP 方法是否在白名单内
     * <p>
     * 补充1：来源 设计文档第4.3.2节，2022版 4.3.2。
     * </p>
     *
     * @param method SIP 方法名
     * @return true 表示方法合法
     */
    public static boolean isMethodAllowed(String method) {
        if (ObjectUtils.isEmpty(method)) {
            return false;
        }
        return ALLOWED_METHODS.contains(method.toUpperCase());
    }

    /**
     * 校验 Content-Type 是否合法
     * <p>
     * 补充1：来源 设计文档第4.3.2节。<br>
     * 主类型必须为 APPLICATION/TEXT/MULTIPART 之一，
     * 子类型必须为 MANSCDP+xml/SDP/XML/PLAIN/MIXED/MANSRTSP 之一。
     * </p>
     *
     * @param contentType    主类型
     * @param contentSubType 子类型
     * @return true 表示 Content-Type 合法
     */
    public static boolean isContentTypeAllowed(String contentType, String contentSubType) {
        if (ObjectUtils.isEmpty(contentType) || ObjectUtils.isEmpty(contentSubType)) {
            return false;
        }
        return ALLOWED_CONTENT_TYPES.contains(contentType.toUpperCase())
                && ALLOWED_CONTENT_SUBTYPES.contains(contentSubType.toUpperCase());
    }

    /**
     * 校验消息体大小是否超阈值
     * <p>默认上限 1MB</p>
     *
     * @param contentLength 消息体字节数
     * @return true 表示大小合法
     */
    public static boolean isContentLengthValid(int contentLength) {
        return contentLength >= 0 && contentLength <= MAX_CONTENT_LENGTH;
    }
}
