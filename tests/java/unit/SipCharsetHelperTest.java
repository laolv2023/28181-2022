package com.genersoft.iot.vmp.gb28181.utils;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SIP字符集与XML安全测试
 * 测试用例: A9.1-01~03
 * 来源: 改造项35, 设计文档第5.4节, 2022版6.10
 */
@DisplayName("SIP字符集与XML安全测试")
class SipCharsetHelperTest {

    @Test
    @DisplayName("A9.1-01: 默认字符集为GB18030")
    void testDefaultCharset() {
        // 来源: 改造项35, 设计文档第5.4节, 2022版6.10
        // 2016版字符集为GB 2312, 2022版升级为GB 18030
        assertEquals("gb18030", SipCharsetHelper.DEFAULT_CHARSET, "默认字符集应为gb18030");
        assertEquals("gb2312", SipCharsetHelper.LEGACY_CHARSET, "旧版字符集应为gb2312");
    }

    @Test
    @DisplayName("A9.1-02: GB18030编码解码")
    void testEncodeDecode() {
        String input = "测试设备名称";
        byte[] encoded = SipCharsetHelper.encode(input);
        assertNotNull(encoded, "编码结果不应为null");
        String decoded = SipCharsetHelper.decode(encoded);
        assertEquals(input, decoded, "编解码应可逆");
    }

    @Test
    @DisplayName("A9.1-03: XML特殊字符转义")
    void testEscapeXml() {
        // 审计修复: 防止XML注入
        assertEquals("&amp;", SipCharsetHelper.escapeXml("&"), "&应转义为&amp;");
        assertEquals("&lt;", SipCharsetHelper.escapeXml("<"), "<应转义为&lt;");
        assertEquals("&gt;", SipCharsetHelper.escapeXml(">"), ">应转义为&gt;");
        assertEquals("&quot;", SipCharsetHelper.escapeXml("\""), "\"应转义为&quot;");
        assertEquals("&apos;", SipCharsetHelper.escapeXml("'"), "'应转义为&apos;");
        assertEquals("", SipCharsetHelper.escapeXml(null), "null应返回空字符串");
        assertEquals("normal text", SipCharsetHelper.escapeXml("normal text"), "普通文本不应转义");
    }

    @Test
    @DisplayName("混合特殊字符转义")
    void testEscapeXmlMixed() {
        String input = "<DeviceID>3402&test<>'</DeviceID>";
        String escaped = SipCharsetHelper.escapeXml(input);
        assertFalse(escaped.contains("<D"), "转义后不应包含原始<");
        assertTrue(escaped.contains("&lt;"), "应包含&lt;");
        assertTrue(escaped.contains("&amp;"), "应包含&amp;");
    }
}
