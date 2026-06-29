<!--
  SecurityConfig.vue — 安全配置页面

  来源: 后端改造项2(SM3, 设计文档第8.3节) + 改造项3(注册重定向) +
        改造项26(TCP重连) + 改造项30(TLS, 设计文档第8.2节) + 改造项35(字符集)
  规范依据: 《WVP前端UI改造方案》第3.8节 F8
  技术栈: Vue 2.6 + Element UI 2.x

  功能说明:
    - SM3 摘要算法开关（改造项2）
    - SIP TLS 加密传输开关（改造项30）
    - SIP 信令字符集选择（改造项35）
    - 注册重定向开关（改造项3）
    - TCP 媒体重连开关（改造项26）
    - 保存配置按钮

  使用方式:
    作为独立页面使用，需在路由中注册：
    {
      path: '/operations/security',
      name: 'SecurityConfig',
      component: () => import('@/views/operations/SecurityConfig.vue')
    }

  @author wvp-upgrade
  @since 2026-06-29
-->
<template>
  <div class="security-config-page">
    <!-- 页面标题 -->
    <el-card shadow="never">
      <div slot="header" class="page-header">
        <span class="page-title">安全配置（GB/T 28181-2022）</span>
        <el-tag type="warning" size="small" effect="plain">
          重启服务后生效
        </el-tag>
      </div>

      <!-- 配置表单 -->
      <el-form
        ref="configForm"
        :model="config"
        label-width="200px"
        size="small"
        :disabled="saving"
      >
        <!-- 改造项2: SM3 摘要算法 -->
        <!-- 来源: 后端改造项2, 设计文档第8.3节 -->
        <el-form-item label="SM3 摘要算法">
          <el-switch v-model="config.sm3DigestEnabled" active-text="启用" inactive-text="禁用" />
          <div class="form-item-tip">
            启用后，SIP 信令摘要认证优先使用 SM3 算法（国密），同时兼容 MD5（2016版设备）
          </div>
        </el-form-item>

        <!-- 改造项30: SIP TLS 加密传输 -->
        <!-- 来源: 后端改造项30, 设计文档第8.2节 -->
        <el-form-item label="SIP TLS 加密传输">
          <el-switch v-model="config.sipTlsEnabled" active-text="启用" inactive-text="禁用" />
          <div class="form-item-tip">
            启用后，SIP 信令通过 TLS 通道加密传输，默认端口 5061（TLS over TCP）
          </div>
        </el-form-item>

        <!-- 改造项35: SIP 信令字符集 -->
        <!-- 来源: 后端改造项35, GB 2312 → GB 18030 -->
        <el-form-item label="SIP 信令字符集">
          <el-select v-model="config.sipCharset" placeholder="请选择字符集" style="width: 200px;">
            <el-option label="GB 18030（2022版默认）" value="gb18030" />
            <el-option label="GB 2312（2016版兼容）" value="gb2312" />
          </el-select>
          <div class="form-item-tip">
            GB 18030 为国家强制性标准，兼容 GB 2312 全部字符，推荐使用
          </div>
        </el-form-item>

        <!-- 改造项3: 注册重定向 -->
        <el-form-item label="注册重定向">
          <el-switch v-model="config.registerRedirectEnabled" active-text="启用" inactive-text="禁用" />
          <div class="form-item-tip">
            启用后，平台支持对 302 注册重定向响应的自动处理
          </div>
        </el-form-item>

        <!-- 改造项26: TCP 媒体重连 -->
        <!-- 来源: 后端改造项26, TCP 媒体传输重连机制 -->
        <el-form-item label="TCP 媒体重连">
          <el-switch v-model="config.tcpReconnectEnabled" active-text="启用" inactive-text="禁用" />
          <div class="form-item-tip">
            启用后，TCP 媒体流在连接断开时自动尝试重连，提升媒体传输可靠性
          </div>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            icon="el-icon-check"
            @click="handleSave"
          >
            保存配置
          </el-button>
          <el-button
            :disabled="saving"
            icon="el-icon-refresh"
            @click="handleReset"
          >
            恢复默认
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 保存结果提示 -->
    <el-alert
      v-if="saveResult"
      :title="saveResult.message"
      :type="saveResult.type"
      :closable="true"
      show-icon
      style="margin-top: 16px;"
      @close="saveResult = null"
    />
  </div>
</template>

<script>
/**
 * 安全配置页面
 *
 * 依赖:
 *   - API: saveSecurityConfig（来自 @/api/frontEnd）
 *   - Element UI: el-card, el-form, el-switch, el-select, el-button, el-alert, el-tag
 *
 * 数据流:
 *   1. 页面加载 → 从后端加载当前配置（如后端未提供接口，使用默认值）
 *   2. 用户修改配置 → 点击保存 → 调用 saveSecurityConfig API
 *   3. 显示保存结果（成功/失败），提示需重启生效
 */
import { saveSecurityConfig } from '@/api/frontEnd'

export default {
  name: 'SecurityConfig',

  // ========== Data ==========
  data() {
    return {
      /**
       * 安全配置对象
       */
      config: {
        /** 是否启用 SM3 摘要算法 */
        sm3DigestEnabled: true,
        /** 是否启用 SIP TLS 加密 */
        sipTlsEnabled: false,
        /** SIP 信令字符集 */
        sipCharset: 'gb18030',
        /** 是否启用注册重定向 */
        registerRedirectEnabled: true,
        /** 是否启用 TCP 媒体重连 */
        tcpReconnectEnabled: false
      },
      /**
       * 保存进行中标记
       */
      saving: false,
      /**
       * 保存结果提示
       * @type {{ message: string, type: string } | null}
       */
      saveResult: null
    }
  },

  // ========== Lifecycle ==========

  /**
   * 路由离开守卫：检测未保存的配置变更
   * 来源: 改造项2/3/26/30/35, 防止用户误操作丢失配置
   */
  beforeRouteLeave(to, from, next) {
    const defaultConfig = {
      sm3DigestEnabled: true,
      sipTlsEnabled: false,
      sipCharset: 'gb18030',
      registerRedirectEnabled: true,
      tcpReconnectEnabled: false
    }
    const isModified = Object.keys(defaultConfig).some(
      key => this.config[key] !== defaultConfig[key]
    )
    if (isModified) {
      this.$confirm('当前配置尚未保存，确定离开吗？', '未保存的更改', {
        confirmButtonText: '确定离开',
        cancelButtonText: '留在此页',
        type: 'warning'
      }).then(() => next()).catch(() => next(false))
    } else {
      next()
    }
  },

  // ========== Methods ==========
  methods: {
    /**
     * 保存配置
     *
     * 来源: 后端改造项2/3/26/30/35 配套
     * API: POST /api/device/config/save_security
     */
    async handleSave() {
      this.saving = true
      this.saveResult = null
      try {
        await saveSecurityConfig({ ...this.config })
        this.saveResult = {
          type: 'success',
          message: '安全配置已保存，部分配置需重启服务后生效'
        }
      } catch (err) {
        this.saveResult = {
          type: 'error',
          message: `保存失败：${err.message || '网络异常'}`
        }
        console.error('[安全配置] 保存失败', err)
      } finally {
        this.saving = false
      }
    },

    /**
     * 恢复默认配置
     */
    handleReset() {
      this.$confirm('确定要恢复到默认配置吗？', '确认操作', {
        type: 'warning'
      }).then(() => {
        this.config = {
          sm3DigestEnabled: true,
          sipTlsEnabled: false,
          sipCharset: 'gb18030',
          registerRedirectEnabled: true,
          tcpReconnectEnabled: false
        }
        this.saveResult = null
        this.$message.info('已恢复默认配置，请点击保存生效')
      }).catch(() => {
        // 用户取消
      })
    }
  }
}
</script>

<style scoped>
.security-config-page {
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.6;
}
</style>
