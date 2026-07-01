package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.GB35114Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GB35114Helper 默认实现（桩实现）
 * 生产环境需替换为基于国密 SM2 签名算法的完整实现。
 */
@Component
public class GB35114HelperImpl implements GB35114Helper {
    private static final Logger log = LoggerFactory.getLogger(GB35114HelperImpl.class);

    @Override
    public boolean verifySignature(byte[] data, String signature) throws GB35114Exception {
        log.warn("[GB35114] verifySignature 桩实现, 返回 false");
        return false;
    }

    @Override
    public boolean checkSecurityLevel(String declaredLevel, String requiredLevel) throws GB35114Exception {
        if (declaredLevel == null || requiredLevel == null) {
            throw new GB35114Exception("安全级别参数不能为null");
        }
        // 简单比较: 声明级别 >= 要求级别
        // 安全级别比较: A < B < C < D
        int declaredRank = "ABCD".indexOf(declaredLevel);
        int requiredRank = "ABCD".indexOf(requiredLevel);
        return declaredRank >= 0 && declaredRank >= requiredRank;
    }
}
