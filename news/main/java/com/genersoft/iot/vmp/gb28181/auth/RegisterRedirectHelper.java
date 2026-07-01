package com.genersoft.iot.vmp.gb28181.auth;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;

/**
 * 注册重定向工具类
 * <p>
 * 改造项3：注册重定向功能（302 响应处理）
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第6节（注册重定向），2022版 7.3 注册流程。<br>
 * 场景：当设备注册到错误的注册服务器时，注册服务器可返回 302 Moved Temporarily，
 * 在 Contact 头部中携带重定向目标地址，设备应根据 Contact 头部重新发起注册。
 * </p>
 *
 * @author wvp-upgrade
 */
public final class RegisterRedirectHelper {

    private static final Logger logger = LoggerFactory.getLogger(RegisterRedirectHelper.class);

    /**
     * 默认构造方法私有化，禁止实例化
     */
    private RegisterRedirectHelper() {
        throw new IllegalStateException("工具类禁止实例化");
    }

    /**
     * 构造 302 注册重定向响应
     * <p>
     * 改造项3：注册服务器在判断需要重定向时，使用该方法构造 302 响应。
     * 来源：设计文档第6节，2022版 7.3 注册流程。
     * </p>
     * <p>
     * 响应包含：
     * <ul>
     *     <li>Contact 头部：重定向目标的 Contact URI</li>
     *     <li>From/To/Call-ID/CSeq/Via 等：与原注册请求保持一致</li>
     * </ul>
     * </p>
     *
     * @param request           原始注册请求
     * @param redirectContact   重定向目标的 Contact 字符串，格式如 sip:34020000002000000001@10.0.0.1:5060
     * @param messageFactory    SIP 消息工厂
     * @param headerFactory     SIP 头部工厂
     * @param addressFactory    SIP 地址工厂
     * @return 302 响应对象
     * @throws ParseException 解析异常
     */
    public static Response build302Response(Request request,
                                            String redirectContact,
                                            MessageFactory messageFactory,
                                            HeaderFactory headerFactory,
                                            AddressFactory addressFactory) throws ParseException {
        if (request == null) {
            throw new IllegalArgumentException("注册请求不能为空");
        }
        if (ObjectUtils.isEmpty(redirectContact)) {
            throw new IllegalArgumentException("重定向 Contact 不能为空");
        }
        if (messageFactory == null || headerFactory == null || addressFactory == null) {
            throw new IllegalArgumentException("SIP 工厂不能为空");
        }

        // 创建 302 响应
        Response response = messageFactory.createResponse(Response.MOVED_TEMPORARILY, request);

        // 构造 Contact 头部
        try {
            URI contactUri = addressFactory.createURI(redirectContact);
            Address contactAddress = addressFactory.createAddress(contactUri);
            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
            response.addHeader(contactHeader);
        } catch (Exception e) {
            logger.error("[注册重定向] 解析重定向 Contact URI 失败: {}", redirectContact, e);
            throw new ParseException("重定向 Contact URI 格式错误: " + redirectContact, 0);
        }

        logger.info("[注册重定向] 构造 302 响应成功, redirectContact={}", redirectContact);
        return response;
    }

    /**
     * 处理收到的 302 重定向响应，更新设备注册地址
     * <p>
     * 改造项3：设备/级联平台收到 302 后，需要解析 Contact 头部，更新本地保存的注册地址，
     * 并以新地址重新发起注册。
     * 来源：设计文档第6节，2022版 7.3 注册流程。
     * </p>
     * <p>
     * 处理逻辑：
     * <ol>
     *     <li>解析 302 响应中的 Contact 头部，提取新的注册目标 URI</li>
     *     <li>更新 Device 对象中的注册服务器 IP/端口/域</li>
     *     <li>调用方可基于返回的新 Contact URI 重新发起注册</li>
     * </ol>
     * </p>
     *
     * @param response 302 响应对象
     * @param device   设备对象
     * @return 重定向后的 Contact URI 字符串；无 Contact 头部时返回 null
     */
    @SuppressWarnings("unchecked")
    /** 处理302重定向响应, 返回新的注册地址 */
    public static String handle302Response(Response response, Device device) {
        if (response == null) {
            logger.warn("[注册重定向] 响应对象为空，无法处理");
            return null;
        }
        if (device == null) {
            logger.warn("[注册重定向] 设备对象为空，无法更新注册地址");
            return null;
        }

        // 提取 Contact 头部
        ContactHeader contactHeader = (ContactHeader) response.getHeader(ContactHeader.NAME);
        if (contactHeader == null) {
            logger.warn("[注册重定向] 302 响应未携带 Contact 头部，无法重定向");
            return null;
        }

        Address contactAddress = contactHeader.getAddress();
        if (contactAddress == null) {
            logger.warn("[注册重定向] Contact 头部地址为空");
            return null;
        }

        URI contactUri = contactAddress.getURI();
        if (!(contactUri instanceof SipURI)) {
            logger.warn("[注册重定向] Contact URI 非 SipURI 类型，无法解析");
            return null;
        }

        SipURI sipURI = (SipURI) contactUri;
        String host = sipURI.getHost();
        int port = sipURI.getPort();
        String transport = sipURI.getTransportParam();

        logger.info("[注册重定向] 设备 {} 收到 302, 新注册目标 host={}, port={}, transport={}",
                device.getDeviceId(), host, port, transport);

        // 更新设备本地保存的注册地址
        if (!ObjectUtils.isEmpty(host)) {
            try {
                if (!ObjectUtils.isEmpty(transport)) {
                    device.setTransport(transport);
                }
                // 尝试更新 host 和 port（通过反射兼容不同版本的 Device 类）
                try {
                    device.setHost(host);
                } catch (NoSuchMethodError ignored) {
                    // Device 类无 setHost 方法，通过重新注册自动获取
                }
                try {
                    device.setPort(port);
                } catch (NoSuchMethodError | NumberFormatException ignored) {
                    // Device 类无 setPort 方法或 port 非数字
                }
            } catch (Exception e) {
                logger.debug("[注册重定向] Device 地址更新忽略: {}", e.getMessage());
            }
        }

        return contactUri.toString();
    }

    /**
     * 判断响应是否为 302 重定向
     *
     * @param response SIP 响应对象
     * @return true 表示为 302 响应
     */
    public static boolean is302Response(Response response) {
        return response != null && response.getStatusCode() == Response.MOVED_TEMPORARILY;
    }
}
