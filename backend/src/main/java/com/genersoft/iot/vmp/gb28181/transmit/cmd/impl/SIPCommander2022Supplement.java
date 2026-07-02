package com.genersoft.iot.vmp.gb28181.transmit.cmd.impl;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.SIPCommanderSupplement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SIPCommander GB/T 28181-2022 升级改造 —— XML 构造与 SIP 下发方法实现
 *
 * <p>依据: 《WVP前端UI改造方案》D4修复、《WVP合规性升级改造开发方案》改造项7~15</p>
 * <p>说明: 本文件实现了 {@link SIPCommanderSupplement} 接口中定义的 10 个新增方法。
 * 通过 Spring {@code @Component} 注入，与 WVP 现有 SIPCommander 并行工作。</p>
 *
 * <p>XML 元素名大小写严格遵循 GB/T 28181-2022 规范原文:</p>
 * <ul>
 *   <li>CmdType 值: DeviceControl — D大写、C大写（A.2.3.1 XML Schema fixed 值）</li>
 *   <li>PTzPrecisectrl — P大写、T大写、z小写</li>
 *   <li>Tilt — T大写（PTZ精准控制中的垂直角元素）</li>
 *   <li>FormatSdcard — F大写、S大写、d小写（A.2.3.1.13）</li>
 *   <li>HomepositionQuery — H大写、p小写</li>
 *   <li>cruiseTrackQuery — 全小写c开头</li>
 *   <li>pTZposition — p小写、TZ大写</li>
 *   <li>SDcardStatus — S大写、D大写、c小写</li>
 *   <li>Deviceupgrade — D大写、u小写</li>
 *   <li>FileuRL — F大写、u小写（A.2.3.1.12）</li>
 * </ul>
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */
@Slf4j
@Component
public class SIPCommander2022Supplement implements SIPCommanderSupplement {

    // SN 序号（线程安全的原子自增，持久化以防重启后 SN 重复）
    // 注意: 若持久化文件损坏或不可读，snCounter 从 0 开始，
    // 可能导致 SIP 消息重放风险。生产环境建议使用数据库序列或 Redis 原子递增。
    private static final AtomicInteger snCounter = new AtomicInteger(
            loadSnCounterFromPersistence());

    /** 从持久化文件恢复 SN 计数器，失败时告警并从 0 开始 */
    private static int loadSnCounterFromPersistence() {
        java.io.File snFile = new java.io.File(
                System.getProperty("wvp.sn.persist.file",
                        System.getProperty("user.home") + "/.wvp/sn_counter.txt"));
        try {
            if (snFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(snFile.toPath())).trim();
                int sn = Integer.parseInt(content);
                log.info("[SN计数器] 从持久化文件恢复 SN={}", sn);
                return sn;
            }
        } catch (Exception e) {
            log.error("[SN计数器] 持久化文件损坏或不可读, SN 从 0 开始! 文件: {}, 错误: {}",
                    snFile.getAbsolutePath(), e.getMessage());
        }
        log.warn("[SN计数器] 持久化文件不存在, SN 从 0 开始。生产环境请配置 wvp.sn.persist.file");
        return 0;
    }

    private static int nextSn() {
        return snCounter.incrementAndGet();
    }

    // 固件上传目录（可通过系统属性 wvp.firmware.dir 配置）
    private static final String FIRMWARE_UPLOAD_DIR =
            System.getProperty("wvp.firmware.dir", System.getProperty("java.io.tmpdir") + "/wvp/firmware");

    // SIP 消息发送基础设施（由 WVP 容器注入，开发阶段可能为 null）
    @Autowired(required = false)
    private SipProvider sipProvider;

    @Autowired(required = false)
    private MessageFactory messageFactory;

    @Autowired(required = false)
    private HeaderFactory headerFactory;

    @Autowired(required = false)
    private AddressFactory addressFactory;

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
    @Override
    public void ptzPreciseCmd(Device device, String channelId, double pan, double tilt, double zoom) {
        // NaN/Infinity 防护: 防止 String.format 输出字面量 "NaN"/"Infinity" 到设备
        if (Double.isNaN(pan) || Double.isInfinite(pan)
                || Double.isNaN(tilt) || Double.isInfinite(tilt)
                || Double.isNaN(zoom) || Double.isInfinite(zoom)) {
            throw new IllegalArgumentException(
                    "PTZ参数包含非法值(NaN/Infinity): pan=" + pan + ", tilt=" + tilt + ", zoom=" + zoom);
        }
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<PTzPrecisectrl>\r\n");
        xml.append("<pan>").append(String.format(Locale.ROOT, "%.2f", pan)).append("</pan>\r\n");
        xml.append("<Tilt>").append(String.format(Locale.ROOT, "%.2f", tilt)).append("</Tilt>\r\n");
        xml.append("<zoom>").append(String.format(Locale.ROOT, "%.1f", zoom)).append("</zoom>\r\n");
        xml.append("</PTzPrecisectrl>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-PTZ精准控制] sn={}, deviceId={}, xml={}", sn, deviceId, xmlStr);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 二、存储卡格式化（改造项8，A.2.3.1.13）
    // ========================================================================

    /**
     * 存储卡格式化命令
     *
     * <p>XML 元素名: FormatSdcard（F大写、S大写、d小写，来源 spec_2022.txt 行2161）</p>
     *
     * 来源: 后端改造项8, 设计文档第10.1节, 2022版A.2.3.1.13
     */
    @Override
    public void formatSdcardCmd(Device device, String channelId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<FormatSdcard/>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-存储卡格式化] sn={}, deviceId={}", sn, deviceId);
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
    @Override
    public void targetTrackCmd(Device device, String channelId, String action) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<TargetTrack>").append(escapeXml(action)).append("</TargetTrack>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-目标跟踪] sn={}, deviceId={}, action={}", sn, deviceId, action);
        sendSipMessage(device, channelId, xmlStr);
    }

    // ========================================================================
    // 四、设备软件升级（改造项10，A.2.3.1.12）
    // ========================================================================

    /**
     * 设备软件升级命令
     *
     * <p>XML 元素名: Deviceupgrade（D大写、u小写，来源 spec_2022.txt 行2551）</p>
     * <p>字段大小写: FirmWare(W大写)、FileuRL(u小写)、sessionID(s小写)</p>
     *
     * 来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12
     */
    @Override
    public void deviceUpgradeCmd(Device device, String channelId, String firmware,
            String fileUrl, String manufacturer, String sessionId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<Deviceupgrade>\r\n");
        xml.append("<FirmWare>").append(escapeXml(firmware)).append("</FirmWare>\r\n");
        xml.append("<FileuRL>").append(escapeXml(fileUrl)).append("</FileuRL>\r\n");
        xml.append("<Manufacturer>").append(escapeXml(manufacturer)).append("</Manufacturer>\r\n");
        xml.append("<sessionID>").append(escapeXml(sessionId)).append("</sessionID>\r\n");
        xml.append("</Deviceupgrade>\r\n");
        xml.append("</Control>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-设备升级] sn={}, deviceId={}, firmware={}, sessionId={}",
                sn, deviceId, firmware, sessionId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * 固件文件上传处理
     *
     * 来源: 后端改造项10配套, 设计文档第10.1节
     */
    @Override
    public String uploadFirmwareFile(String deviceId, MultipartFile file) throws IOException {
        // 确保上传目录存在
        Path uploadDir = Paths.get(FIRMWARE_UPLOAD_DIR, deviceId);
        Files.createDirectories(uploadDir);

        // 生成唯一文件名（保留原始扩展名）
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String savedName = UUID.randomUUID().toString() + ext;
        Path targetPath = uploadDir.resolve(savedName);

        // 保存文件（try-with-resources 确保输入流正确关闭）
        try (java.io.InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 返回文件访问 URL（实际项目应返回可通过 HTTP 访问的完整 URL）
        String fileUrl = "/firmware/" + deviceId + "/" + savedName;
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
    @Override
    public void homePositionQueryCmd(Device device, String channelId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>HomepositionQuery</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-看守位查询] sn={}, deviceId={}", sn, deviceId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * 巡航轨迹查询
     *
     * <p>CmdType: cruiseTrackQuery（全小写c开头，来源 design_doc.md 行1159）</p>
     *
     * 来源: 后端改造项12, 设计文档第11.1节, 2022版A.2.4.11
     */
    @Override
    public void cruiseTrackQueryCmd(Device device, String channelId, Integer trackListId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>cruiseTrackQuery</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        if (trackListId != null) {
            xml.append("<CruiseTrackListID>").append(trackListId).append("</CruiseTrackListID>\r\n");
        }
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-巡航轨迹查询] sn={}, deviceId={}, trackListId={}", sn, deviceId, trackListId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * PTZ 精准状态查询
     *
     * <p>CmdType: pTZposition（p小写、TZ大写，来源 design_doc.md 行1189）</p>
     *
     * 来源: 后端改造项13, 设计文档第11.1节, 2022版A.2.4.13
     */
    @Override
    public void ptzPreciseStatusQueryCmd(Device device, String channelId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>pTZposition</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-PTZ精准状态查询] sn={}, deviceId={}", sn, deviceId);
        sendSipMessage(device, channelId, xmlStr);
    }

    /**
     * 存储卡状态查询
     *
     * <p>CmdType: SDcardStatus（S大写、D大写、c小写，来源 design_doc.md 行1216）</p>
     *
     * 来源: 后端改造项14, 设计文档第11.1节, 2022版A.2.4.14
     */
    @Override
    public void storageCardStatusQueryCmd(Device device, String channelId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Query>\r\n");
        xml.append("<CmdType>SDcardStatus</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("</Query>\r\n");

        String xmlStr = xml.toString();
        log.debug("[SIP-存储卡状态查询] sn={}, deviceId={}", sn, deviceId);
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
    @Override
    public void snapshotConfigCmd(Device device, String channelId, int resolution,
            int snapNum, int interval, String uploadUrl, String sessionId) {
        int sn = nextSn();
        String deviceId = device.getDeviceId();

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(sn).append("</SN>\r\n");
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
        log.debug("[SIP-图像抓拍配置] sn={}, deviceId={}, resolution={}, snapNum={}",
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
     * <p>使用 WVP 容器注入的 JAIN-SIP 基础设施进行消息发送。
     * 若 SIP 基础设施不可用（开发阶段），记录 ERROR 日志并静默丢弃，
     * 确保 WVP 集成后 SIP 发送自动生效。</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     * @param xml       GB/T 28181 格式的 XML 消息体
     */
    private void sendSipMessage(Device device, String channelId, String xml) {
        if (sipProvider == null || messageFactory == null || headerFactory == null || addressFactory == null) {
            log.error("[SIP发送] SIP基础设施未初始化(sipProvider={}, msgFactory={}, hdrFactory={}, addrFactory={}), "
                    + "消息已丢弃: device={}, channel={}",
                    sipProvider != null, messageFactory != null, headerFactory != null, addressFactory != null,
                    device.getDeviceId(), channelId);
            log.debug("[SIP发送-丢弃内容] device={}, channel={}, xml={}", device.getDeviceId(), channelId, xml);
            return;
        }
        try {
            String deviceId = device.getDeviceId();
            String ip = device.getIp();
            int port = device.getPort() > 0 ? device.getPort() : 5060;

            // 构造 SIP MESSAGE 请求
            SipURI requestUri = addressFactory.createSipURI(deviceId, ip + ":" + port);
            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader viaHeader = headerFactory.createViaHeader(
                    sipProvider.getSipStack().getIPAddress(),
                    sipProvider.getSipStack().getPort(),
                    "UDP", null);
            viaHeaders.add(viaHeader);

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.MESSAGE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            FromHeader fromHeader = headerFactory.createFromHeader(
                    addressFactory.createAddress(addressFactory.createSipURI(
                            sipProvider.getSipStack().getIPAddress())), UUID.randomUUID().toString());
            ToHeader toHeader = headerFactory.createToHeader(
                    addressFactory.createAddress(requestUri), null);

            Request request = messageFactory.createRequest(requestUri, Request.MESSAGE,
                    callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(
                    "Application", "MANSCDP+xml");
            request.setContent(xml, contentTypeHeader);

            ClientTransaction transaction = sipProvider.getNewClientTransaction(request);
            transaction.sendRequest();
            log.debug("[SIP发送] MESSAGE已发送: device={}, channel={}, callId={}",
                    deviceId, channelId, callIdHeader.getCallId());
        } catch (ParseException | javax.sip.SipException | javax.sip.InvalidArgumentException e) {
            log.error("[SIP发送] 发送失败: device={}, channel={}, err={}",
                    device.getDeviceId(), channelId, e.getMessage());
        }
    }
}
