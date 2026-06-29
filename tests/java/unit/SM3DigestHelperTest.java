package com.genersoft.iot.vmp.gb28181.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM3 国密摘要算法工具类测试
 * <p>
 * 测试用例: A1.1-01 ~ A1.1-07
 * 规范来源:
 *   - 改造项 2: SM3 摘要认证
 *   - 设计文档第 5.3 节 (摘要与认证)
 *   - GB/T 28181-2022 第 6.7.2 节 (数字摘要算法)
 *   - GB/T 32905-2016 SM3 密码杂凑算法
 * <p>
 * 测试目的: 验证 SM3DigestHelper 在 GB28181-2022 SIP 鉴权场景下的:
 *   1. 摘要计算正确性 (32 字节 / 64 位十六进制串)
 *   2. 摘要验证接口可用
 *   3. 当 SM3 算法提供者缺失时,可平滑回退到 MD5 兼容旧平台
 *   4. 边界输入 (空串 / null) 的健壮性
 *   5. 算法可用性检测接口对部署环境的探测能力
 *
 * @author vmp-test
 * @since GB28181-2022 改造
 */
@DisplayName("SM3 摘要算法工具类测试 (A1.1-01~07)")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class SM3DigestHelperTest {

    /** 已知 SM3 向量: SM3("abc") 的标准结果 (来自 GB/T 32905-2016 附录 A) */
    private static final String SM3_ABC_HEX =
            "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0";

    /** 已知 MD5 向量: MD5("abc") = 900150983cd24fb0d6963f7d28e17f72 */
    private static final String MD5_ABC_HEX =
            "900150983cd24fb0d6963f7d28e17f72";

    @BeforeAll
    static void initLocale() {
        // 强制 locale, 避免日志/异常信息随环境波动
        Locale.setDefault(Locale.CHINA);
    }

    /**
     * 用例 A1.1-01: SM3 摘要计算正确性
     * <p>
     * 来源: 改造项 2 / 设计文档 5.3 / 2022 版 6.7.2 / GB/T 32905-2016 附录 A
     * 验证点:
     *   - 输出长度 = 32 字节 = 64 个十六进制字符
     *   - 输出字符均为 0-9a-f (或大写)
     *   - 与 GB/T 32905 标准向量一致
     */
    @Test
    @DisplayName("A1.1-01: SM3 摘要计算正确性")
    void testSm3Digest() {
        // 来源: GB/T 32905-2016 附录 A.1 测试用例 1
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        String result = SM3DigestHelper.digest(input);

        assertNotNull(result, "SM3 摘要结果不应为 null");
        assertEquals(64, result.length(),
                "SM3 摘要应为 32 字节,即 64 个十六进制字符,实际: " + result.length());
        assertTrue(result.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}"),
                "SM3 摘要应仅包含十六进制字符,实际: " + result);
        assertEquals(SM3_ABC_HEX, result.toLowerCase(Locale.ROOT),
                "SM3('abc') 应等于 GB/T 32905-2016 标准向量");

        // 长输入向量 (GB/T 32905-2016 附录 A.1 测试用例 2 的前 56 字节变体)
        byte[] longInput = new byte[64];
        for (int i = 0; i < longInput.length; i++) {
            longInput[i] = (byte) ('a' + (i % 26));
        }
        String longResult = SM3DigestHelper.digest(longInput);
        assertEquals(64, longResult.length(),
                "任意长度输入摘要均应为 64 个十六进制字符");
    }

    /**
     * 用例 A1.1-02: SM3 摘要验证接口
     * <p>
     * 来源: 设计文档 5.3 / 2022 版 6.7.2
     * 验证点:
     *   - verify(input, hexDigest) 对正确摘要返回 true
     *   - 对篡改后的摘要返回 false
     *   - 大小写不敏感 (hex 比较前统一转小写)
     */
    @Test
    @DisplayName("A1.1-02: SM3 摘要验证方法")
    void testSm3Verify() {
        byte[] input = "test123456".getBytes(StandardCharsets.UTF_8);
        String digestHex = SM3DigestHelper.digest(input);

        // 来源: 2022 版 6.7.2 - 鉴权响应需校验摘要
        assertTrue(SM3DigestHelper.verify(input, digestHex),
                "正确摘要应校验通过");
        assertTrue(SM3DigestHelper.verify(input, digestHex.toUpperCase(Locale.ROOT)),
                "摘要大小写不敏感,大写形式应同样校验通过");

        // 篡改输入
        byte[] tampered = "test123457".getBytes(StandardCharsets.UTF_8);
        assertFalse(SM3DigestHelper.verify(tampered, digestHex),
                "输入被篡改后应校验失败");

        // 篡改摘要
        char[] tamperedHex = digestHex.toCharArray();
        tamperedHex[0] = (tamperedHex[0] == '0') ? '1' : '0';
        assertFalse(SM3DigestHelper.verify(input, new String(tamperedHex)),
                "摘要被篡改后应校验失败");
    }

    /**
     * 用例 A1.1-03: MD5 兼容回退
     * <p>
     * 来源: 改造项 2 兼容性要求 - 需兼容 2016 版平台不支持 SM3 的设备
     * 验证点:
     *   - 当 SM3 不可用时,回退到 MD5
     *   - MD5 输出长度 = 16 字节 = 32 个十六进制字符
     *   - MD5('abc') 与标准向量一致
     */
    @Test
    @DisplayName("A1.1-03: MD5 兼容回退")
    void testMd5Fallback() {
        // 来源: 改造项 2 - 兼容 2016 平台
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);

        String md5Hex = SM3DigestHelper.digestMd5(input);
        assertNotNull(md5Hex, "MD5 摘要不应为 null");
        assertEquals(32, md5Hex.length(),
                "MD5 摘要应为 16 字节 = 32 个十六进制字符,实际: " + md5Hex.length());
        assertEquals(MD5_ABC_HEX, md5Hex.toLowerCase(Locale.ROOT),
                "MD5('abc') 应等于标准向量");

        // 验证回退逻辑 - 优先 SM3,失败则 MD5
        String autoDigest = SM3DigestHelper.digestWithFallback(input);
        assertNotNull(autoDigest, "自动选择算法摘要不应为 null");
        // 若 SM3 可用,长度应为 64;否则回退 MD5 长度为 32
        assertTrue(autoDigest.length() == 64 || autoDigest.length() == 32,
                "自动选择算法摘要长度应为 64 (SM3) 或 32 (MD5 回退),实际: " + autoDigest.length());
    }

    /**
     * 用例 A1.1-04: 空输入处理
     * <p>
     * 来源: 设计文档 5.3 健壮性要求
     * 验证点:
     *   - 空字节数组应正常返回摘要,不抛异常
     *   - 空字符串等价于空字节数组
     *   - SM3("") 长度仍为 64,MD5("") 长度仍为 32
     */
    @Test
    @DisplayName("A1.1-04: 空输入处理")
    void testEmptyInput() {
        byte[] empty = new byte[0];

        String sm3Empty = SM3DigestHelper.digest(empty);
        assertNotNull(sm3Empty, "空输入 SM3 摘要不应为 null");
        assertEquals(64, sm3Empty.length(), "空输入 SM3 摘要长度仍应为 64");

        String md5Empty = SM3DigestHelper.digestMd5(empty);
        assertNotNull(md5Empty, "空输入 MD5 摘要不应为 null");
        assertEquals(32, md5Empty.length(), "空输入 MD5 摘要长度仍应为 32");

        // 空字符串等价
        String emptyStr = "";
        assertEquals(sm3Empty,
                SM3DigestHelper.digest(emptyStr.getBytes(StandardCharsets.UTF_8)),
                "空字节数组与空字符串摘要应一致");

        // 与 RFC 1320 MD5("") 标准向量比对 (d41d8cd98f00b204e9800998ecf8427e)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                md5Empty.toLowerCase(Locale.ROOT),
                "MD5('') 应等于 RFC 1320 标准向量");
    }

    /**
     * 用例 A1.1-05: null 输入安全
     * <p>
     * 来源: 设计文档 5.3 健壮性要求 - 鉴权流程中可能传入 null
     * 验证点:
     *   - digest(null) 不抛 NPE, 返回 null 或抛 IllegalArgumentException
     *   - verify(null, ...) / verify(..., null) 同样安全
     *   - digestMd5(null) 同样安全
     */
    @Test
    @DisplayName("A1.1-05: null 输入安全")
    void testNullInput() {
        // digest(null) - 应当不抛 NPE,允许返回 null 或抛 IllegalArgumentException
        String nullResult = assertDoesNotThrow(
                () -> SM3DigestHelper.digest(null),
                "digest(null) 不应抛出未捕获异常");
        // 若返回 null 也可接受; 若返回值则长度必须为 64
        if (nullResult != null) {
            assertEquals(64, nullResult.length(),
                    "digest(null) 若返回非空,长度必须为 64");
        }

        // verify(null, ...) 安全
        assertDoesNotThrow(
                () -> SM3DigestHelper.verify(null, "abcdef"),
                "verify(null, ...) 不应抛 NPE");
        assertFalse(SM3DigestHelper.verify(null, "abcdef"),
                "verify(null, ...) 应返回 false");

        // verify(input, null) 安全
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(
                () -> SM3DigestHelper.verify(input, null),
                "verify(input, null) 不应抛 NPE");
        assertFalse(SM3DigestHelper.verify(input, null),
                "verify(input, null) 应返回 false");

        // digestMd5(null) 安全
        assertDoesNotThrow(
                () -> SM3DigestHelper.digestMd5(null),
                "digestMd5(null) 不应抛 NPE");
    }

    /**
     * 用例 A1.1-06: SM3 算法可用性检测
     * <p>
     * 来源: 改造项 2 - 部署环境检测,用于决定是否启用 SM3 鉴权
     * 验证点:
     *   - isSm3Available() 返回布尔值,不抛异常
     *   - 当 BouncyCastle 或国密 Provider 注册时,应返回 true
     *   - 与 Java Security Provider 注册状态一致
     */
    @Test
    @DisplayName("A1.1-06: SM3 算法可用性检测")
    void testAlgorithmAvailability() {
        boolean available = assertDoesNotThrow(
                SM3DigestHelper::isSm3Available,
                "isSm3Available() 不应抛异常");

        // 来源: 通过 java.security.Security 验证一致性
        boolean providerRegistered = false;
        for (java.security.Provider p : Security.getProviders()) {
            if (p.getService("MessageDigest", "SM3") != null) {
                providerRegistered = true;
                break;
            }
        }
        assertEquals(providerRegistered, available,
                "isSm3Available() 应与 Security Provider 注册状态一致");

        // 当 SM3 不可用时, digestWithFallback 必须回退到 MD5
        if (!available) {
            byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
            String fallback = SM3DigestHelper.digestWithFallback(input);
            assertNotNull(fallback);
            assertEquals(32, fallback.length(),
                    "SM3 不可用时 digestWithFallback 应回退到 MD5,长度 32");
        }
    }

    /**
     * 用例 A1.1-07: 摘要算法一致性 (回归)
     * <p>
     * 来源: 改造项 2 - 同一输入多次调用应结果一致
     * 验证点:
     *   - 多次调用 digest 返回相同结果
     *   - 多次调用 verify 结果稳定
     *   - 跨实例线程安全
     */
    @Test
    @DisplayName("A1.1-07: 摘要算法一致性 (回归)")
    void testDigestConsistency() {
        byte[] input = "GB28181-2022-AuthTest".getBytes(StandardCharsets.UTF_8);
        String first = SM3DigestHelper.digest(input);

        // 多次调用一致性
        for (int i = 0; i < 50; i++) {
            assertEquals(first, SM3DigestHelper.digest(input),
                    "多次调用 digest 结果应一致,iteration=" + i);
            assertTrue(SM3DigestHelper.verify(input, first),
                    "多次调用 verify 应稳定返回 true,iteration=" + i);
        }

        // 简单并发验证 - 不强制要求线程安全实现,但若实现声称线程安全则必须稳定
        java.util.Set<String> concurrentResults = java.util.Collections.newSetFromMap(
                new java.util.concurrent.ConcurrentHashMap<>());
        int threads = 8;
        int perThread = 25;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            concurrentResults.add(SM3DigestHelper.digest(input));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS),
                    "并发摘要任务应在 10s 内完成");
            assertEquals(1, concurrentResults.size(),
                    "并发调用 digest 结果应保持一致");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("测试被中断");
        } finally {
            pool.shutdownNow();
        }
    }
}
