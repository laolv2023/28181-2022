<!--
  DeviceUpgradeDialog.vue — 设备软件升级对话框

  来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12
  规范依据: 《WVP前端UI改造方案》第3.4节 F4
  技术栈: Vue 2.6 + Element UI 2.x

  功能说明:
    - 用户选择本地固件文件（.bin/.img/.zip）
    - 上传固件到服务器获取 fileUrl
    - 下发升级命令（含 firmware、fileUrl、manufacturer、sessionId）
    - 显示升级进度和结果

  使用方式:
    <device-upgrade-dialog
      :visible.sync="showUpgradeDialog"
      :device-id="currentDevice.deviceId"
      :channel-id="currentChannel.channelId"
      @upgraded="onDeviceUpgraded"
    />

  @author wvp-upgrade
  @since 2026-06-29
-->
<template>
  <el-dialog
    title="设备软件升级（GB/T 28181-2022）"
    :visible.sync="dialogVisible"
    width="550px"
    :close-on-click-modal="false"
    @closed="handleDialogClosed"
  >
    <!-- 升级步骤说明 -->
    <el-alert
      title="升级流程"
      type="info"
      :closable="false"
      style="margin-bottom: 16px;"
    >
      <div style="font-size: 12px; line-height: 1.8;">
        1. 选择设备固件文件（.bin / .img / .zip）<br>
        2. 系统自动上传固件至服务器<br>
        3. 填写设备制造商信息<br>
        4. 点击"开始升级"下发升级命令
      </div>
    </el-alert>

    <!-- 升级表单 -->
    <el-form
      ref="upgradeForm"
      :model="form"
      :rules="formRules"
      label-width="100px"
      size="small"
    >
      <!-- 设备信息（只读展示） -->
      <el-form-item label="目标设备">
        <el-input :value="deviceId" disabled style="width: 100%;" />
      </el-form-item>

      <!-- 固件文件选择 -->
      <el-form-item label="固件文件" prop="firmwareFile">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :accept="'.bin,.img,.zip'"
          action="#"
          style="width: 100%;"
        >
          <el-button size="small" type="primary" :disabled="uploading">
            <i class="el-icon-upload2" style="margin-right: 4px;" />
            {{ form.firmware ? '重新选择' : '选择固件文件' }}
          </el-button>
          <span slot="tip" style="margin-left: 8px; color: #909399; font-size: 12px;">
            支持 .bin / .img / .zip 格式
          </span>
        </el-upload>
      </el-form-item>

      <!-- 已选文件展示 -->
      <el-form-item v-if="form.firmware" label="已选文件">
        <el-tag
          :type="uploadSuccess ? 'success' : 'info'"
          size="small"
          effect="plain"
          closable
          @close="handleFileRemove"
        >
          {{ form.firmware }}
          <template v-if="uploadSuccess"> — 上传完成</template>
        </el-tag>
      </el-form-item>

      <!-- 设备制造商 -->
      <el-form-item label="制造商" prop="manufacturer">
        <el-input
          v-model="form.manufacturer"
          placeholder="请输入设备制造商名称（如 Hikvision）"
          maxlength="128"
          show-word-limit
          style="width: 100%;"
        />
      </el-form-item>
    </el-form>

    <!-- 底部操作栏 -->
    <div slot="footer" class="dialog-footer">
      <el-button @click="handleClose">关 闭</el-button>
      <el-button
        type="primary"
        :loading="upgrading"
        :disabled="!canUpgrade"
        @click="handleUpgrade"
      >
        {{ uploading ? '上传中...' : '开始升级' }}
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
/**
 * 设备软件升级对话框
 *
 * 依赖:
 *   - API: uploadFirmware, deviceUpgrade（来自 @/api/frontEnd）
 *   - Element UI: el-dialog, el-form, el-upload, el-input, el-tag, el-alert, el-button
 *
 * 数据流:
 *   1. 用户选择文件 → handleFileChange → 记录文件信息
 *   2. 用户点击"开始升级" → handleUpgrade → uploading=true
 *      → 调用 uploadFirmware 上传 → 获取 fileUrl
 *      → 调用 deviceUpgrade 下发升级命令
 *   3. 显示结果
 *
 * 注意:
 *   - 固件上传使用 FormData，不手动设置 Content-Type，由 axios 自动添加 boundary
 *   - sessionId 由前端生成 UUID，用于关联升级请求与结果通知
 */
import { uploadFirmware, deviceUpgrade } from '@/api/frontEnd'

export default {
  name: 'DeviceUpgradeDialog',

  // ========== Props ==========
  props: {
    visible: { type: Boolean, default: false },
    deviceId: { type: String, required: true },
    channelId: { type: String, required: true }
  },

  // ========== Data ==========
  data() {
    return {
      /**
       * 升级表单
       */
      form: {
        deviceId: '',           // 设备编码（初始化时自动填充）
        firmware: '',           // 固件文件名（用户选择后填充）
        firmwareFile: null,     // 原始 File 对象
        manufacturer: ''        // 设备制造商
      },
      /**
       * 表单验证规则
       */
      formRules: {
        manufacturer: [
          { required: true, message: '请输入设备制造商', trigger: 'blur' }
        ]
      },
      /**
       * 上传进行中
       */
      uploading: false,
      /**
       * 上传是否成功（得到 fileUrl）
       */
      uploadSuccess: false,
      /**
       * 上传得到的文件 URL
       */
      uploadedFileUrl: '',
      /**
       * 升级进行中
       */
      upgrading: false
    }
  },

  // ========== Computed ==========
  computed: {
    dialogVisible: {
      get() { return this.visible },
      set(val) { this.$emit('update:visible', val) }
    },

    /**
     * 是否允许开始升级
     * 条件：已选择文件 且 上传成功 且 已填写制造商 且 当前无进行中的上传/升级操作
     */
    canUpgrade() {
      return this.uploadSuccess &&
             this.form.manufacturer.trim() !== '' &&
             !this.uploading &&
             !this.upgrading
    }
  },

  // ========== Methods ==========
  methods: {
    // ---- 生命周期 ----

    /**
     * 对话框关闭时：重置所有状态
     */
    handleDialogClosed() {
      this.resetForm()
    },

    /**
     * 重置表单和状态
     */
    resetForm() {
      this.form = {
        deviceId: this.deviceId,
        firmware: '',
        firmwareFile: null,
        manufacturer: ''
      }
      this.uploading = false
      this.uploadSuccess = false
      this.uploadedFileUrl = ''
      this.upgrading = false
      if (this.$refs.uploadRef) {
        this.$refs.uploadRef.clearFiles()
      }
    },

    // ---- 文件操作 ----

    /**
     * 文件选择变化事件
     *
     * 来源: 后端改造项10, 设备软件升级文件选择
     * 校验: 文件类型（.bin/.img/.zip）、空文件、大小限制（200MB）
     *
     * @param {Object} file - Element UI el-upload 的 file 对象
     * @param {Array}  fileList - 当前文件列表
     */
    handleFileChange(file, fileList) {
      // 只保留最后一个文件
      if (fileList.length > 1) {
        fileList.splice(0, 1)
      }
      const rawFile = file.raw
      if (!rawFile) {
        this.$message.warning('文件对象无效，请重新选择')
        return
      }
      // 校验文件类型（JS 层面二次校验，防止绕过 HTML accept 属性）
      const ALLOWED_EXT = ['.bin', '.img', '.zip']
      const fileName = rawFile.name.toLowerCase()
      const ext = '.' + fileName.split('.').pop()
      if (!ALLOWED_EXT.includes(ext)) {
        this.$message.error(`不支持的文件类型：${ext}，请选择 .bin / .img / .zip 格式的固件文件`)
        fileList.splice(0, 1)
        return
      }
      // 校验空文件
      if (rawFile.size === 0) {
        this.$message.error('不能上传空文件')
        fileList.splice(0, 1)
        return
      }
      // 校验文件大小（上限 200MB）
      const MAX_SIZE = 200 * 1024 * 1024
      if (rawFile.size > MAX_SIZE) {
        this.$message.error(`文件过大：${(rawFile.size / 1024 / 1024).toFixed(1)}MB，上限 200MB`)
        fileList.splice(0, 1)
        return
      }
      this.form.firmware = file.name
      this.form.firmwareFile = rawFile
      this.uploadSuccess = false
      this.uploadedFileUrl = ''
    },

    /**
     * 文件移除事件
     */
    handleFileRemove() {
      this.form.firmware = ''
      this.form.firmwareFile = null
      this.uploadSuccess = false
      this.uploadedFileUrl = ''
    },

    // ---- 升级 ----

    /**
     * 开始升级流程
     *
     * 来源: 后端改造项10, 2022版A.2.3.1.12
     *
     * 流程:
     *   1. 上传固件文件 → 获取 fileUrl
     *   2. 下发升级命令 → 传入 firmware / fileUrl / manufacturer / sessionId
     */
    async handleUpgrade() {
      // 表单验证
      const valid = await this.$refs.upgradeForm.validate().catch(() => false)
      if (!valid) return

      // 二次确认
      try {
        await this.$confirm(
          '升级过程中设备可能会重启，确定继续？',
          '确认升级',
          { type: 'warning', confirmButtonText: '确定升级', cancelButtonText: '取消' }
        )
      } catch {
        return  // 用户取消
      }

      this.upgrading = true

      try {
        // Step 1: 上传固件文件
        if (!this.uploadSuccess) {
          await this.doUpload()
        }

        // Step 2: 下发升级命令
        const sessionId = this.generateUUID()
        await deviceUpgrade({
          deviceId: this.deviceId,
          channelId: this.channelId,
          firmware: this.form.firmware,
          fileUrl: this.uploadedFileUrl,
          manufacturer: this.form.manufacturer.trim(),
          sessionId
        })

        this.$message.success('升级命令已下发，设备将在下载固件后自动升级')
        this.$emit('upgraded', { sessionId })
        this.handleClose()
      } catch (err) {
        this.$message.error(`升级失败：${err.message || '网络异常'}`)
        console.error('[设备升级] 升级失败', err)
      } finally {
        this.upgrading = false
      }
    },

    /**
     * 上传固件文件
     *
     * API: POST /api/device/control/upload_firmware/{deviceId}
     * 注意: FormData 上传，不手动设置 Content-Type（由 axios 自动添加 boundary）
     */
    async doUpload() {
      this.uploading = true
      try {
        const { data } = await uploadFirmware(this.deviceId, this.form.firmwareFile)
        // 防御性获取 fileUrl：兼容多种后端响应格式
        const url = data && (data.fileUrl || data.url)
        if (typeof url !== 'string' || url.length === 0) {
          throw new Error('服务器返回的文件地址无效')
        }
        this.uploadedFileUrl = url
        this.uploadSuccess = true
        this.$message.success('固件文件上传成功')
      } catch (err) {
        this.uploadSuccess = false
        throw new Error(`固件上传失败：${err.message || '网络异常'}`)
      } finally {
        this.uploading = false
      }
    },

    // ---- 工具 ----

    /**
     * 生成 UUID v4
     *
     * @returns {string} UUID v4 字符串
     */
    generateUUID() {
      // 优先使用浏览器原生 crypto.randomUUID()，需同时检查 crypto 非 null
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
