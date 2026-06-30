package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MANSRTSP 协议辅助工具类
 * <p>
 * 改造项24-25：MANSRTSP 拉流倍速与反向播放
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第12节，2022版 A.2.4 MANSRTSP 协议扩展。<br>
 * MANSRTSP 用于 GB/T 28181 历史媒体回放控制，2022 版新增倍速范围约束与反向播放支持。
 * </p>
 *
 * @author wvp-upgrade
 */
public final class MansrtspHelper {

    private static final Logger logger = LoggerFactory.getLogger(MansrtspHelper.class);

    /**
     * MANSRTSP Scale 头部名称
     */
    public static final String HEADER_SCALE = "Scale";

    /**
     * 允许的播放倍速集合
     * <p>
     * 改造项24：来源 设计文档第12节，2022版 A.2.4
     * 仅允许以下倍速：0.25、0.5、1、2、4
     * </p>
     */
    public static final List<Double> ALLOWED_SCALES = Collections.unmodifiableList(
            Arrays.asList(0.25, 0.5, 1.0, 2.0, 4.0)
    );

    /**
     * 默认倍速
     */
    public static final double DEFAULT_SCALE = 1.0;

    /**
     * 默认构造方法私有化，禁止实例化
     */
    private MansrtspHelper() {
        throw new IllegalStateException("工具类禁止实例化");
    }

    /**
     * 在 MANSRTSP 请求中追加 Scale 头部
     * <p>
     * 改造项24：倍速取值仅允许 {0.25, 0.5, 1, 2, 4}，若调用方传入其他值，
     * 自动就近匹配最接近的合法值。
     * </p>
     * <p>
     * 来源：设计文档第12节，2022版 A.2.4 MANSRTSP Scale 头部规范。
     * </p>
     *
     * @param builder StringBuilder，承载完整 MANSRTSP 报文
     * @param scale   期望倍速
     * @return 实际使用的倍速（已 snap 到合法值）
     */
    /** 追加Scale头部, 返回实际使用的倍速 */
    public static double appendScale(StringBuilder builder, double scale) {
        if (builder == null) {
            return DEFAULT_SCALE;
        }
        // 校验并 snap 到最接近的合法倍速
        double snapped = snapToAllowedScale(scale);
        builder.append(HEADER_SCALE).append(": ").append(formatScale(snapped)).append("\r\n");
        logger.debug("[MANSRTSP] 追加 Scale 头部, 期望={}, 实际={}", scale, snapped);
        return snapped;
    }

    /**
     * 在 MANSRTSP 请求中追加反向播放 Scale 头部
     * <p>
     * 改造项25：反向播放通过负数倍速实现，2022 版新增支持。
     * </p>
     * <p>
     * 来源：设计文档第12节，2022版 A.2.4 MANSRTSP Scale 头部规范。<br>
     * 反向播放：Scale 取负值，例如 -1.0 表示 1 倍速反向播放，-2.0 表示 2 倍速反向播放。
     * </p>
     *
     * @param builder StringBuilder，承载完整 MANSRTSP 报文
     * @param scale   期望倍速绝对值，方法内部取负
     * @return 实际使用的反向倍速（已 snap 到合法值的负数）
     */
    public static double appendReverseScale(StringBuilder builder, double scale) {
        if (builder == null) {
            return -DEFAULT_SCALE;
        }
        // 反向播放：snap 后取负
        double snapped = snapToAllowedScale(Math.abs(scale));
        double reverseScale = -snapped;
        builder.append(HEADER_SCALE).append(": ").append(formatScale(reverseScale)).append("\r\n");
        logger.debug("[MANSRTSP] 追加反向 Scale 头部, 期望={}, 实际={}", scale, reverseScale);
        return reverseScale;
    }

    /**
     * 将任意倍速 snap 到最接近的合法值
     * <p>
     * 算法：遍历 ALLOWED_SCALES，找出与输入值差的绝对值最小的合法值。
     * </p>
     *
     * @param scale 输入倍速
     * @return 最接近的合法倍速
     */
    public static double snapToAllowedScale(double scale) {
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            return DEFAULT_SCALE;
        }
        double best = ALLOWED_SCALES.get(0);
        double minDiff = Math.abs(scale - best);
        for (double allowed : ALLOWED_SCALES) {
            double diff = Math.abs(scale - allowed);
            if (diff < minDiff) {
                minDiff = diff;
                best = allowed;
            }
        }
        return best;
    }

    /**
     * 判断倍速是否合法
     *
     * @param scale 倍速
     * @return true 表示合法
     */
    public static boolean isValidScale(double scale) {
        for (double allowed : ALLOWED_SCALES) {
            if (Double.compare(allowed, scale) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为反向播放倍速
     *
     * @param scale 倍速
     * @return true 表示反向（scale 为负）
     */
    public static boolean isReverseScale(double scale) {
        return scale < 0;
    }

    /**
     * 格式化倍速字符串
     * <p>
     * 整数倍速显示为整数（如 4 而非 4.0），小数倍速保留小数（如 0.25）。
     * </p>
     *
     * @param scale 倍速
     * @return 格式化字符串
     */
    private static String formatScale(double scale) {
        if (scale == (long) scale) {
            return String.valueOf((long) scale);
        }
        return String.valueOf(scale);
    }
}
