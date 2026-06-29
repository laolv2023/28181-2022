package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * SIP 字符集工具类
 * <p>
 * 改造项35：字符集由 GB 2312 升级为 GB 18030。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第5.4节（字符集升级），2022版 6.10 字符集要求。<br>
 * 协议要求：2016 版默认字符集为 GB 2312，仅支持 6763 个汉字；2022 版强制升级为 GB 18030，
 * 支持全部 GBK 字符集及少数民族文字，向下兼容 GB 2312 与 GBK。
 * </p>
 * <p>
 * <b>升级影响：</b>
 * <ul>
 *     <li>SIP 消息体（XML）的编码解码统一使用 GB 18030</li>
 *     <li>SDP 中字符串字段编码统一使用 GB 18030</li>
 *     <li>设备名称、通道名称等包含汉字的字段不再受 GB 2312 字符集限制</li>
 *     <li>Content-Type 头部需携带 charset=gb18030 参数</li>
 * </ul>
 * </p>
 * <p>
 * <b>兼容性策略：</b><br>
 * 当 GB 18030 编码不可用时（理论上不会出现，因为 JDK 内置 GB 18030 支持），
 * 降级使用 UTF-8 并记录告警，避免流程中断。
 * </p>
 *
 * @author wvp-upgrade
 */
public final class SipCharsetHelper {

    private static final Logger logger = LoggerFactory.getLogger(SipCharsetHelper.class);

    /**
     * 默认字符集常量：GB 18030
     * <p>改造项35：来源 设计文档第5.4节，2022版 6.10</p>
     */
    public static final String DEFAULT_CHARSET = "gb18030";

    /**
     * 旧版（2016）字符集常量：GB 2312
     * <p>用于版本协商回退场景</p>
     */
    public static final String LEGACY_CHARSET = "gb2312";

    /**
     * 备用字符集常量：UTF-8
     * <p>当 GB 18030 不可用时降级使用</p>
     */
    public static final String FALLBACK_CHARSET = "UTF-8";

    /**
     * 默认字符集 Charset 对象（启动时初始化）
     */
    private static final Charset DEFAULT_CHARSET_OBJ;

    static {
        Charset charset;
        try {
            // JDK 内置支持 GB 18030，理论上不会失败
            charset = Charset.forName(DEFAULT_CHARSET);
        } catch (Exception e) {
            logger.warn("[字符集] GB 18030 不可用，降级使用 UTF-8", e);
            charset = StandardCharsets.UTF_8;
        }
        DEFAULT_CHARSET_OBJ = charset;
    }

    /**
     * 私有构造方法，禁止实例化
     */
    private SipCharsetHelper() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 获取默认字符集名称
     * <p>改造项35：返回 GB 18030 字符集名称字符串</p>
     *
     * @return 默认字符集名称
     */
    public static String getDefaultCharsetName() {
        return DEFAULT_CHARSET;
    }

    /**
     * 获取默认字符集 Charset 对象
     *
     * @return 默认字符集 Charset 对象
     */
    public static Charset getDefaultCharset() {
        return DEFAULT_CHARSET_OBJ;
    }

    /**
     * 使用 GB 18030 编码字符串
     * <p>
     * 改造项35：来源 设计文档第5.4节，2022版 6.10。<br>
     * 将字符串编码为 GB 18030 字节序列，用于 SIP 消息体构造。
     * </p>
     *
     * @param text 待编码字符串
     * @return GB 18030 编码后的字节数组
     * @throws IllegalArgumentException 当字符串为 null 时抛出
     */
    public static byte[] encode(String text) {
        if (text == null) {
            throw new IllegalArgumentException("待编码字符串不能为 null");
        }
        try {
            return text.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            // 理论上不会发生，DEFAULT_CHARSET_OBJ 已经在静态块兜底
            logger.warn("[字符集] GB 18030 编码异常，降级使用 UTF-8", e);
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 使用 GB 18030 解码字节数组
     * <p>
     * 改造项35：来源 设计文档第5.4节，2022版 6.10。<br>
     * 将 GB 18030 字节序列解码为字符串，用于 SIP 消息体解析。
     * </p>
     *
     * @param bytes 待解码字节数组
     * @return 解码后的字符串
     * @throws IllegalArgumentException 当字节数组为 null 时抛出
     */
    public static String decode(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("待解码字节数组不能为 null");
        }
        try {
            return new String(bytes, DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            logger.warn("[字符集] GB 18030 解码异常，降级使用 UTF-8", e);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * 使用指定字符集名称编码字符串
     * <p>
     * 用于版本协商时根据对端版本选择不同字符集（GB 2312 或 GB 18030）。
     * </p>
     *
     * @param text        待编码字符串
     * @param charsetName 字符集名称
     * @return 编码后的字节数组
     * @throws IllegalArgumentException 当参数非法时抛出
     */
    public static byte[] encode(String text, String charsetName) {
        if (text == null) {
            throw new IllegalArgumentException("待编码字符串不能为 null");
        }
        if (ObjectUtils.isEmpty(charsetName)) {
            return encode(text);
        }
        try {
            return text.getBytes(charsetName);
        } catch (UnsupportedEncodingException e) {
            logger.warn("[字符集] 指定字符集 {} 不可用，降级使用 GB 18030", charsetName, e);
            return encode(text);
        }
    }

    /**
     * 使用指定字符集名称解码字节数组
     *
     * @param bytes       待解码字节数组
     * @param charsetName 字符集名称
     * @return 解码后的字符串
     * @throws IllegalArgumentException 当参数非法时抛出
     */
    public static String decode(byte[] bytes, String charsetName) {
        if (bytes == null) {
            throw new IllegalArgumentException("待解码字节数组不能为 null");
        }
        if (ObjectUtils.isEmpty(charsetName)) {
            return decode(bytes);
        }
        try {
            return new String(bytes, charsetName);
        } catch (UnsupportedEncodingException e) {
            logger.warn("[字符集] 指定字符集 {} 不可用，降级使用 GB 18030", charsetName, e);
            return decode(bytes);
        }
    }

    /**
     * 根据协议版本协商字符集
     * <p>
     * 改造项35+改造项1：来源 设计文档第5.4节、第4节，2022版 6.10、7.1。<br>
     * 2022 版使用 GB 18030，2016 版使用 GB 2312，未指定版本时默认 GB 18030。
     * </p>
     *
     * @param protocolVersion 协议版本号（"2.0" / "1.0" / null）
     * @return 协商后的字符集名称
     */
    public static String negotiateCharset(String protocolVersion) {
        if (protocolVersion == null || protocolVersion.trim().isEmpty()) {
            return DEFAULT_CHARSET;
        }
        if ("1.0".equals(protocolVersion.trim())) {
            // 2016 版回退到 GB 2312
            return LEGACY_CHARSET;
        }
        // 2022 版及其他默认 GB 18030
        return DEFAULT_CHARSET;
    }
}
