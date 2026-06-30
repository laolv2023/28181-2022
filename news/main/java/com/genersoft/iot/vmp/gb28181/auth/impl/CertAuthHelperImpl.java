package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.CertAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CertAuthHelper 默认实现（桩实现）
 * <p>生产环境需替换为基于 Java KeyStore 和 X.509 证书链验证的完整实现。</p>
 */
@Component
public class CertAuthHelperImpl implements CertAuthHelper {
    private static final Logger logger = LoggerFactory.getLogger(CertAuthHelperImpl.class);

    @Override
    public boolean verifyCertificate(String certData) {
        logger.warn("[证书认证] 当前为桩实现, 直接返回 false。生产环境需提供完整实现。");
        return false;
    }

    @Override
    public String getCertSubject(String certData) {
        logger.warn("[证书认证] getCertSubject 桩实现, 返回空字符串。");
        return "";
    }
}
