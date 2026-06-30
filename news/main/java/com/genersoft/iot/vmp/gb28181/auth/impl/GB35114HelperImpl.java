package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.GB35114Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GB35114Helper 默认实现（桩实现）
 * <p>生产环境需替换为基于国密 SM2 签名算法的完整实现。</p>
 */
@Component
public class GB35114HelperImpl implements GB35114Helper {
    private static final Logger logger = LoggerFactory.getLogger(GB35114HelperImpl.class);

    @Override
    public String signSipMessage(String message, String signerId) {
        logger.warn("[GB35114] signSipMessage 桩实现, 返回空字符串。");
        return "";
    }

    @Override
    public boolean verifySipMessage(String message, String signature, String signerId) {
        logger.warn("[GB35114] verifySipMessage 桩实现, 返回 false。");
        return false;
    }
}
