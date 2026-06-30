package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.cmd;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.Platform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.NotifyMessageHandler;
import com.genersoft.iot.vmp.gb28181.utils.GBProtocolVersionHelper;
import com.genersoft.iot.vmp.gb28181.utils.XmlUtil;
import com.genersoft.iot.vmp.service.IDeviceService;
import com.genersoft.iot.vmp.storager.IVideoManagerStorage;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.Header;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;

/**
 * 设备软件升级结果通知消息处理器
 * <p>
 * 改造项10：设备软件升级——服务端接收设备侧上报的升级结果通知。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：spec_2022.txt 第2551行，对应 2022版 A.2.3.1.12 设备软件升级相关通知。<br>
 * CmdType：DeviceupgradeResult<br>
 * </p>
 * <p>
 * <b>XML 结构示例：</b>
 * <pre>
 * &lt;?xml version="1.0" encoding="GB18030"?&gt;
 * &lt;Notify&gt;
 *     &lt;CmdType&gt;DeviceupgradeResult&lt;/CmdType&gt;
 *     &lt;SN&gt;1&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;UpgradeResult&gt;OK&lt;/UpgradeResult&gt;
 *     &lt;Firmware&gt;V2.0.1&lt;/Firmware&gt;
 *     &lt;UpgradeFailedReason&gt;&lt;/UpgradeFailedReason&gt;
 *     &lt;sessionID&gt;12345678&lt;/sessionID&gt;
 * &lt;/Notify&gt;
 * </pre>
 * </p>
 * <p>
 * 字段说明：
 * <ul>
 *     <li>UpgradeResult：升级结果，"OK" 表示成功，"FAILED" 表示失败</li>
 *     <li>Firmware：升级后的固件版本号</li>
 *     <li>UpgradeFailedReason：升级失败原因（仅失败时填写）</li>
 *     <li>sessionID：升级会话标识，与下发升级命令时一致</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
@Component
public class DeviceUpgradeResultNotifyMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(DeviceUpgradeResultNotifyMessageHandler.class);

    /**
     * 命令类型字符串
     * <p>来源：spec_2022.txt 第2551行</p>
     */
    private static final String CMD_TYPE = "DeviceupgradeResult";

    /**
     * 升级成功标识
     */
    private static final String UPGRADE_RESULT_OK = "OK";

    /**
     * 升级失败标识
     */
    private static final String UPGRADE_RESULT_FAILED = "FAILED";

    @Autowired
    private NotifyMessageHandler notifyMessageHandler;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private IDeviceService deviceService;

    /**
     * Spring 容器初始化后回调，将当前处理器注册到 NotifyMessageHandler 中。
     * <p>
     * 来源：改造项10 设备软件升级。注册 CmdType=DeviceupgradeResult，使 NotifyMessageHandler
     * 收到该类型 Notify 时路由到本处理器。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 注册当前处理器到 NotifyMessageHandler，Key 为 CmdType
        notifyMessageHandler.addHandler(CMD_TYPE, this);
        logger.info("[设备升级结果通知] 处理器注册成功，CmdType={}", CMD_TYPE);
    }

    /**
     * 获取当前处理器对应的命令类型
     *
     * @return 命令类型字符串
     */
    public String getCmdType() {
        return CMD_TYPE;
    }

    /**
     * 处理上级平台转发的设备软件升级结果通知
     * <p>级联场景下也接收该 Notify，沿用同一套解析逻辑。</p>
     *
     * @param evt           SIP 请求事件
     * @param parentPlatform 上级平台（仅级联场景使用，单设备场景为 null）
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform parentPlatform, Element element) {
        handleMessage(evt, parentPlatform, element);
    }

    /**
     * 处理设备直接上报的升级结果通知
     *
     * @param evt    SIP 请求事件
     * @param device 上报设备
     * @param element XML 根元素
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        handleMessage(evt, null, element);
    }

    /**
     * 统一消息处理入口
     * <p>
     * 处理流程：
     * <ol>
     *     <li>解析 Notify 根节点，提取设备 ID</li>
     *     <li>从根节点下读取 UpgradeResult、Firmware、UpgradeFailedReason、sessionID 等字段</li>
     *     <li>根据 UpgradeResult 判定升级是否成功，记录日志并通知上层业务</li>
     *     <li>更新设备表中固件版本号（升级成功时）</li>
     *     <li>回复 200 OK，结束本次事务</li>
     * </ol>
     * </p>
     *
     * @param evt           SIP 请求事件
     * @param parentPlatform 上级平台，单设备场景为 null
     * @param rootElement    XML 根元素（由调用方传入，避免重复解析）
     */
    private void handleMessage(RequestEvent evt, Platform parentPlatform, Element rootElement) {
        try {
            if (rootElement == null) {
                logger.warn("[设备升级结果通知] XML 根节点为空，无法处理");
                respondAck(evt, Response.BAD_REQUEST);
                return;
            }

            // 提取设备 ID
            String deviceId = XmlUtil.getText(rootElement, "DeviceID");
            // 提取升级结果
            String upgradeResult = XmlUtil.getText(rootElement, "UpgradeResult");
            // 提取固件版本
            String firmware = XmlUtil.getText(rootElement, "Firmware");
            // 提取失败原因
            String failedReason = XmlUtil.getText(rootElement, "UpgradeFailedReason");
            // 提取会话 ID
            String sessionId = XmlUtil.getText(rootElement, "sessionID");
            // 提取 SN 序号
            String sn = XmlUtil.getText(rootElement, "SN");

            logger.info("[设备升级结果通知] 收到通知: deviceId={}, upgradeResult={}, firmware={}, failedReason={}, sessionId={}, sn={}",
                    deviceId, upgradeResult, firmware, failedReason, sessionId, sn);

            // 根据升级结果进行差异化业务处理
            if (UPGRADE_RESULT_OK.equalsIgnoreCase(upgradeResult)) {
                // 升级成功：更新设备固件版本
                handleUpgradeSuccess(deviceId, firmware, sessionId);
            } else if (UPGRADE_RESULT_FAILED.equalsIgnoreCase(upgradeResult)) {
                // 升级失败：记录失败原因
                handleUpgradeFailure(deviceId, firmware, failedReason, sessionId);
            } else {
                // 未知结果：仅记录日志
                logger.warn("[设备升级结果通知] 未知的升级结果标识: {}, deviceId={}", upgradeResult, deviceId);
            }

            // 回复 200 OK
            respondAck(evt, Response.OK);

        } catch (Exception e) {
            logger.error("[设备升级结果通知] 处理异常", e);
            // 审计修复P1-15: 系统异常回复 500, 避免掩盖服务端故障
            respondAck(evt, Response.SERVER_INTERNAL_ERROR);
        }
    }

    /**
     * 处理升级成功业务
     * <p>
     * 升级成功后，更新设备表中固件版本号，并清理升级会话状态。
     * </p>
     *
     * @param deviceId  设备 ID
     * @param firmware  新固件版本
     * @param sessionId 升级会话 ID
     */
    private void handleUpgradeSuccess(String deviceId, String firmware, String sessionId) {
        if (ObjectUtils.isEmpty(deviceId)) {
            logger.warn("[设备升级结果通知] 设备ID为空，无法更新固件版本");
            return;
        }
        try {
            Device device = storager.queryVideoDevice(deviceId);
            if (device != null && !ObjectUtils.isEmpty(firmware)) {
                // 更新设备固件版本
                // 验证设备身份一致性
        if (!deviceId.equals(device.getDeviceId())) {
            logger.warn("[设备升级结果] 设备ID不一致, 忽略");
            return;
        }
        // 仅更新固件版本字段, 不覆盖整个设备对象
        device.setFirmware(firmware);
                deviceService.updateDevice(device);
                logger.info("[设备升级结果通知] 设备 {} 固件版本更新为 {}", deviceId, firmware);
            }
        } catch (Exception e) {
            logger.error("[设备升级结果通知] 更新设备固件版本异常, deviceId={}", deviceId, e);
        }
    }

    /**
     * 处理升级失败业务
     *
     * @param deviceId      设备 ID
     * @param firmware      期望固件版本
     * @param failedReason  失败原因
     * @param sessionId     升级会话 ID
     */
    private void handleUpgradeFailure(String deviceId, String firmware, String failedReason, String sessionId) {
        logger.warn("[设备升级结果通知] 设备 {} 升级失败, 期望固件={}, 失败原因={}, sessionId={}",
                deviceId, firmware, failedReason, sessionId);
        // 业务可在此扩展：告警、重试、状态机推进等
    }

    /**
     * 回复 SIP 响应
     * <p>
     * 创建/复用 ServerTransaction，封装并返回响应消息。
     * 响应头携带 X-GB-ver 头部，符合改造项1协议版本协商要求。
     * </p>
     *
     * @param evt  请求事件
     * @param code 响应码
     */
    private void respondAck(RequestEvent evt, int code) {
        try {
            Request request = evt.getRequest();
            ServerTransaction serverTransaction = getServerTransaction(evt);
            Response response = getMessageFactory().createResponse(code, request);

            // 改造项1：响应消息携带 X-GB-ver 头部，标识当前协议版本
            // 来源：设计文档第4节，2022版 7.1 协议版本协商
            Header gbVerHeader = getHeaderFactory().createHeader(
                    "X-GB-ver", GBProtocolVersionHelper.GB_PROTOCOL_VERSION);
            response.addHeader(gbVerHeader);

            serverTransaction.sendResponse(response);
        } catch (ParseException | SipException | InvalidArgumentException e) {
            logger.error("[设备升级结果通知] 回复 {} 响应异常", code, e);
        } catch (Exception t) {
            // 兜底捕获，避免线程因响应失败而终止
            logger.error("[设备升级结果通知] 回复响应未知异常", t);
        }
    }
}
