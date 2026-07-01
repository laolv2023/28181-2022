package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.CertAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.cert.X509Certificate;

/**
 * CertAuthHelper 默认实现（桩实现）
 * 生产环境需替换为基于 Java KeyStore 和 X.509 证书链验证的完整实现。
 */
@Component
public class CertAuthHelperImpl implements CertAuthHelper {
    private static final Logger log = LoggerFactory.getLogger(CertAuthHelperImpl.class);

    // 审计修复 56_B-05: 启动时告警, 提醒运维替换桩实现
    @PostConstruct
    void warnStubImplementation() {
        log.error("========================================");
        log.error("[证书认证] 当前使用桩实现! 所有证书验证请求将失败!");
        log.error("[证书认证] 生产环境必须替换为完整的 X.509 证书链验证实现!");
        log.error("========================================");
    }

    @Override
    public boolean verifyDeviceCert(X509Certificate deviceCert) throws CertAuthException {
        if (deviceCert == null) throw new CertAuthException("设备证书不能为null");
        log.warn("[证书认证] verifyDeviceCert 桩实现, 返回 false");
        return false;
    }

    @Override
    public X509Certificate loadCaCert(String caCertPath) throws CertAuthException {
        if (caCertPath == null || caCertPath.isEmpty()) throw new CertAuthException("CA证书路径不能为空");
        log.warn("[证书认证] loadCaCert 桩实现, 返回 null");
        return null;
    }

    @Override
    public boolean verifySubjectMatch(X509Certificate deviceCert, String deviceId) throws CertAuthException {
        if (deviceCert == null || deviceId == null) throw new CertAuthException("参数不能为null");
        log.warn("[证书认证] verifySubjectMatch 桩实现, 返回 false");
        return false;
    }
}
