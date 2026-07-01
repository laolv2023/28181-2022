package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.GB35114Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * GB35114Helper 默认实现（桩实现）
 * 生产环境需替换为基于国密 SM2 签名算法的完整实现。
 */
@Component
public class GB35114HelperImpl implements GB35114Helper {
    private static final Logger log = LoggerFactory.getLogger(GB35114HelperImpl.class);

    // 审计修复 56_B-06: 启动时告警, 提醒运维替换桩实现
    @PostConstruct
    void warnStubImplementation() {
        log.error("========================================");
        log.error("[GB35114] 当前使用桩实现! 所有签名验证请求将失败!");
        log.error("[GB35114] 生产环境必须替换为基于国密 SM2 的完整实现!");
        log.error("========================================");
    }

    @Override
    public byte[] processSVACEncryptedStream(byte[] encryptedStream) throws GB35114Exception {
        if (encryptedStream == null || encryptedStream.length == 0) {
            throw new GB35114Exception("加密流数据不能为空");
        }
        log.warn("[GB35114] processSVACEncryptedStream 桩实现, 无法解密SVAC流; 生产环境必须替换为国密SM4完整实现");
        throw new GB35114Exception("SVAC加密流解密未实现, 请替换为生产级GB35114HelperImpl");
    }

    @Override
    public boolean verifySignature(byte[] data, String signature) throws GB35114Exception {
        if (data == null || data.length == 0) {
            throw new GB35114Exception("签名原始数据不能为空");
        }
        if (signature == null || signature.isEmpty()) {
            throw new GB35114Exception("签名值不能为空");
        }
        log.warn("[GB35114] verifySignature 桩实现, 返回 false");
        return false;
    }

    @Override
    public boolean checkSecurityLevel(String declaredLevel, String requiredLevel) throws GB35114Exception {
        if (declaredLevel == null || requiredLevel == null) {
            throw new GB35114Exception("安全级别参数不能为null");
        }
        // 安全级别比较: GB 35114 仅定义 A/B/C 三个安全等级
        // 空字符串/空白字符绕过防护: indexOf("")=0 会导致空声明被识别为 A 级
        if (declaredLevel.trim().isEmpty() || requiredLevel.trim().isEmpty()) {
            throw new GB35114Exception("安全级别不能为空字符串");
        }
        int declaredRank = "ABC".indexOf(declaredLevel.trim());
        int requiredRank = "ABC".indexOf(requiredLevel.trim());
        if (declaredRank < 0) {
            log.warn("[GB35114] 设备声明了非标准安全级别: {}, 仅支持 A/B/C", declaredLevel);
        }
        if (requiredRank < 0) {
            log.warn("[GB35114] 要求的非标准安全级别: {}, 仅支持 A/B/C", requiredLevel);
        }
        return declaredRank >= 0 && declaredRank >= requiredRank;
    }
}
