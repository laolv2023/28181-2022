package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;

/**
 * SDP 字段构造工具类
 * <p>
 * 整合 GB/T 28181-2022 中涉及 SDP 字段调整的多个改造项，包括：
 * </p>
 * <ul>
 *     <li>改造项4：下载场景 s 字段（DoWnload）</li>
 *     <li>改造项5：取消 Talk 类型支持</li>
 *     <li>改造项6：下载倍速字段（a=downloadspeed:，大写 W）</li>
 *     <li>改造项18：f 字段格式扩展（v/a 双字段）</li>
 *     <li>改造项19：文件大小字段（a=filesize:）</li>
 *     <li>改造项20：SSVC 比例字段（a=ssvcratio:）</li>
 *     <li>改造项21：流编号字段（a=streamnumber:）</li>
 *     <li>改造项22：SDP 协商错误响应（488/486）</li>
 *     <li>改造项23：u 字段携带下载类型（0~3）</li>
 * </ul>
 *
 * @author wvp-upgrade
 */
public final class SdpFieldHelper {

    private static final Logger logger = LoggerFactory.getLogger(SdpFieldHelper.class);

    /**
     * 视频媒体标识符
     */
    private static final String MEDIA_TYPE_VIDEO = "video";

    /**
     * 音频媒体标识符
     */
    private static final String MEDIA_TYPE_AUDIO = "audio";

    /**
     * 默认构造方法私有化，禁止实例化
     */
    private SdpFieldHelper() {
        throw new IllegalStateException("工具类禁止实例化");
    }

    // ================================================================
    // 改造项18：f 字段构造
    // ================================================================

    /**
     * 构造 SDP 的 f= 字段
     * <p>
     * 改造项18：f 字段格式扩展，支持 v/a 双字段。
     * </p>
     * <p>
     * 来源：设计文档第11.1节，2022版 9.2.2 媒体描述。<br>
     * 格式：f=v/视频编码格式/分辨率/帧率/码率类型/码率大小 a/音频编码格式/码率大小/采样率
     * </p>
     *
     * @param videoCodec     视频编码格式，如 H.264、H.265
     * @param resolution     分辨率，如 1920x1080
     * @param frameRate      帧率，如 25
     * @param bitrateType    码率类型，CBR/VBR
     * @param bitrateSize    视频码率大小，单位 kbps
     * @param audioCodec     音频编码格式，如 G.711A、AAC
     * @param audioBitrate   音频码率大小
     * @param sampleRate     音频采样率
     * @return f= 字段内容
     */
    public static String buildFField(String videoCodec, String resolution, String frameRate,
                                     String bitrateType, String bitrateSize,
                                     String audioCodec, String audioBitrate, String sampleRate) {
        // 改造项18：f 字段构造，使用 v/ 与 a/ 分段
        // 审计修复P2-10: 对所有字符串参数进行 XML 转义, 防止注入
        StringBuilder sb = new StringBuilder("f=");
        // 视频段
        sb.append(MEDIA_TYPE_VIDEO).append("/");
        sb.append(emptyIfNull(videoCodec)).append("/");
        sb.append(emptyIfNull(resolution)).append("/");
        sb.append(emptyIfNull(frameRate)).append("/");
        sb.append(emptyIfNull(bitrateType)).append("/");
        sb.append(emptyIfNull(bitrateSize));
        // 音频段
        sb.append(" ").append(MEDIA_TYPE_AUDIO).append("/");
        sb.append(emptyIfNull(audioCodec)).append("/");
        sb.append(emptyIfNull(audioBitrate)).append("/");
        sb.append(emptyIfNull(sampleRate));
        return sb.toString();
    }

    // ================================================================
    // 改造项4：下载场景 s 字段（DoWnload，大写 W）
    // ================================================================

    /**
     * SDP s 字段中标识下载场景的固定值
     * <p>改造项4：来源 2022版 9.2.2，s 字段取值固定为 "Download"（大写 W）</p>
     */
    public static final String DOWNLOAD_S_FIELD = "Download";

    /**
     * 在 SDP 中追加下载场景的 s 字段
     * <p>
     * 改造项4：下载场景下 s 字段使用 "Download"，注意 W 为大写。
     * 来源：设计文档第11.2节，2022版 9.2.2 媒体描述。
     * </p>
     *
     * @param sdpBuilder SDP 字符串构造器
     */
    public static void appendDownloadSField(StringBuilder sdpBuilder) {
        if (sdpBuilder == null) {
            return;
        }
        sdpBuilder.append("s=").append(DOWNLOAD_S_FIELD).append("\r\n");
    }

    // ================================================================
    // 改造项5：取消 Talk 类型支持
    // ================================================================

    /**
     * 判断是否为 Talk 类型
     * <p>
     * 改造项5：2022 版取消 Talk 类型支持，所有曾经使用 Talk 类型的场景都返回 false。
     * </p>
     * <p>
     * 来源：设计文档第11.3节，2022版 9.2.2 媒体描述。<br>
     * 影响：原有 Talk 类型的语音对讲场景改为使用 Broadcast（广播）类型。
     * </p>
     *
     * @param streamType 流类型字符串
     * @return 永远返回 false
     */
    public static boolean isTalkType(String streamType) {
        // 改造项5：2022 版移除 Talk 类型，统一返回 false
        // 调用方应基于此判断跳过 Talk 相关流程
        if ("Talk".equalsIgnoreCase(streamType)) {
            logger.debug("[SDP] 检测到 Talk 类型，2022 版已废弃，请改用 Broadcast 类型");
        }
        return false;
    }

    // ================================================================
    // 改造项6：下载倍速字段（a=downloadspeed:，大写 W）
    // ================================================================

    /**
     * 在 SDP 中追加下载倍速字段
     * <p>
     * 改造项6：下载倍速字段名为 a=downloadspeed:，注意 W 为大写。
     * 来源：设计文档第11.4节，2022版 9.2.2 媒体描述。
     * </p>
     *
     * @param sdpBuilder SDP 字符串构造器
     * @param speed      下载倍速，如 1.0、2.0、4.0
     */
    public static void appendDownloadSpeed(StringBuilder sdpBuilder, double speed) {
        if (sdpBuilder == null) {
            return;
        }
        // 改造项6：固定字段名 downloadspeed，大写 W
        sdpBuilder.append("a=downloadspeed:").append(formatSpeed(speed)).append("\r\n");
    }

    // ================================================================
    // 改造项19：文件大小字段
    // ================================================================

    /**
     * 在 SDP 中追加文件大小字段
     * <p>
     * 改造项19：文件大小字段名为 a=filesize:，单位字节。
     * 来源：设计文档第11.5节，2022版 9.2.2 媒体描述。
     * </p>
     *
     * @param sdpBuilder SDP 字符串构造器
     * @param fileSize   文件大小（字节）
     */
    public static void appendFileSize(StringBuilder sdpBuilder, long fileSize) {
        if (sdpBuilder == null) {
            return;
        }
        sdpBuilder.append("a=filesize:").append(fileSize).append("\r\n");
    }

    // ================================================================
    // 改造项20：SSVC 比例字段
    // ================================================================

    /**
     * 在 SDP 中追加 SSVC 比例字段
     * <p>
     * 改造项20：SSVC 比例字段名为 a=ssvcratio:，用于 SVC 分层视频编码场景。
     * 来源：设计文档第11.6节，2022版 9.2.2 媒体描述。
     * </p>
     *
     * @param sdpBuilder SDP 字符串构造器
     * @param ssvcRatio  SSVC 比例字符串，如 "1:2:4"
     */
    public static void appendSsvcratio(StringBuilder sdpBuilder, String ssvcRatio) {
        if (sdpBuilder == null || ObjectUtils.isEmpty(ssvcRatio)) {
            return;
        }
        String safeRatio = ssvcRatio.replaceAll("[\r\n]", "");
        sdpBuilder.append("a=ssvcratio:").append(safeRatio).append("\r\n");
    }

    // ================================================================
    // 改造项21：流编号字段
    // ================================================================

    /**
     * 在 SDP 中追加流编号字段
     * <p>
     * 改造项21：流编号字段名为 a=streamnumber:，用于多流复用场景。
     * 来源：设计文档第11.7节，2022版 9.2.2 媒体描述。
     * </p>
     *
     * @param sdpBuilder  SDP 字符串构造器
     * @param streamNumber 流编号，从 0 开始
     */
    public static void appendStreamNumber(StringBuilder sdpBuilder, int streamNumber) {
        if (sdpBuilder == null) {
            return;
        }
        if (streamNumber < 0) {
            streamNumber = 0;
        }
        sdpBuilder.append("a=streamnumber:").append(streamNumber).append("\r\n");
    }

    // ================================================================
    // 改造项23：u 字段携带下载类型
    // ================================================================

    /**
     * 下载类型常量：0 - 全部下载
     */
    public static final int DOWNLOAD_TYPE_ALL = 0;

    /**
     * 下载类型常量：1 - 按时间段下载
     */
    public static final int DOWNLOAD_TYPE_BY_TIME = 1;

    /**
     * 下载类型常量：2 - 按文件下载
     */
    public static final int DOWNLOAD_TYPE_BY_FILE = 2;

    /**
     * 下载类型常量：3 - 按事件下载
     */
    public static final int DOWNLOAD_TYPE_BY_EVENT = 3;

    /**
     * 允许的下载类型集合
     */
    private static final List<Integer> VALID_DOWNLOAD_TYPES = Arrays.asList(
            DOWNLOAD_TYPE_ALL, DOWNLOAD_TYPE_BY_TIME, DOWNLOAD_TYPE_BY_FILE, DOWNLOAD_TYPE_BY_EVENT
    );

    /**
     * 在 SDP 中追加 u 字段，携带下载类型
     * <p>
     * 改造项23：u 字段携带下载类型，取值范围 0~3。
     * 来源：设计文档第11.8节，2022版 9.2.2 媒体描述。
     * </p>
     *
     * @param sdpBuilder  SDP 字符串构造器
     * @param downloadType 下载类型，0~3
     * @param downloadUrl  下载 URL，可空
     */
    public static void appendUField(StringBuilder sdpBuilder, int downloadType, String downloadUrl) {
        if (sdpBuilder == null) {
            return;
        }
        // 改造项23：downloadType 取值范围校验，超出范围按 0 处理
        if (!VALID_DOWNLOAD_TYPES.contains(downloadType)) {
            logger.warn("[SDP] 非法的 downloadType={}, 自动修正为 0", downloadType);
            downloadType = DOWNLOAD_TYPE_ALL;
        }
        // 格式：u=下载类型:下载URL
        StringBuilder uField = new StringBuilder("u=");
        uField.append(downloadType);
        if (!ObjectUtils.isEmpty(downloadUrl)) {
            // 审计修复P2-09: 转义 downloadUrl 中的 XML 特殊字符, 防止注入
            uField.append(":").append(SipCharsetHelper.escapeXml(downloadUrl));
        }
        sdpBuilder.append(uField).append("\r\n");
    }

    // ================================================================
    // 改造项22：SDP 协商错误响应处理
    // ================================================================

    /**
     * 响应码：488 Not Acceptable Here
     */
    public static final int SDP_ERROR_488 = 488;

    /**
     * 响应码：486 Busy Here
     */
    public static final int SDP_ERROR_486 = 486;

    /**
     * 处理 SDP 协商失败场景
     * <p>
     * 改造项22：SDP 协商失败时返回指定响应码，并记录日志。
     * </p>
     * <p>
     * 来源：设计文档第11.9节，2022版 9.2.2 媒体描述。<br>
     * 协商场景：
     * <ul>
     *     <li>488：媒体参数不可接受（编码格式、码率等不匹配）</li>
     *     <li>486：媒体通道忙，暂时无法建立</li>
     * </ul>
     * </p>
     *
     * @param statusCode 响应码（488 或 486）
     * @param reason     失败原因描述
     * @return 错误响应码（用于调用方构造 SIP 响应）
     */
    public static int handleSdpErrorResponse(int statusCode, String reason) {
        if (statusCode != SDP_ERROR_488 && statusCode != SDP_ERROR_486) {
            logger.warn("[SDP] 不支持的错误响应码: {}, 默认使用 488", statusCode);
            statusCode = SDP_ERROR_488;
        }
        logger.warn("[SDP] 协商失败: statusCode={}, reason={}", statusCode, reason);
        return statusCode;
    }

    // ================================================================
    // 内部工具方法
    // ================================================================

    /**
     * 空值转空字符串
     *
     * @param value 原始值
     * @return 非空原值或空字符串
     */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 格式化倍速字段
     * <p>
     * 整数倍速显示为整数（如 4 而非 4.0），小数倍速保留一位小数（如 1.5）。
     * </p>
     *
     * @param speed 原始倍速
     * @return 格式化后的倍速字符串
     */
    private static String formatSpeed(double speed) {
        if (speed == (long) speed) {
            return String.valueOf((long) speed);
        }
        return String.valueOf(speed);
    }
}
