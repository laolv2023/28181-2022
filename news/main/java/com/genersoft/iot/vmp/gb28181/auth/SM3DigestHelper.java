package com.genersoft.iot.vmp.gb28181.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

/**
 * SM3 摘要算法工具类
 * <p>
 * 改造项2：SM3 国密摘要算法支持，用于 GB/T 28181-2022 鉴权流程。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第5节（鉴权增强），2022版 7.2 安全性要求。<br>
 * 2022 版要求支持 SM3 摘要算法，作为 MD5 的国密替代方案。
 * </p>
 * <p>
 * <b>实现策略：</b>
 * <ol>
 *     <li>优先使用 JDK 内置 SM3 算法（部分 JDK 内置 BouncyCastle Provider）</li>
 *     <li>其次尝试加载 BouncyCastle Provider</li>
 *     <li>若均不可用，记录告警并不可用时抛异常（保证功能不中断，但需运维介入）</li>
 * </ol>
 * </p>
 *
 * @author wvp-upgrade
 */
public final class SM3DigestHelper {

    private static final Logger logger = LoggerFactory.getLogger(SM3DigestHelper.class);

    /**
     * SM3 算法名称（JDK 标准）
     */
    private static final String ALGORITHM_SM3 = "SM3";

    /**
     * 回退算法：SHA-256（已废弃 — digestWithFallback 现已改为抛异常而非降级，此常量保留仅供静态块历史参考）
     * @deprecated SM3 不可用时直接抛 {@link IllegalStateException}，不再使用 SHA-256 降级
     */
    @Deprecated
    private static final String ALGORITHM_FALLBACK = "SHA-256";

    /**
     * BouncyCastle Provider 类名
     */
    private static final String BC_PROVIDER_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider";

    /**
     * 16 进制字符表
     */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * SM3 是否可用（启动时一次性检测，避免每次调用都触发异常）
     */
    private static final boolean SM3_AVAILABLE;

    /**
     * 当前实际使用的算法名称
     */
    private static final String ACTUAL_ALGORITHM;
    /**
     * SM3算法是否可用
     */

    static {
        // 静态初始化块：检测 SM3 算法可用性
        boolean available = false;
        String algorithm = null;

        // 1. 先尝试 JDK 内置 SM3
        try {
            MessageDigest.getInstance(ALGORITHM_SM3);
            available = true;
            algorithm = ALGORITHM_SM3;
            logger.info("[SM3] JDK 内置 SM3 算法可用");
        } catch (NoSuchAlgorithmException e) {
            logger.info("[SM3] JDK 内置 SM3 不可用，尝试加载 BouncyCastle Provider");
        }

        // 2. JDK 内置不可用时尝试加载 BouncyCastle
        if (!available) {
            try {
                Class<?> bcClass = Class.forName(BC_PROVIDER_CLASS);
                Provider provider = (Provider) bcClass.getDeclaredConstructor().newInstance();
                Security.addProvider(provider);
                MessageDigest.getInstance(ALGORITHM_SM3);
                available = true;
                algorithm = ALGORITHM_SM3;
                logger.info("[SM3] BouncyCastle SM3 算法加载成功");
            } catch (Throwable t) {
                logger.error("[SM3] BouncyCastle SM3 不可用。原因：{}", t.getMessage());
            // SM3 不可用时不静默降级, 调用方应通过 digestMd5() 显式回退
                logger.warn("[SM3] 请引入 bouncycastle 依赖以启用 SM3 算法：<dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></dependency>");
            }
        }

        ACTUAL_ALGORITHM = algorithm;
        SM3_AVAILABLE = available;
    }

    /**
     * 默认构造方法私有化，禁止实例化
     */
    private SM3DigestHelper() {
        // 工具类禁止实例化;
    }

    /**
     * 检测 SM3 算法是否可用
     *
     * @return true 表示 SM3 可用；false 表示已不可用时抛异常
     */
    public static boolean isSm3Available() {
        return SM3_AVAILABLE;
    }

    /**
     * 获取当前实际使用的算法名称
     *
     * @return 算法名称字符串
     */
    public static String getActualAlgorithm() {
        return ACTUAL_ALGORITHM;
    }

    /**
     * 计算字节数组的 SM3 摘要，返回 16 进制小写字符串
     * <p>
     * 改造项2：SM3 摘要计算入口
     * 来源：设计文档第5节，2022版 7.2 安全性要求。
     * </p>
     *
     * @param data 原始字节数组
     * @return 16 进制摘要字符串（小写），SM3 长度为 64；输入为空返回空字符串
     */
    public static String digest(byte[] data) {
        if (data == null) {
            return "";
        }
        if (!SM3_AVAILABLE) {
            throw new IllegalStateException("SM3算法不可用, 请引入BouncyCastle依赖");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(ACTUAL_ALGORITHM);
            byte[] digestBytes = md.digest(data);
            return bytesToHex(digestBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("[SM3] 摘要计算异常，算法: {}", ACTUAL_ALGORITHM, e);
            return "";
        }
    }

    /**
     * 计算字符串的 SM3 摘要，使用 UTF-8 编码
     *
     * @param text 原始字符串
     * @return 16 进制摘要字符串；输入为空返回空字符串
     */
    public static String digest(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return "";
        }
        return digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 校验输入数据与目标摘要是否匹配
     * <p>
     * 改造项2：用于鉴权场景，比对设备上报摘要与本端计算摘要。
     * </p>
     *
     * @param data       原始字节数组
     * @param expectHex  期望的 16 进制摘要字符串（不区分大小写）
     * @return true 表示匹配；false 表示不匹配或参数无效
     */
    public static boolean verify(byte[] data, String expectHex) {
        if (ObjectUtils.isEmpty(expectHex)) {
            return false;
        }
        String actualHex = digest(data);
        if (actualHex == null || actualHex.isEmpty()) {
            return false;
        }
        // 审计修复P1-03: 使用常量时间比较防止时序攻击, 大小写不敏感
        String normalizedActual = actualHex.toLowerCase(java.util.Locale.ROOT);
        String normalizedExpect = expectHex.trim().toLowerCase(java.util.Locale.ROOT);
        return java.security.MessageDigest.isEqual(
                normalizedActual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                normalizedExpect.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 字节数组转 16 进制字符串（小写）
     *
     * @param bytes 字节数组
     * @return 16 进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_CHARS[v >>> 4];
            chars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * MD5 摘要计算（兼容 2016 版设备）
     * <p>
     * 来源: 改造项2 兼容性要求 — 当 SM3 不可用时回退到 MD5
     * </p>
     *
     * @param data 原始数据
     * @return 32 位十六进制字符串
     */
    public static String digestMd5(byte[] data) {
        if (data == null) {
            return "";
        }
        try {
            java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
            return bytesToHex(md5.digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.error("[SM3] MD5算法不可用", e);
            return "";
        }
    }

    /**
     * 自动选择算法的摘要计算（SM3 优先）
     * <p>
     * 来源: 改造项2 — SM3 优先。
     * 审计修复 P1-03: SM3 不可用时直接抛 {@link IllegalStateException}，不再静默降级到 SHA-256。
     * 调用方如需 MD5 兼容，请显式调用 {@link #digestMd5(byte[])}。
     * </p>
     *
     * @param data 原始数据
     * @return 摘要十六进制字符串（SM3 为 64 字符）
     * @throws IllegalStateException 当 SM3 算法不可用时
     */
    public static String digestWithFallback(byte[] data) {
        if (!SM3_AVAILABLE) {
            throw new IllegalStateException("SM3算法不可用, 请引入BouncyCastle依赖");
        }
        if (data == null) {
            return "";
        }
        return digest(data);
    }
}

