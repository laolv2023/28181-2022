package com.genersoft.iot.vmp.gb28181.transmit.cmd.impl;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.SipLayer;
import com.genersoft.iot.vmp.gb28181.transmit.SIPSender;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.ISIPCommander;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.SIPRequestHeaderProvider;
import com.genersoft.iot.vmp.gb28181.utils.SipCharsetHelper;
import com.genersoft.iot.vmp.utils.SipUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.SipException;
import javax.sip.InvalidArgumentException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.util.UUID;

/**
 * SIPCommander GB/T 28181-2022 升级改造 —— XML 构造与 SIP 下发方法实现
 *
 * <p>依据: 《WVP前端UI改造方案》D4修复、《WVP合规性升级改造开发方案》改造项7~15</p>
 * <p>说明: 本文件包含 10 个新增方法的 XML 构造逻辑骨架。</p>
 * <p>集成方式: 将本文件中的方法复制到 {@code SIPCommander.java} 中，或通过组合方式注入。</p>
 *
 * <p>XML 元素名大小写严格遵循 GB/T 28181-2022 规范原文:</p>
 * <ul>
 *   <li>PTzPrecisectrl — P大写、T大写、z小写</li>
 *   <li>Tilt — T大写（PTZ精准控制中的垂直角元素）</li>
 *   <li>Devicecontrol — D大写、c小写</li>
 *   <li>HomepositionQuery — H大写、p小写</li>
 *   <li>cruiseTrackQuery — 全小写c开头</li>
 *   <li>pTZposition — p小写、TZ大写</li>
 *   <li>SDcardStatus — S大写、D大写、c小写</li>
 *   <li>Deviceupgrade — D大写、u小写</li>
 * </ul>
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */
@Slf4j
@Component
public class SIPCommander2022Supplement {

    @Autowired
    private SIPSender sipSender;

    @Autowired
    private SIPRequestHeaderProvider headerProvider;

    @Autowired
    private SipLayer sipLayer;

    // SN 序号（简单递增，生产环境应考虑线程安全和持久化）
    private static final String SN_PERSIST_FILE = System.getProperty("java.io.tmpdir") + "/wvp-sn-counter.properties";
    private static final java.util.concurrent.atomic.AtomicInteger snCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    private static synchronized int nextSn() {
        int sn = snCounter.incrementAndGet();
        persistSn(sn);
        return sn;
    }

    // 固件上传目录（可配置化）
    // 审计修复P1-13: 固件上传需校验文件大小(≤100MB)、类型(.bin/.img/.zip)、路径遍历
    private static final String FIRMWARE_UPLOAD_DIR = System.getProperty("wvp.firmware.dir", System.getProperty("java.io.tmpdir") + "/wvp/firmware");

    // ========================================================================
    // 一、PTZ 精准控制（改造项7，A.2.3.1.11）
    // ========================================================================

    /**
     * 构造并发送 PTZ 精准控制命令 XML
     *
     * <p>XML 结构:</p>
     * <pre>
     * &lt;Control&gt;
     *   &lt;CmdType&gt;DeviceControl&lt;/CmdType&gt;
     *   &lt;SN&gt;{sn}&lt;/SN&gt;
     *   &lt;DeviceID&gt;{deviceId}&lt;/DeviceID&gt;
     *   &lt;PTzPrecisectrl&gt;
     *     &lt;pan&gt;{pan}&lt;/pan&gt;
     *     &lt;Tilt&gt;{tilt}&lt;/Tilt&gt;
     *     &lt;zoom&gt;{zoom}&lt;/zoom&gt;
     *   &lt;/PTzPrecisectrl&gt;
     * &lt;/Control&gt;
     * </pre>
     *
     * 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11
     * 注意: Tilt 元素名中 T 大写（规范原文 spec_2022.txt 行2890）
     */
    public void ptzPreciseCmdImpl(Device device, String channelId, double pan, double tilt, double zoom) {
        if (device == null) {
            throw new IllegalArgumentException("device不能为null");
        }
        // PTZ参数范围校验
        if (pan < -180 || pan > 180) throw new IllegalArgumentException("pan范围: -180~180");
        if (tilt < -90 || tilt > 90) throw new IllegalArgumentException("tilt范围: -90~90");
        if (zoom < 0 || zoom > 20) throw new IllegalArgumentException("zoom范围: 0~20");
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<PTzPrecisectrl>\r\n");
        xml.append("<pan>").append(String.format(java.util.Locale.ROOT, "%.2f", pan)).append("</pan>\r\n");
        xml.append("<Tilt>").append(String.format(java.util.Locale.ROOT, "%.2f", tilt)).append("</Tilt>\r\n");
        xml.append("<zoom>").append(String.format(java.util.Locale.ROOT, "%.1f", zoom)).append("</zoom>\r\n");
        xml.append("</PTzPrecisectrl>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-PTZ精准控制] sn={}, deviceId={}, xml={}", sn, deviceId, xmlStr);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 二、存储卡格式化（改造项8，A.2.3.1.13）
    // ========================================================================

    /**
     * 存储卡格式化命令
     *
     * <p>XML 元素名: FormatSdcard，F大写、S大写、d小写（来源 spec_2022.txt 行2161）</p>
     *
     * 来源: 后端改造项8, 设计文档第10.1节, 2022版A.2.3.1.13
     */
    public void formatSdcardCmdImpl(Device device, String channelId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<FormatSdcard/>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-存储卡格式化] sn={}, deviceId={}", sn, deviceId);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 三、目标跟踪（改造项9，A.2.3.1.14）
    // ========================================================================

    /**
     * 目标跟踪命令
     *
     * <p>XML 元素名: TargetTrack，action 取值 Auto/Manual（来源 spec_2022.txt 行2178-2179）</p>
     *
     * 来源: 后端改造项9, 设计文档第10.1节, 2022版A.2.3.1.14
     */
    public void targetTrackCmdImpl(Device device, String channelId, String action) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<TargetTrack>").append(escapeXml(action)).append("</TargetTrack>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-目标跟踪] sn={}, deviceId={}, action={}", sn, deviceId, action);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 四、设备软件升级（改造项10，A.2.3.1.12）
    // ========================================================================

    /**
     * 设备软件升级命令
     *
     * <p>XML 元素名: Deviceupgrade（D大写、u小写，来源 spec_2022.txt 行2551）</p>
     * <p>字段大小写: FirmWare(W大写)、FileURL(u小写)、sessionID(s小写)</p>
     *
     * 来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12
     */
    public void deviceUpgradeCmdImpl(Device device, String channelId, String firmware,
            String fileUrl, String manufacturer, String sessionId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        // SSRF防护: 校验fileUrl不指向内网地址
        if (fileUrl != null && !fileUrl.isEmpty()) {
            try {
                java.net.URL url = new java.net.URL(fileUrl);
                java.net.InetAddress addr = java.net.InetAddress.getByName(url.getHost());
                if (addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isAnyLocalAddress()) {
                    throw new IllegalArgumentException("fileUrl不允许指向内网地址");
                }
            } catch (java.net.MalformedURLException e) {
                throw new IllegalArgumentException("fileUrl格式错误");
            } catch (java.net.UnknownHostException e) {
                throw new IllegalArgumentException("fileUrl主机无法解析");
            }
        }

        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<Deviceupgrade>\r\n");
        xml.append("<FirmWare>").append(escapeXml(firmware)).append("</FirmWare>\r\n");
        xml.append("<FileURL>").append(escapeXml(fileUrl)).append("</FileURL>\r\n");
        xml.append("<manufacturer>").append(escapeXml(manufacturer)).append("</manufacturer>\r\n");
        xml.append("<sessionID>").append(escapeXml(sessionId)).append("</sessionID>\r\n");
        xml.append("</Deviceupgrade>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-设备升级] sn={}, deviceId={}, firmware={}, sessionId={}",
                sn, deviceId, firmware, sessionId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * 固件文件上传处理
     *
     * 来源: 后端改造项10配套, 设计文档第10.1节
     */
    public String uploadFirmwareFileImpl(String deviceId, MultipartFile file) throws IOException {
        // 路径遍历防护: deviceId 只允许字母和数字
        if (deviceId == null || !deviceId.matches("^[a-zA-Z0-9]{1,32}$")) {
            throw new IllegalArgumentException("非法的 deviceId");
        }
        // 确保上传目录存在
        Path uploadDir = Paths.get(FIRMWARE_UPLOAD_DIR, deviceId);
        Files.createDirectories(uploadDir);

        // 文件类型白名单校验
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.matches(".*\.(bin|img|zip)$")) {
            throw new IllegalArgumentException("不支持的固件文件类型, 仅允许 .bin/.img/.zip");
        }
        // 文件大小校验 (≤100MB)
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("固件文件超过100MB限制");
        }
        // 生成唯一文件名（保留原始扩展名）
        String ext = "";
        if (originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String savedName = UUID.randomUUID().toString() + ext;
        Path targetPath = uploadDir.resolve(savedName);

        // 保存文件
        try (java.io.InputStream is = file.getInputStream()) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 返回文件访问 URL（实际项目应返回可通过 HTTP 访问的完整 URL）
        String fileUrl = "http://" + device.getIp() + ":" + device.getPort() + "/firmware/" + deviceId + "/" + savedName;
        log.info("[固件上传] deviceId={}, originalName={}, savedAs={}, fileUrl={}",
                deviceId, originalName, savedName, fileUrl);
        return fileUrl;
    }

    // ========================================================================
    // 五、2022版新增查询（改造项11~14）
    // ========================================================================

    /**
     * 看守位信息查询
     *
     * <p>CmdType: HomepositionQuery（H大写、p小写，来源 design_doc.md 行1112）</p>
     *
     * 来源: 后端改造项11, 设计文档第11.1节, 2022版A.2.4.10
     */
    public void homePositionQueryCmdImpl(Device device, String channelId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>HomepositionQuery</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-看守位查询] sn={}, deviceId={}", sn, deviceId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * 巡航轨迹查询
     *
     * <p>CmdType: cruiseTrackQuery（全小写c开头，来源 design_doc.md 行1159）</p>
     *
     * 来源: 后端改造项12, 设计文档第11.1节, 2022版A.2.4.11
     */
    public void cruiseTrackQueryCmdImpl(Device device, String channelId, Integer trackListId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>cruiseTrackQuery</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        if (trackListId != null) {
            xml.append("<CruiseTrackListID>").append(trackListId).append("</CruiseTrackListID>\r\n");
        }
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-巡航轨迹查询] sn={}, deviceId={}, trackListId={}", sn, deviceId, trackListId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * PTZ 精准状态查询
     *
     * <p>CmdType: pTZposition（p小写、TZ大写，来源 design_doc.md 行1189）</p>
     *
     * 来源: 后端改造项13, 设计文档第11.1节, 2022版A.2.4.13
     */
    public void ptzPreciseStatusQueryCmdImpl(Device device, String channelId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>pTZposition</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-PTZ精准状态查询] sn={}, deviceId={}", sn, deviceId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * 存储卡状态查询
     *
     * <p>CmdType: SDcardStatus（S大写、D大写、c小写，来源 design_doc.md 行1216）</p>
     *
     * 来源: 后端改造项14, 设计文档第11.1节, 2022版A.2.4.14
     */
    public void storageCardStatusQueryCmdImpl(Device device, String channelId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>SDcardStatus</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-存储卡状态查询] sn={}, deviceId={}", sn, deviceId);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 六、图像抓拍配置（改造项15，2022版9.14）
    // ========================================================================

    /**
     * 图像抓拍配置命令
     *
     * <p>XML 字段: snapNum(小写n), Interval(I大写), uploaduRL(u小写), sessionID(s小写)</p>
     * <p>注: Resolution 字段为后端扩展（前端传入），规范中无此字段，但已在实际 Handler 中实现</p>
     *
     * 来源: 后端改造项15, 设计文档第12.2节, 2022版9.14
     */
    public void snapshotConfigCmdImpl(Device device, String channelId, int resolution,
            int snapNum, int interval, String uploadUrl, String sessionId) {
        if (device == null) { throw new IllegalArgumentException("device不能为null"); }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(escapeXml(String.valueOf(sn))).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<SnapConfig>\r\n");
        xml.append("<Resolution>").append(resolution).append("</Resolution>\r\n");
        xml.append("<snapNum>").append(snapNum).append("</snapNum>\r\n");
        xml.append("<Interval>").append(interval).append("</Interval>\r\n");
        xml.append("<uploaduRL>").append(escapeXml(uploadUrl != null ? uploadUrl : "")).append("</uploaduRL>\r\n");
        xml.append("<sessionID>").append(escapeXml(sessionId)).append("</sessionID>\r\n");
        xml.append("</SnapConfig>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.info("[SIP-图像抓拍配置] sn={}, deviceId={}, resolution={}, snapNum={}",
                sn, deviceId, resolution, snapNum);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /**
     * XML 特殊字符转义（防注入）
     * 转义规则: & → &amp;  < → &lt;  > → &gt;  " → &quot;  ' → &apos;
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeXml(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 20);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:   sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * 通过 JAIN-SIP 发送 MESSAGE 到设备
     *
     * <p>实际实现需调用 WVP 现有的 SIP 消息发送基础设施（如 {@code SipSender}），
     * 此处为骨架方法，集成时将 xml 字符串作为 MESSAGE body 发送</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     * @param xml       GB/T 28181 格式的 XML 消息体
     */
    private void sendSipMessage(Device device, String channelId, String xml) {
        try {
            Request request = headerProvider.createMessageRequest(
                    device, xml,
                    SipUtils.getNewViaTag(),
                    SipUtils.getNewFromTag(),
                    null,
                    sipSender.getNewCallIdHeader(
                            sipLayer.getLocalIp(device.getLocalIp()),
                            device.getTransport()
                    )
            );
            sipSender.transmitRequest(
                    sipLayer.getLocalIp(device.getLocalIp()),
                    request
            );
        } catch (SipException | ParseException | InvalidArgumentException e) {
            log.error("[SIP发送] 消息发送失败: device={}, channelId={}", device.getDeviceId(), channelId, e);
        }
    }
    private static void persistSn(int sn) {
        try {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("sn", String.valueOf(sn));
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(SN_PERSIST_FILE)) {
                props.store(fos, "WVP SN Counter");
            }
        } catch (Exception ignored) {}
    }
}
