<!--
  StorageCardDialog.vue — 存储卡格式化与状态查询对话框

  来源: 后端改造项8(存储卡格式化, 2022版A.2.3.1.13) + 改造项14(存储卡状态查询, 2022版A.2.4.14)
  规范依据: 《WVP前端UI改造方案》第3.2节 F2
  技术栈: Vue 2.6 + Element UI 2.x

  功能说明:
    - 打开对话框时自动查询存储卡当前状态（是否存在、总容量、剩余容量）
    - 显示存储卡状态标签（正常/异常/不存在）
    - 提供格式化按钮（需二次确认），存储卡不存在时禁用
    - 格式化成功后自动刷新状态

  使用方式:
    <storage-card-dialog
      :visible.sync="showCardDialog"
      :device-id="currentDevice.deviceId"
      :channel-id="currentChannel.channelId"
      @formatted="onCardFormatted"
    />

  @author wvp-upgrade
  @since 2026-06-29
-->
<template>
  <el-dialog
    title="存储卡管理（GB/T 28181-2022）"
    :visible.sync="dialogVisible"
    width="520px"
    :close-on-click-modal="false"
    @open="handleDialogOpen"
    @closed="handleDialogClosed"
  >
    <!-- 加载状态 -->
    <div v-loading="queryLoading" element-loading-text="正在查询存储卡状态...">
      <!-- 存储卡状态标题 -->
      <h4 style="margin: 0 0 12px 0; font-size: 14px; color: #303133;">存储卡状态</h4>

      <!-- 状态信息描述列表 -->
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType" size="small" effect="plain">
            {{ statusText }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总容量">
          <template v-if="cardStatus.capacity != null">
            {{ formatCapacity(cardStatus.capacity) }}
          </template>
          <template v-else>
            <span style="color: #909399;">—</span>
          </template>
        </el-descriptions-item>
        <el-descriptions-item label="剩余容量">
          <template v-if="cardStatus.remainCapacity != null">
            {{ formatCapacity(cardStatus.remainCapacity) }}
          </template>
          <template v-else>
            <span style="color: #909399;">—</span>
          </template>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 查询错误提示 -->
      <el-alert
        v-if="queryError"
        :title="queryError"
        type="error"
        :closable="false"
        show-icon
        style="margin-top: 12px;"
      />
    </div>

    <!-- 底部操作栏 -->
    <div slot="footer" class="dialog-footer">
      <el-button @click="handleClose">关 闭</el-button>
      <el-popconfirm
        title="格式化将清空存储卡上所有数据，确定继续？"
        confirm-button-text="确认格式化"
        cancel-button-text="取消"
        icon="el-icon-warning"
        icon-color="#E6A23C"
        @confirm="handleFormat"
      >
        <el-button
          slot="reference"
          type="danger"
          :loading="formatLoading"
          :disabled="isCardAbsent"
        >
          格式化存储卡
        </el-button>
      </el-popconfirm>
    </div>
  </el-dialog>
</template>

<script>
/**
 * 存储卡管理对话框
 *
 * 依赖:
 *   - API: queryStorageCardStatus, formatStorageCard（来自 @/api/frontEnd）
 *   - Element UI: el-dialog, el-descriptions, el-tag, el-alert, el-popconfirm
 *
 * 数据流:
 *   1. 对话框打开 → queryStatus() → 更新 cardStatus → 驱动模板渲染
 *   2. 用户点击格式化 → 二次确认 → handleFormat() → formatLoading=true
 *      → 调用 formatStorageCard API → 成功后重新 queryStatus()
 *   3. 用户关闭对话框 → handleClose() → 重置状态
 */
import { queryStorageCardStatus, formatStorageCard } from '@/api/frontEnd'

export default {
  name: 'StorageCardDialog',

  // ========== Props ==========
  props: {
    /**
     * 对话框显示状态（支持 .sync 修饰符）
     */
    visible: {
      type: Boolean,
      default: false
    },
    /**
     * 设备编码（20位 GB/T 28181 编码）
     */
    deviceId: {
      type: String,
      required: true
    },
    /**
     * 通道编码
     */
    channelId: {
      type: String,
      required: true
    }
  },

  // ========== Data ==========
  data() {
    return {
      /** 查询加载状态 */
      queryLoading: false,
      /** 格式化加载状态 */
      formatLoading: false,
      /** 查询错误信息（null 表示无错误） */
      queryError: null,
      /**
       * 存储卡状态数据
       * @typedef {Object} CardStatus
       * @property {number} status         - 0=不存在, 1=正常, 2=异常
       * @property {number} capacity       - 总容量（MB）
       * @property {number} remainCapacity - 剩余容量（MB）
       */
      cardStatus: {}
    }
  },

  // ========== Computed ==========
  computed: {
    /**
     * 对话框显示状态（v-model 双向绑定 visible prop）
     */
    dialogVisible: {
      get() {
        return this.visible
      },
      set(val) {
        this.$emit('update:visible', val)
      }
    },

    /**
     * 存储卡是否不存在
     * 当 status === 0 时，格式化按钮禁用
     */
    isCardAbsent() {
      return this.cardStatus.status === 0
    },

    /**
     * 状态标签文本
     */
    statusText() {
      const map = {
        0: '未检测到存储卡',
        1: '状态正常',
        2: '状态异常'
      }
      return map[this.cardStatus.status] || '未知状态'
    },

    /**
     * 状态标签颜色类型（Element UI el-tag type）
     */
    statusTagType() {
      const map = {
        0: 'info',      // 灰色 — 不存在
        1: 'success',   // 绿色 — 正常
        2: 'danger'     // 红色 — 异常
      }
      return map[this.cardStatus.status] || 'info'
    }
  },

  // ========== Methods ==========
  methods: {
    // ---- 生命周期 ----

    /**
     * 对话框打开时：自动查询存储卡状态
     */
    handleDialogOpen() {
      this.queryStatus()
    },

    /**
     * 对话框关闭时：重置状态并清理定时器
     */
    handleDialogClosed() {
      // 清理格式化后延迟刷新定时器，防止组件销毁后仍执行
      if (this._formatTimer) {
        clearTimeout(this._formatTimer)
        this._formatTimer = null
      }
      this.queryError = null
      this.cardStatus = {}
    },

    // ---- 查询 ----

    /**
     * 查询存储卡当前状态
     *
     * 来源: 后端改造项14, 2022版A.2.4.14
     * API: GET /api/device/control/storage_card_status_query/{deviceId}/{channelId}
     */
    async queryStatus() {
      this.queryLoading = true
      this.queryError = null
      try {
        const { data } = await queryStorageCardStatus(this.deviceId, this.channelId)
        // 后端返回格式: { data: { storageCard: { status, capacity, remainCapacity } } }
        if (data && data.storageCard) {
          this.cardStatus = data.storageCard
        } else if (data && typeof data.status !== 'undefined') {
          // 兼容后端直接返回 storageCard 对象的情况
          this.cardStatus = data
        } else {
          this.queryError = '查询返回数据格式异常'
        }
      } catch (err) {
        this.queryError = `查询存储卡状态失败：${err.message || '网络异常'}`
        console.error('[存储卡管理] 状态查询失败', err)
      } finally {
        this.queryLoading = false
      }
    },

    // ---- 格式化 ----

    /**
     * 格式化存储卡（需二次确认）
     *
     * 来源: 后端改造项8, 2022版A.2.3.1.13
     * API: POST /api/device/control/format_sdcard/{deviceId}/{channelId}
     *
     * 注意: 本操作为破坏性操作，使用 POST 方法，且需要用户二次确认
     */
    async handleFormat() {
      this.formatLoading = true
      try {
        await formatStorageCard(this.deviceId, this.channelId)
        this.$message.success('格式化命令已下发，请稍后查看状态')
        // 格式化成功后自动刷新状态（延迟 2 秒等待设备响应）
        // timer 引用存入 this._formatTimer，在 handleDialogClosed 中清理
        this._formatTimer = setTimeout(() => {
          this._formatTimer = null
          this.queryStatus()
        }, 2000)
        // 通知父组件
        this.$emit('formatted')
      } catch (err) {
        this.$message.error(`格式化失败：${err.message || '网络异常'}`)
        console.error('[存储卡管理] 格式化失败', err)
      } finally {
        this.formatLoading = false
      }
    },

    // ---- 工具 ----

    /**
     * 格式化容量显示（MB → 可读格式）
     * 防御性处理：字符串输入转为数值、NaN/负数兜底
     *
     * @param {number|string} mb - 容量值（MB）
     * @returns {string}  格式化后的字符串，如 "64 GB"、"512 MB"
     */
    formatCapacity(mb) {
      if (mb == null || mb === '') return '—'
      const val = Number(mb)
      if (isNaN(val) || val < 0) return '—'
      if (val >= 1024) {
        return `${(val / 1024).toFixed(1)} GB`
      }
      return `${val} MB`
    },

    /**
     * 关闭对话框
     */
    handleClose() {
      this.dialogVisible = false
    }
  }
}
</script>

<style scoped>
/* 底部操作栏 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
