package com.genersoft.iot.vmp.gb28181.utils;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MANSRTSP协议测试
 * 测试用例: A8.1-01~03, B10-01~04
 * 来源: 改造项24-25, 设计文档第12节, 2022版附录B
 */
@DisplayName("MANSRTSP协议测试")
class MansrtspHelperTest {

    @Test
    @DisplayName("A8.1-01: 合法scale值{0.25, 0.5, 1, 2, 4}")
    void testAppendScale_ValidValues() {
        // 来源: 改造项24, 2022版B.2.7
        // 合法scale取值: 0.25, 0.5, 1, 2, 4
        for (double validScale : new double[]{0.25, 0.5, 1.0, 2.0, 4.0}) {
            StringBuilder sb = new StringBuilder();
            MansrtspHelper.appendScale(sb, validScale);
            String result = sb.toString();
            assertTrue(result.contains("Scale:" + validScale) || result.contains("Scale: " + validScale),
                "合法scale值" + validScale + "应被接受");
        }
    }

    @Test
    @DisplayName("A8.1-01: 非法scale值纠正到最近合法值")
    void testAppendScale_InvalidValue() {
        // 来源: 改造项24, 非法scale值应snap到最近的合法值
        StringBuilder sb = new StringBuilder();
        MansrtspHelper.appendScale(sb, 0.3);
        String result = sb.toString();
        // 0.3最近的合法值是0.25或0.5
        assertTrue(result.contains("0.25") || result.contains("0.5"),
            "非法scale值0.3应纠正到0.25或0.5");
    }

    @Test
    @DisplayName("A8.1-02: 倒放scale为负数")
    void testAppendReverseScale() {
        // 来源: 改造项25, 2022版B.2.8
        // 倒放时Scale为负数, 至少支持-1
        StringBuilder sb = new StringBuilder();
        MansrtspHelper.appendReverseScale(sb, 1.0);
        String result = sb.toString();
        assertTrue(result.contains("-"), "倒放scale应包含负号");
    }

    @Test
    @DisplayName("B10-03: 倒放至少支持-1")
    void testAppendReverseScale_MinusOne() {
        StringBuilder sb = new StringBuilder();
        MansrtspHelper.appendReverseScale(sb, 1.0);
        String result = sb.toString();
        assertTrue(result.contains("-1"), "倒放应至少支持scale=-1");
    }

    @Test
    @DisplayName("空StringBuilder安全")
    void testNullSafe() {
        MansrtspHelper.appendScale(null, 1.0); // 不应抛异常
        MansrtspHelper.appendReverseScale(null, 1.0);
        assertTrue(true);
    }
}
