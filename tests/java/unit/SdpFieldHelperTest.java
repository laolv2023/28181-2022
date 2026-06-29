package com.genersoft.iot.vmp.gb28181.utils;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SDP字段改造测试
 * 测试用例: A3.1-01, A3.2-01, A3.3-01, B8-01~08
 * 来源: 改造项4-6,18-23, 设计文档第7节
 */
@DisplayName("SDP字段改造测试")
class SdpFieldHelperTest {

    @Test
    @DisplayName("A3.1-01: s字段DoWnload大写W")
    void testAppendDownloadSField() {
        // 来源: 改造项4, 设计文档第7节
        // 规范要求: 2022版s字段"Download"改为"DoWnload"(大写W)
        StringBuilder sb = new StringBuilder();
        SdpFieldHelper.appendDownloadSField(sb);
        String sdp = sb.toString();
        assertTrue(sdp.contains("s=DoWnload"), "s字段应包含DoWnload(大写W)");
        assertFalse(sdp.contains("s=Download"), "s字段不应为Download(全小写)");
    }

    @Test
    @DisplayName("A3.1-02: Talk操作类型删除")
    void testIsTalkType() {
        // 来源: 改造项5, 2022版删除Talk操作类型
        assertFalse(SdpFieldHelper.isTalkType("Play"), "Play类型应返回false");
        assertFalse(SdpFieldHelper.isTalkType("Playback"), "Playback类型应返回false");
        assertFalse(SdpFieldHelper.isTalkType("Download"), "Download类型应返回false");
    }

    @Test
    @DisplayName("A3.2-01: a=doWnloadspeed大写W")
    void testAppendDownloadSpeed() {
        // 来源: 改造项6, 2022版a=downloadspeed改为a=doWnloadspeed(大写W)
        StringBuilder sb = new StringBuilder();
        SdpFieldHelper.appendDownloadSpeed(sb, 4.0);
        String sdp = sb.toString();
        assertTrue(sdp.contains("a=doWnloadspeed:"), "应包含a=doWnloadspeed(大写W)");
        assertFalse(sdp.contains("a=downloadspeed:"), "不应为a=downloadspeed(全小写)");
    }

    @Test
    @DisplayName("A3.3-01: f字段格式")
    void testBuildFField() {
        // 来源: 改造项18, 设计文档第7节
        // f=v/编码格式/分辨率/帧率/码率类型/码率大小a/编码格式/码率大小/采样率
        String f = SdpFieldHelper.buildFField("H265", "1920x1080", 25, 1, 4096, "AAC", 128, 44100);
        assertNotNull(f, "f字段不应为null");
        assertTrue(f.startsWith("f="), "f字段应以f=开头");
        assertTrue(f.contains("H265"), "f字段应包含H265编码");
    }

    @Test
    @DisplayName("B8-05: a=filesize参数")
    void testAppendFileSize() {
        StringBuilder sb = new StringBuilder();
        SdpFieldHelper.appendFileSize(sb, 1024000L);
        assertTrue(sb.toString().contains("a=filesize:1024000"), "应包含a=filesize参数");
    }

    @Test
    @DisplayName("B8-06: a=ssvcratio参数")
    void testAppendSsvcratio() {
        StringBuilder sb = new StringBuilder();
        SdpFieldHelper.appendSsvcratio(sb, "1:1");
        assertTrue(sb.toString().contains("a=ssvcratio:1:1"), "应包含a=ssvcratio参数");
    }

    @Test
    @DisplayName("B8-07: a=streamnumber参数")
    void testAppendStreamNumber() {
        StringBuilder sb = new StringBuilder();
        SdpFieldHelper.appendStreamNumber(sb, 2);
        assertTrue(sb.toString().contains("a=streamnumber:2"), "应包含a=streamnumber参数");
    }

    @Test
    @DisplayName("A3.2-02: 488错误响应处理")
    void testHandleSdpErrorResponse() {
        // 来源: 改造项22, 488 Not Acceptable Here
        int code = SdpFieldHelper.handleSdpErrorResponse(488, "Unsupported codec");
        assertEquals(488, code, "488错误应返回488");
    }

    @Test
    @DisplayName("B8-08: u字段下载类型")
    void testAppendUField() {
        // 来源: 改造项23, u=deviceId:downloadType
        StringBuilder sb = new StringBuilder();
        SdpFieldHelper.appendUField(sb, 0, "34020000001320000001");
        String u = sb.toString();
        assertTrue(u.contains("u="), "应包含u=字段");
        assertTrue(u.contains("34020000001320000001"), "u字段应包含设备ID");
    }

    @Test
    @DisplayName("空StringBuilder安全")
    void testNullSafe() {
        SdpFieldHelper.appendDownloadSField(null); // 不应抛异常
        SdpFieldHelper.appendDownloadSpeed(null, 1.0);
        SdpFieldHelper.appendFileSize(null, 0);
        // 如果没有异常则通过
        assertTrue(true);
    }
}
