package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * GB/T 28181-2022 编码规则校验工具类
 * <p>
 * 改造项36-38：编码规则改造，包括行政区域码、新型码、网络码、采集位置码的校验。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第13.5节（编码规则改造），2022版 附录 E（标识编码规则）。<br>
 * 2022 版相较于 2016 版对编码规则做了如下扩展：
 * <ul>
 *     <li>行政区域码：6 位数字，省级码段范围 11~65</li>
 *     <li>新型设备类型码：120~125、140~143、502、503、505</li>
 *     <li>网络码：0~9 共 10 种取值</li>
 *     <li>采集位置码（改造项34，附录 O）：1~10 共 10 种取值</li>
 * </ul>
 * </p>
 * <p>
 * <b>20位国标编码结构（2022版 附录 E）：</b>
 * <pre>
 *  位置: 1-6       7-8       9-10       11-13       14        15-17       18-20
 *  字段: 行政区码  行业类型  设备类型   网络标识    采集位置   设备序号   接入序号
 *  长度: 6         2         2          3           1         3          3
 * </pre>
 * </p>
 *
 * @author wvp-upgrade
 */
public final class GbCode2022 {

    private static final Logger logger = LoggerFactory.getLogger(GbCode2022.class);

    /**
     * 行政区码长度（位）
     */
    public static final int ADMIN_CODE_LENGTH = 6;

    /**
     * 省级码段最小值
     * <p>来源 2022版 附录 E，省级码段范围 11~65</p>
     */
    public static final int PROVINCE_CODE_MIN = 11;

    /**
     * 省级码段最大值
     * <p>来源 2022版 附录 E，省级码段范围 11~65</p>
     */
    public static final int PROVINCE_CODE_MAX = 65;

    /**
     * 新型设备类型码集合
     * <p>
     * 改造项37：来源 设计文档第13.5节，2022版 附录 E。<br>
     * 新增设备类型码：120~125（智能设备）、140~143（编码设备扩展）、502/503/505（其他扩展）。
     * </p>
     */
    public static final Set<Integer> NEW_TYPE_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            120, 121, 122, 123, 124, 125,
            140, 141, 142, 143,
            502, 503, 505
    )));

    /**
     * 网络码最小值
     * <p>来源 2022版 附录 E，网络码取值范围 0~9</p>
     */
    public static final int NET_CODE_MIN = 0;

    /**
     * 网络码最大值
     * <p>来源 2022版 附录 E，网络码取值范围 0~9</p>
     */
    public static final int NET_CODE_MAX = 9;

    /**
     * 采集位置码最小值
     * <p>改造项34：来源 设计文档第13.5节，2022版 附录 O，采集位置码取值范围 1~10</p>
     */
    public static final int CAPTURE_POSITION_MIN = 1;

    /**
     * 采集位置码最大值
     * <p>改造项34：来源 设计文档第13.5节，2022版 附录 O，采集位置码取值范围 1~10</p>
     */
    public static final int CAPTURE_POSITION_MAX = 10;

    /**
     * 私有构造方法，禁止实例化工具类
     */
    private GbCode2022() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 校验行政区域码合法性
     * <p>
     * 改造项36：来源 设计文档第13.5节，2022版 附录 E。<br>
     * 规则：6 位数字，前 2 位为省级码段，取值范围 11~65。
     * </p>
     *
     * @param adminCode 待校验的行政区码
     * @return true 表示合法
     */
    public static boolean isValidAdminCode(String adminCode) {
        if (ObjectUtils.isEmpty(adminCode)) {
            return false;
        }
        // 必须为 6 位数字
        if (adminCode.length() != ADMIN_CODE_LENGTH) {
            logger.debug("[编码校验] 行政区码长度非法: {}, 期望 {}", adminCode, ADMIN_CODE_LENGTH);
            return false;
        }
        // 校验省级码段（parseInt失败时捕获异常，替代regex预检）
        try {
            int provinceCode = Integer.parseInt(adminCode.substring(0, 2));
            if (provinceCode < PROVINCE_CODE_MIN || provinceCode > PROVINCE_CODE_MAX) {
                logger.debug("[编码校验] 省级码段非法: {}, 范围 {}~{}", provinceCode, PROVINCE_CODE_MIN, PROVINCE_CODE_MAX);
                return false;
            }
        } catch (NumberFormatException e) {
            logger.warn("[编码校验] 行政区码解析异常: {}", adminCode, e);
            return false;
        }
        return true;
    }

    /**
     * 校验是否为 2022 新增设备类型码
     * <p>
     * 改造项37：来源 设计文档第13.5节，2022版 附录 E。<br>
     * 新型设备类型码集合：120~125、140~143、502、503、505。
     * </p>
     *
     * @param typeCode 待校验的设备类型码字符串
     * @return true 表示为新型设备类型码
     */
    public static boolean isNewTypeCode(String typeCode) {
        if (ObjectUtils.isEmpty(typeCode)) {
            return false;
        }
        try {
            int code = Integer.parseInt(typeCode);
            return NEW_TYPE_CODES.contains(code);
        } catch (NumberFormatException e) {
            logger.debug("[编码校验] 设备类型码解析失败: {}", typeCode);
            return false;
        }
    }

    /**
     * 校验是否为 2022 新增设备类型码（整型重载）
     *
     * @param typeCode 待校验的设备类型码
     * @return true 表示为新型设备类型码
     * @see #isNewTypeCode(String)
     */
    public static boolean isNewTypeCode(int typeCode) {
        return NEW_TYPE_CODES.contains(typeCode);
    }

    /**
     * 校验网络码合法性
     * <p>
     * 改造项38：来源 设计文档第13.5节，2022版 附录 E。<br>
     * 规则：网络码为单字符数字，取值范围 0~9，共 10 种取值。
     * </p>
     *
     * @param netCode 待校验的网络码
     * @return true 表示合法
     */
    public static boolean isValidNetCode(String netCode) {
        if (ObjectUtils.isEmpty(netCode)) {
            return false;
        }
        // 网络码为单字符数字
        if (netCode.length() != 1) {
            logger.debug("[编码校验] 网络码长度非法: {}, 期望 1", netCode);
            return false;
        }
        if (!Character.isDigit(netCode.charAt(0))) {
            logger.debug("[编码校验] 网络码非数字字符: {}", netCode);
            return false;
        }
        int code = netCode.charAt(0) - '0';
        return code >= NET_CODE_MIN && code <= NET_CODE_MAX;
    }

    /**
     * 校验采集位置码合法性
     * <p>
     * 改造项34：来源 设计文档第13.5节，2022版 附录 O。<br>
     * 规则：采集位置码取值范围 1~10，共 10 种取值。
     * </p>
     *
     * @param capturePositionCode 待校验的采集位置码
     * @return true 表示合法
     */
    public static boolean isValidCapturePositionCode(String capturePositionCode) {
        if (ObjectUtils.isEmpty(capturePositionCode)) {
            return false;
        }
        try {
            // 采集位置码可以是 1~2 位数字
            int code = Integer.parseInt(capturePositionCode);
            return code >= CAPTURE_POSITION_MIN && code <= CAPTURE_POSITION_MAX;
        } catch (NumberFormatException e) {
            logger.debug("[编码校验] 采集位置码解析失败: {}", capturePositionCode);
            return false;
        }
    }

    /**
     * 校验采集位置码合法性（整型重载）
     *
     * @param capturePositionCode 待校验的采集位置码
     * @return true 表示合法
     * @see #isValidCapturePositionCode(String)
     */
    public static boolean isValidCapturePositionCode(int capturePositionCode) {
        return capturePositionCode >= CAPTURE_POSITION_MIN && capturePositionCode <= CAPTURE_POSITION_MAX;
    }

    /**
     * 综合校验 20 位国标编码
     * <p>
     * 改造项36-38：综合校验行政区码、设备类型码、网络码、采集位置码。
     * 来源：设计文档第13.5节，2022版 附录 E。
     * </p>
     * <p>
     * 20 位编码结构：
     * <pre>
     * 1-6:    行政区码 (adminCode)
     * 7-8:    行业类型 (industryCode)
     * 9-10:   设备类型 (deviceTypeCode)
     * 11-13:  网络标识 (networkIdentifier)
     * 14:     采集位置 (capturePositionCode)
     * 15-17:  设备序号 (deviceSerial)
     * 18-20:  接入序号 (accessSerial)
     * </pre>
     * </p>
     *
     * @param gbCode 完整 20 位国标编码
     * @return true 表示综合校验通过
     */
    public static boolean isValidGbCode(String gbCode) {
        if (ObjectUtils.isEmpty(gbCode) || gbCode.length() != 20) {
            logger.debug("[编码校验] 国标编码长度非法: {}", gbCode);
            return false;
        }
        // 校验行政区码（前 6 位）— 各子方法内部有 parseInt try-catch
        String adminCode = gbCode.substring(0, 6);
        if (!isValidAdminCode(adminCode)) {
            return false;
        }
        // 校验设备类型码（第 9-10 位）
        String deviceTypeCode = gbCode.substring(8, 10);
        if (!isNewTypeCode(deviceTypeCode)) {
            // 2016 设备类型码也可接受，此处仅做新型码标记性校验，不阻断
            logger.debug("[编码校验] 设备类型码非新型: {}", deviceTypeCode);
        }
        // 校验网络码（第 11 位，网络标识首位）
        String netCode = gbCode.substring(10, 11);
        if (!isValidNetCode(netCode)) {
            return false;
        }
        // 校验采集位置码（第 14 位）
        // 审计修复P2-13 + 56_B-02: 采集位置码范围1~10，
        // 当位置14为'1'且位置15为'0'时表示"10"（2位），否则取1位
        char c14 = gbCode.charAt(13);
        String capturePositionCode;
        if (c14 == '1' && gbCode.charAt(14) == '0') {
            capturePositionCode = "10";
        } else {
            capturePositionCode = String.valueOf(c14);
        }
        if (!isValidCapturePositionCode(capturePositionCode)) {
            return false;
        }
        return true;
    }
}
