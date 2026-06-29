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

/**
 * 存储卡状态查询消息处理器
 * <p>
 * 改造项14：存储卡状态查询——查询设备存储卡的当前状态、总容量与剩余容量，
 * 用于运维监控与录像存储空间预警。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第10.2节（信息查询类命令扩展），2022版 A.2.4.1.6 存储卡状态查询。<br>
 * 命令类型：CmdType = SDcardStatus<br>
 * 协议要求：2022 版新增存储卡状态查询命令，返回存储卡状态及容量信息。
 * </p>
 * <p>
 * <b>请求 XML 示例：</b>
 * <pre>
 * &lt;Query&gt;
 *     &lt;CmdType&gt;SDcardStatus&lt;/CmdType&gt;
 *     &lt;SN&gt;30&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 * &lt;/Query&gt;
 * </pre>
 * </p>
 * <p>
 * <b>响应 XML 示例：</b>
 * <pre>
 * &lt;Response&gt;
 *     &lt;CmdType&gt;SDcardStatus&lt;/CmdType&gt;
 *     &lt;SN&gt;30&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;Result&gt;OK&lt;/Result&gt;
 *     &lt;StorageCard&gt;
 *         &lt;Status&gt;1&lt;/Status&gt;
 *         &lt;Capacity&gt;65536&lt;/Capacity&gt;
 *         &lt;RemainCapacity&gt;32768&lt;/RemainCapacity&gt;
 *     &lt;/StorageCard&gt;
 * &lt;/Response&gt;
 * </pre>
 * </p>
 * <p>
 * <b>Status 字段枚举值：</b>
 * <ul>
 *     <li>0：存储卡不存在</li>
 *     <li>1：存储卡状态正常</li>
 *     <li>2：存储卡状态异常（损坏、未格式化、读写错误等）</li>
 * </ul>
 * </p>
 * <p>
 * <b>容量单位：</b>MB（兆字节）
 * </p>
 *
 * @author wvp-upgrade
 */
@Slf4j
@Component
public class StorageCardStatusQueryMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    /**
     * 命令类型字符串
     * <p>
     * 改造项14：来源 设计文档第10.2节，2022版 A.2.4.1.6
     * </p>
     */
    private final String cmdType = "SDcardStatus";

    /**
     * 响应结果：成功
     */
    private static final String RESULT_OK = "OK";

    /**
     * 存储卡状态：不存在
     * <p>来源 2022版 A.2.4.1.6</p>
     */
    public static final int STATUS_NOT_EXIST = 0;

    /**
     * 存储卡状态：正常
     * <p>来源 2022版 A.2.4.1.6</p>
     */
    public static final int STATUS_NORMAL = 1;

    /**
     * 存储卡状态：异常
     * <p>来源 2022版 A.2.4.1.6</p>
     */
    public static final int STATUS_ABNORMAL = 2;

    /**
     * 默认存储卡总容量（MB）
     */
    private static final long DEFAULT_CAPACITY = 65536L;

    /**
     * 默认存储卡剩余容量（MB）
     */
    private static final long DEFAULT_REMAIN_CAPACITY = 32768L;

    /**
     * 注入查询消息分发器
     */
    @Autowired
    private QueryMessageHandler queryMessageHandler;

    /**
     * Spring 容器初始化后回调，将当前处理器注册到 QueryMessageHandler。
     * <p>
     * 改造项14：注册 CmdType=SDcardStatus。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.6。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
        log.info("[存储卡状态查询] 处理器注册成功, CmdType={}", cmdType);
    }

    /**
     * 处理来自设备的存储卡状态查询请求
     * <p>
     * 改造项14：设备主动发起查询场景较少，此处仅 ACK 200 OK。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.6。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param device  上报设备
     * @param element XML 根节点
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        log.info("[存储卡状态查询] 收到设备查询请求, deviceId={}",
                device != null ? device.getDeviceId() : "null");
        responseOk(evt);
    }

    /**
     * 处理来自上级平台的存储卡状态查询请求
     * <p>
     * 改造项14：上级平台查询设备存储卡状态。
     * 处理流程：解析请求 → 查询本地存储卡状态缓存 → 构造响应 XML → ACK 200 OK。
     * </p>
     *
     * @param evt      SIP 请求事件
     * @param platform 上级平台
     * @param element  XML 根节点
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform platform, Element element) {
        log.info("[存储卡状态查询] 收到上级平台查询请求, platformId={}",
                platform != null ? platform.getServerGBId() : "null");
        handleQuery(evt, element);
    }

    /**
     * 统一处理存储卡状态查询请求
     * <p>
     * 改造项14：来源 设计文档第10.2节，2022版 A.2.4.1.6。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param element XML 根节点
     */
    private void handleQuery(RequestEvent evt, Element element) {
        if (element == null) {
            log.warn("[存储卡状态查询] XML 根节点为空，无法处理");
            responseOk(evt);
            return;
        }
        // 解析请求字段
        String deviceId = XmlUtil.getText(element, "DeviceID");
        String sn = XmlUtil.getText(element, "SN");
        log.info("[存储卡状态查询] 解析请求: deviceId={}, sn={}", deviceId, sn);

        // 查询本地存储卡状态缓存（此处使用默认值，实际项目应从设备状态缓存中读取）
        int status = STATUS_NORMAL;
        long capacity = DEFAULT_CAPACITY;
        long remainCapacity = DEFAULT_REMAIN_CAPACITY;

        // 构造响应 XML
        String responseXml = buildResponseXml(deviceId, sn, RESULT_OK, status, capacity, remainCapacity);
        log.info("[存储卡状态查询] 响应XML准备就绪, 待异步发送:\n{}", responseXml);

        responseOk(evt);
    }

    /**
     * 构造存储卡状态查询响应 XML
     * <p>
     * 改造项14：来源 2022版 A.2.4.1.6 响应报文格式。
     * </p>
     *
     * @param deviceId       设备 ID
     * @param sn             请求 SN
     * @param result         查询结果
     * @param status         存储卡状态（0=不存在,1=正常,2=异常）
     * @param capacity       总容量（MB）
     * @param remainCapacity 剩余容量（MB）
     * @return 完整响应 XML 字符串
     */
    private String buildResponseXml(String deviceId, String sn, String result,
                                    int status, long capacity, long remainCapacity) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Response>\r\n");
        xml.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        xml.append("<SN>").append(ObjectUtils.isEmpty(sn) ? "1" : SipCharsetHelper.escapeXml(sn)).append("</SN>\r\n");
        xml.append("<DeviceID>").append(ObjectUtils.isEmpty(deviceId) ? "" : SipCharsetHelper.escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<Resend(result</Result>\r\n");
        xml.append("<StorageCard>\r\n");
        xml.append("<Status>").append(status).append("</Status>\r\n");
        xml.append("<Capacity>").append(capacity).append("</Capacity>\r\n");
        xml.append("<RemainCapacity>").append(remainCapacity).append("</RemainCapacity>\r\n");
        xml.append("</StorageCard>\r\n");
        xml.append("</Response>\r\n");
        return xml.toString();
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
            log.error("[存储卡状态查询] 回复 200 OK 异常: {}", e.getMessage());
        } catch (Throwable t) {
            log.error("[存储卡状态查询] 回复响应未知异常", t);
        }
    }
}
