<!--
  SnapshotConfigDialog.vue — 图像抓拍配置对话框

  来源: 后端改造项15, 设计文档第12.2节, 2022版9.14
  规范依据: 《WVP前端UI改造方案》第3.7节 F7
  技术栈: Vue 2.6 + Element UI 2.x

  功能说明:
    - 选择抓拍分辨率（CIF/4CIF/D1/720P/1080P）
    - 设置连拍张数（1~10）
    - 下发抓拍命令，loading 状态反馈
    - 可查看最近抓拍结果列表（snapshotList）

  使用方式:
    <snapshot-config-dialog
      :visible.sync="showSnapshotDialog"
      :device-id="currentDevice.deviceId"
      :channel-id="currentChannel.channelId"
    />

  @author wvp-upgrade
  @since 2026-06-29
-->
<template>
  <el-dialog
    title="图像抓拍配置（GB/T 28181-2022）"
    :visible.sync="dialogVisible"
    width="520px"
    :close-on-click-modal="false"
    @closed="handleDialogClosed"
  >
    <!-- 配置表单 -->
    <el-form :model="form" label-width="100px" size="small">
      <!-- 分辨率选择 -->
      <!-- 注: Resolution 枚举值由后端 SnapshotConfigMessageHandler 定义，值区间 0~4 -->
      <el-form-item label="分辨率">
        <el-select v-model="form.resolution" placeholder="请选择分辨率" style="width: 100%;">
          <el-option label="CIF（352×288）" :value="0" />
          <el-option label="4CIF（704×576）" :value="1" />
          <el-option label="D1（704×576）" :value="2" />
          <el-option label="720P（1280×720）" :value="3" />
          <el-option label="1080P（1920×1080）" :value="4" />
        </el-select>
      </el-form-item>

      <!-- 抓拍数量 -->
      <el-form-item label="抓拍数量">
        <el-input-number
          v-model="form.snapNum"
          :min="1"
          :max="10"
          controls-position="right"
          style="width: 100%;"
        />
        <span style="margin-left: 8px; color: #909399; font-size: 12px;">范围：1~10 张</span>
      </el-form-item>
    </el-form>

    <!-- 抓拍结果列表（仅在有过抓拍后显示） -->
    <div v-if="snapshotList.length > 0" style="margin-top: 16px;">
      <h4 style="margin: 0 0 8px 0; font-size: 14px; color: #303133;">最近抓拍记录</h4>
      <el-table :data="snapshotList" size="small" border max-height="200">
        <el-table-column prop="index" label="序号" width="60" align="center" />
        <el-table-column prop="fileId" label="文件ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="timestamp" label="时间" width="160">
          <template slot-scope="scope">
            {{ scope.row.timestamp || '—' }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 底部操作栏 -->
    <div slot="footer" class="dialog-footer">
      <el-button @click="handleClose">关 闭</el-button>
      <el-button
        type="primary"
        :loading="capturing"
        @click="handleSnapshot"
      >
        开始抓拍
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
/**
 * 图像抓拍配置对话框
 *
 * 依赖:
 *   - API: snapshotConfig（来自 @/api/frontEnd）
 *   - Element UI: el-dialog, el-form, el-select, el-input-number, el-table, el-button
 *
 * 数据流:
 *   1. 用户配置分辨率和数量 → 点击"开始抓拍" → capturing=true
 *   2. 调用 snapshotConfig API（POST），参数含自动生成的 sessionId
 *   3. 成功后追加到 snapshotList 展示
 *
 * 注意:
 *   - interval（抓拍间隔）、uploadUrl（上传路径）、sessionId（会话ID）
 *     由前端自动生成默认值（sessionId=UUID），用户无需手动填写
 */
import { snapshotConfig } from '@/api/frontEnd'

export default {
  name: 'SnapshotConfigDialog',

  // ========== Props ==========
  props: {
    /**
     * 对话框显示状态（支持 .sync）
     */
    visible: {
      type: Boolean,
      default: false
    },
    /**
     * 设备编码
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
      abortController: null,
      timer: null,
      /**
       * 抓拍配置表单
       */
      form: {
        resolution: 3,  // 默认 720P
        snapNum: 1      // 默认 1 张
      },
      /**
       * 抓拍进行中标记
       */
      capturing: false,
      /**
       * 最近抓拍结果列表
       */
      snapshotList: []
    }
  },

  // ========== Computed ==========
  computed: {
    /**
     * 对话框显示状态（双向绑定）
     */
    dialogVisible: {
      get() {
        return this.visible
      },
      set(val) {
        this.$emit('update:visible', val)
      }
    }
  },

  // ========== Methods ==========
  beforeDestroy() {
    // 审计修复P3-03: 组件销毁前清理资源
    if (this.timer) { clearTimeout(this.timer); }
    if (this.abortController) { this.abortController.abort(); }
  },

  methods: {
    // ---- 生命周期 ----

    /**
     * 对话框关闭时：重置表单
     */
    handleDialogClosed() {
      this.form = {
        resolution: 3,
        snapNum: 1
      }
    },

    // ---- 抓拍 ----

    /**
     * 下发图像抓拍命令
     *
     * 来源: 后端改造项15, 2022版9.14
     * API: POST /api/device/control/snapshot_config/{deviceId}/{channelId}
     *
     * 传入参数:
     *   - resolution: 分辨率枚举值（0~4）
     *   - snapNum: 连拍张数（1~10）
     *   - interval: 抓拍间隔（可选，默认 undefined 由后端处理）
     *   - uploadUrl: 上传路径（可选）
     *   - sessionId: 前端生成 UUID 用于关联抓拍请求与结果通知
     */
    async handleSnapshot() {
      this.capturing = true
      try {
        const sessionId = this.generateUUID()
        await snapshotConfig({
          deviceId: this.deviceId,
          channelId: this.channelId,
          resolution: this.form.resolution,
          snapNum: this.form.snapNum,
          interval: undefined,    // 由后端使用默认值
          uploadUrl: undefined,   // 由后端使用默认上传路径
          sessionId               // 前端生成，用于关联抓拍通知
        })
        this.$message.success('抓拍命令已下发')
        // 记录本次抓拍
        this.snapshotList.unshift({
          index: this.snapshotList.length + 1,
          fileId: sessionId,
          timestamp: new Date().toLocaleString()
        })
        // 最多保留 20 条记录
        if (this.snapshotList.length > 20) {
          this.snapshotList = this.snapshotList.slice(0, 20)
        }
      } catch (err) {
        this.$message.error(`抓拍失败：${err.message || '网络异常'}`)
        console.error('[图像抓拍] 命令下发失败', err)
      } finally {
        this.capturing = false
      }
    },

    // ---- 工具 ----

    /**
     * 生成 UUID v4（用于 sessionId）
     *
     * 来源: 后端改造项10/15 规范要求 sessionID 为 32~128 字节唯一标识符
     *
     * @returns {string} UUID v4 字符串（如 "a1b2c3d4-..."）
     */
    generateUUID() {
      if (typeof crypto !== 'undefined' && crypto !== null && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID()
      }
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        const v = c === 'x' ? r : (r & 0x3) | 0x8
        return v.toString(16)
      })
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
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
