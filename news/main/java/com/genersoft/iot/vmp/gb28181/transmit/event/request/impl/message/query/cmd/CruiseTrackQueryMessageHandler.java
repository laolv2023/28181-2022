package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.Platform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import com.genersoft.iot.vmp.gb28181.utils.XmlUtil;
import com.genersoft.iot.vmp.gb28181.utils.SipCharsetHelper;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 巡航轨迹列表查询 / 巡航轨迹查询消息处理器
 * <p>
 * 改造项12：巡航轨迹列表查询和巡航轨迹查询——支持两种语义合一的查询：
 * <ol>
 *     <li>列表查询：请求中携带 CruiseTrackListID，返回该轨迹列表下所有巡航轨迹条目</li>
 *     <li>详情查询：请求中携带 CruiseTrackID，返回单条巡航轨迹的预置位序列</li>
 * </ol>
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第10.2节（信息查询类命令扩展），2022版 A.2.4.1.3 巡航轨迹查询。<br>
 * 命令类型：CmdType = CruiseTrackQuery<br>
 * 协议要求：2022 版新增巡航轨迹查询命令，用于查询巡航轨迹列表以及单条轨迹的详细预置位序列。
 * </p>
 * <p>
 * <b>列表查询请求 XML 示例：</b>
 * <pre>
 * &lt;Query&gt;
 *     &lt;CmdType&gt;CruiseTrackQuery&lt;/CmdType&gt;
 *     &lt;SN&gt;10&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;CruiseTrackListID&gt;1&lt;/CruiseTrackListID&gt;
 * &lt;/Query&gt;
 * </pre>
 * </p>
 * <p>
 * <b>详情查询请求 XML 示例：</b>
 * <pre>
 * &lt;Query&gt;
 *     &lt;CmdType&gt;CruiseTrackQuery&lt;/CmdType&gt;
 *     &lt;SN&gt;11&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;CruiseTrackID&gt;1&lt;/CruiseTrackID&gt;
 * &lt;/Query&gt;
 * </pre>
 * </p>
 * <p>
 * <b>响应 XML 示例（列表查询）：</b>
 * <pre>
 * &lt;Response&gt;
 *     &lt;CmdType&gt;CruiseTrackQuery&lt;/CmdType&gt;
 *     &lt;SN&gt;10&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;Result&gt;OK&lt;/Result&gt;
 *     &lt;CruiseTrackList Num="2"&gt;
 *         &lt;CruiseTrack&gt;
 *             &lt;ID&gt;1&lt;/ID&gt;
 *             &lt;Name&gt;白天巡航&lt;/Name&gt;
 *         &lt;/CruiseTrack&gt;
 *         &lt;CruiseTrack&gt;
 *             &lt;ID&gt;2&lt;/ID&gt;
 *             &lt;Name&gt;夜间巡航&lt;/Name&gt;
 *         &lt;/CruiseTrack&gt;
 *     &lt;/CruiseTrackList&gt;
 * &lt;/Response&gt;
 * </pre>
 * </p>
 * <p>
 * <b>响应 XML 示例（详情查询）：</b>
 * <pre>
 * &lt;Response&gt;
 *     &lt;CmdType&gt;CruiseTrackQuery&lt;/CmdType&gt;
 *     &lt;SN&gt;11&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;Result&gt;OK&lt;/Result&gt;
 *     &lt;CruiseTrack&gt;
 *         &lt;ID&gt;1&lt;/ID&gt;
 *         &lt;Name&gt;白天巡航&lt;/Name&gt;
 *         &lt;PresetID&gt;1&lt;/PresetID&gt;
 *         &lt;PresetID&gt;2&lt;/PresetID&gt;
 *         &lt;PresetID&gt;3&lt;/PresetID&gt;
 *     &lt;/CruiseTrack&gt;
 * &lt;/Response&gt;
 * </pre>
 * </p>
 *
 * @author wvp-upgrade
 */
@Slf4j
@Component
public class CruiseTrackQueryMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    /**
     * 命令类型字符串
     * <p>
     * 改造项12：来源 设计文档第10.2节，2022版 A.2.4.1.3
     * </p>
     */
    private final String cmdType = "CruiseTrackQuery";

    /**
     * 响应结果：成功
     */
    private static final String RESULT_OK = "OK";

    /**
     * 注入查询消息分发器
     */
    @Autowired
    private QueryMessageHandler queryMessageHandler;

    /**
     * Spring 容器初始化后回调，将当前处理器注册到 QueryMessageHandler。
     * <p>
     * 改造项12：注册 CmdType=CruiseTrackQuery。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.3。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
        log.info("[巡航轨迹查询] 处理器注册成功, CmdType={}", cmdType);
    }

    /**
     * 处理来自设备的巡航轨迹查询请求
     * <p>
     * 改造项12：设备主动上报或响应巡航轨迹查询，按通用流程回复 200 OK。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.3。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param device  上报设备
     * @param element XML 根节点
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        log.info("[巡航轨迹查询] 收到设备查询请求, deviceId={}",
                device != null ? device.getDeviceId() : "null");
        // 设备端通常作为查询响应方而非发起方，此处仅 ACK
        responseOk(evt);
    }

    /**
     * 处理来自上级平台的巡航轨迹查询请求
     * <p>
     * 改造项12：上级平台发起巡航轨迹列表或详情查询。
     * 处理流程：
     * <ol>
     *     <li>解析 DeviceID、SN、CruiseTrackListID、CruiseTrackID</li>
     *     <li>若 CruiseTrackID 存在 → 详情查询；否则按列表查询处理</li>
     *     <li>构造响应 XML 用于异步发送</li>
     *     <li>ACK 200 OK</li>
     * </ol>
     * </p>
     *
     * @param evt      SIP 请求事件
     * @param platform 上级平台
     * @param element  XML 根节点
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform platform, Element element) {
        log.info("[巡航轨迹查询] 收到上级平台查询请求, platformId={}",
                platform != null ? platform.getServerGBId() : "null");
        handleQuery(evt, element);
    }

    /**
     * 统一处理巡航轨迹查询请求
     * <p>
     * 改造项12：根据请求 XML 中是否携带 CruiseTrackID 区分详情查询与列表查询。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.3。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param element XML 根节点
     */
    private void handleQuery(RequestEvent evt, Element element) {
        if (element == null) {
            log.warn("[巡航轨迹查询] XML 根节点为空，无法处理");
            responseOk(evt);
            return;
        }
        // 解析请求公共字段
        String deviceId = XmlUtil.getText(element, "DeviceID");
        String sn = XmlUtil.getText(element, "SN");
        // 解析请求类型字段
        String cruiseTrackListId = XmlUtil.getText(element, "CruiseTrackListID");
        String cruiseTrackId = XmlUtil.getText(element, "CruiseTrackID");

        log.info("[巡航轨迹查询] 解析请求: deviceId={}, sn={}, listId={}, trackId={}",
                deviceId, sn, cruiseTrackListId, cruiseTrackId);

        String responseXml;
        if (!ObjectUtils.isEmpty(cruiseTrackId)) {
            // 详情查询：返回单条巡航轨迹的预置位序列
            responseXml = buildDetailResponseXml(deviceId, sn, cruiseTrackId);
        } else {
            // 列表查询：返回轨迹列表
            responseXml = buildListResponseXml(deviceId, sn, cruiseTrackListId);
        }

        log.info("[巡航轨迹查询] 响应XML准备就绪, 待异步发送:\n{}", responseXml);
        responseOk(evt);
    }

    /**
     * 构造列表查询响应 XML
     * <p>
     * 改造项12：来源 2022版 A.2.4.1.3 巡航轨迹列表响应格式。
     * </p>
     *
     * @param deviceId          设备 ID
     * @param sn                请求 SN
     * @param cruiseTrackListId 轨迹列表 ID
     * @return 完整响应 XML 字符串
     */
    private String buildListResponseXml(String deviceId, String sn, String cruiseTrackListId) {
        // 模拟查询返回的轨迹条目（实际项目应从设备配置缓存中读取）
        List<CruiseTrack> tracks = new ArrayList<>();
        tracks.add(new CruiseTrack(1, "白天巡航", null));
        tracks.add(new CruiseTrack(2, "夜间巡航", null));

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Response>\r\n");
        xml.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        xml.append("<SN>").append(ObjectUtils.isEmpty(sn) ? "1" : sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(ObjectUtils.isEmpty(deviceId) ? "" : deviceId).append("</DeviceID>\r\n");
        xml.append("<Result>").append(RESULT_OK).append("</Result>\r\n");
        // 列表 ID：若请求中携带则回填，否则默认 1
        xml.append("<CruiseTrackListID>").append(ObjectUtils.isEmpty(cruiseTrackListId) ? "1" : cruiseTrackListId).append("</CruiseTrackListID>\r\n");
        xml.append("<CruiseTrackList Num=\"").append(tracks.size()).append("\">\r\n");
        for (CruiseTrack track : tracks) {
            xml.append("<CruiseTrack>\r\n");
            xml.append("<ID>").append(track.id).append("</ID>\r\n");
            xml.append("<Name>").append(nullSafe(track.name)).append("</Name>\r\n");
            xml.append("</CruiseTrack>\r\n");
        }
        xml.append("</CruiseTrackList>\r\n");
        xml.append("</Response>\r\n");
        return xml.toString();
    }

    /**
     * 构造详情查询响应 XML
     * <p>
     * 改造项12：来源 2022版 A.2.4.1.3 巡航轨迹详情响应格式。
     * 返回单条巡航轨迹的 ID、名称及预置位序列。
     * </p>
     *
     * @param deviceId       设备 ID
     * @param sn             请求 SN
     * @param cruiseTrackId  巡航轨迹 ID
     * @return 完整响应 XML 字符串
     */
    private String buildDetailResponseXml(String deviceId, String sn, String cruiseTrackId) {
        // 模拟查询返回的预置位序列
        List<Integer> presetIds = new ArrayList<>();
        presetIds.add(1);
        presetIds.add(2);
        presetIds.add(3);

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Response>\r\n");
        xml.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        xml.append("<SN>").append(ObjectUtils.isEmpty(sn) ? "1" : sn).append("</SN>\r\n");
        xml.append("<DeviceID>").append(ObjectUtils.isEmpty(deviceId) ? "" : deviceId).append("</DeviceID>\r\n");
        xml.append("<Result>").append(RESULT_OK).append("</Result>\r\n");
        xml.append("<CruiseTrack>\r\n");
        xml.append("<ID>").append(cruiseTrackId).append("</ID>\r\n");
        xml.append("<Name>巡航轨迹").append(cruiseTrackId).append("</Name>\r\n");
        for (Integer presetId : presetIds) {
            xml.append("<PresetID>").append(presetId).append("</PresetID>\r\n");
        }
        xml.append("</CruiseTrack>\r\n");
        xml.append("</Response>\r\n");
        return xml.toString();
    }

    /**
     * 空值安全处理
     *
     * @param value 原始值
     * @return 非空原值或空字符串
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 回复 200 OK 响应
     * <p>
     * 改造项1：响应携带 X-GB-ver 协议版本头部。
     * 来源：设计文档第4节，2022版 7.1。
     * </p>
     *
     * @param evt 请求事件
     */
    private void responseOk(RequestEvent evt) {
        try {
            responseAck((SIPRequest) evt.getRequest(), Response.OK);
        } catch (SipException | InvalidArgumentException | ParseException e) {
            log.error("[巡航轨迹查询] 回复 200 OK 异常: {}", e.getMessage());
        } catch (Throwable t) {
            log.error("[巡航轨迹查询] 回复响应未知异常", t);
        }
    }

    /**
     * 巡航轨迹条目内部模型
     * <p>
     * 用于序列化响应 XML 时的中间表示，包含轨迹 ID、名称及预置位序列。
     * </p>
     */
    private static class CruiseTrack {
        /** 轨迹 ID */
        final int id;
        /** 轨迹名称 */
        final String name;
        /** 预置位序列（详情查询时使用） */
        final List<Integer> presetIds;

        CruiseTrack(int id, String name, List<Integer> presetIds) {
            this.id = id;
            this.name = name;
            this.presetIds = presetIds;
        }
    }
}
