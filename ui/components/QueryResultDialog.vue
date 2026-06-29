<!--
  QueryResultDialog.vue — 2022版新增查询结果对话框

  来源: 改造项11(看守位查询, 2022版A.2.4.10) + 改造项12(巡航轨迹查询, 2022版A.2.4.11) +
        改造项13(PTZ精准状态查询, 2022版A.2.4.13)
  规范依据: 《WVP前端UI改造方案》第3.5节 F5
  技术栈: Vue 2.6 + Element UI 2.x

  功能说明:
    - 三个查询标签页：看守位信息 / 巡航轨迹 / PTZ 精准状态
    - 打开对话框时自动查询当前标签页对应数据
    - 支持切换标签页时重新查询
    - 数据展示、加载状态、错误提示

  使用方式:
    <query-result-dialog
      :visible.sync="showQueryDialog"
      :device-id="currentDevice.deviceId"
      :channel-id="currentChannel.channelId"
    />

  @author wvp-upgrade
  @since 2026-06-29
-->
<template>
  <el-dialog
    title="设备信息查询（GB/T 28181-2022）"
    :visible.sync="dialogVisible"
    width="750px"
    :close-on-click-modal="false"
    @open="onDialogOpen"
    @closed="onDialogClosed"
  >
    <!-- 加载状态 -->
    <div v-loading="queryLoading" element-loading-text="正在查询...">
      <!-- 标签页 -->
      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <!-- ========== Tab 1: 看守位信息 ========== -->
        <!-- 来源: 改造项11, 2022版A.2.4.10 -->
        <el-tab-pane label="看守位信息" name="homePosition">
          <template v-if="homePositionError">
            <el-alert :title="homePositionError" type="warning" :closable="false" show-icon />
          </template>
          <el-descriptions v-else :column="1" border size="small">
            <el-descriptions-item label="启用状态">
              <el-tag
                :type="homePositionInfo.enabled ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                {{ homePositionInfo.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="预置位编号">
              {{ homePositionInfo.presetIndex != null ? homePositionInfo.presetIndex : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="复位时间">
              {{ homePositionInfo.resetTime != null ? `${homePositionInfo.resetTime} 秒` : '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- ========== Tab 2: 巡航轨迹 ========== -->
        <!-- 来源: 改造项12, 2022版A.2.4.11 -->
        <el-tab-pane label="巡航轨迹" name="cruiseTrack">
          <template v-if="cruiseTrackError">
            <el-alert :title="cruiseTrackError" type="warning" :closable="false" show-icon />
          </template>
          <el-table
            v-else
            :data="cruiseTrackList"
            size="small"
            border
            max-height="350"
            empty-text="暂无巡航轨迹数据"
          >
            <el-table-column prop="id" label="轨迹 ID" width="80" align="center" />
            <el-table-column prop="name" label="轨迹名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="presetList" label="预置位序列" min-width="200" show-overflow-tooltip>
              <template slot-scope="scope">
                {{ Array.isArray(scope.row.presetList) ? scope.row.presetList.join(' → ') : scope.row.presetList || '—' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ========== Tab 3: PTZ 精准状态 ========== -->
        <!-- 来源: 改造项13, 2022版A.2.4.13 -->
        <el-tab-pane label="PTZ 精准状态" name="ptzStatus">
          <template v-if="ptzStatusError">
            <el-alert :title="ptzStatusError" type="warning" :closable="false" show-icon />
          </template>
          <el-descriptions v-else :column="1" border size="small">
            <el-descriptions-item label="水平转角（Pan）">
              {{ ptzStatus.pan != null ? `${ptzStatus.pan.toFixed(2)}°` : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="垂直转角（Tilt）">
              {{ ptzStatus.tilt != null ? `${ptzStatus.tilt.toFixed(2)}°` : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="变倍倍数（Zoom）">
              {{ ptzStatus.zoom != null ? `${ptzStatus.zoom.toFixed(1)}×` : '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 底部操作栏 -->
    <div slot="footer">
      <el-button type="primary" size="small" icon="el-icon-refresh" @click="refreshCurrentTab">
        刷 新
      </el-button>
      <el-button size="small" @click="handleClose">关 闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
/**
 * 2022版新增查询结果对话框
 *
 * 依赖:
 *   - API: queryHomePosition, queryCruiseTrack, queryPtzPreciseStatus（来自 @/api/frontEnd）
 *   - Element UI: el-dialog, el-tabs, el-descriptions, el-table, el-tag, el-alert
 *
 * 数据流:
 *   1. 对话框打开 → 自动查询当前标签页数据
 *   2. 用户切换标签页 → 自动查询对应数据（如尚未查询过）
 *   3. 用户点击刷新 → 重新查询当前标签页
 */
import { queryHomePosition, queryCruiseTrack, queryPtzPreciseStatus } from '@/api/frontEnd'

export default {
  name: 'QueryResultDialog',

  // ========== Props ==========
  props: {
    visible: { type: Boolean, default: false },
    deviceId: { type: String, required: true },
    channelId: { type: String, required: true }
  },

  // ========== Data ==========
  data() {
    return {
      /** 当前激活标签页 */
      activeTab: 'homePosition',
      /** 查询加载状态 */
      queryLoading: false,

      /** 看守位信息 */
      homePositionInfo: {},
      homePositionError: null,

      /** 巡航轨迹列表 */
      cruiseTrackList: [],
      cruiseTrackError: null,

      /** PTZ 精准状态 */
      ptzStatus: {},
      ptzStatusError: null
    }
  },

  // ========== Computed ==========
  computed: {
    dialogVisible: {
      get() { return this.visible },
      set(val) { this.$emit('update:visible', val) }
    }
  },

  // ========== Methods ==========
  methods: {
    // ---- 生命周期 ----

    /**
     * 对话框打开时：查询默认标签页
     */
    onDialogOpen() {
      this.queryCurrentTab()
    },

    /**
     * 对话框关闭时：重置所有状态
     */
    onDialogClosed() {
      this.activeTab = 'homePosition'
      this.homePositionInfo = {}
      this.homePositionError = null
      this.cruiseTrackList = []
      this.cruiseTrackError = null
      this.ptzStatus = {}
      this.ptzStatusError = null
    },

    // ---- 标签页切换 ----

    /**
     * 标签页点击事件
     * 切换到新标签页时自动触发查询
     */
    handleTabClick() {
      this.queryCurrentTab()
    },

    /**
     * 查询当前标签页数据
     */
    queryCurrentTab() {
      switch (this.activeTab) {
        case 'homePosition':
          this.queryHomePositionData()
          break
        case 'cruiseTrack':
          this.queryCruiseTrackData()
          break
        case 'ptzStatus':
          this.queryPtzPreciseStatusData()
          break
      }
    },

    /**
     * 刷新当前标签页
     */
    refreshCurrentTab() {
      this.queryCurrentTab()
    },

    // ---- 查询实现 ----

    /**
     * 查询看守位信息
     *
     * 来源: 后端改造项11, 2022版A.2.4.10
     * API: GET /api/device/control/home_position_query/{deviceId}/{channelId}
     */
    async queryHomePositionData() {
      this.queryLoading = true
      this.homePositionError = null
      try {
        const { data } = await queryHomePosition(this.deviceId, this.channelId)
        if (data && data.HomePosition) {
          this.homePositionInfo = {
            enabled: data.HomePosition.Enabled === 'true' || data.HomePosition.Enabled === true,
            presetIndex: data.HomePosition.PresetID || data.HomePosition.presetIndex,
            resetTime: data.HomePosition.ResetTime || data.HomePosition.resetTime
          }
        } else if (data && typeof data.enabled !== 'undefined') {
          this.homePositionInfo = data
        } else {
          this.homePositionError = '设备暂未返回看守位信息'
        }
      } catch (err) {
        this.homePositionError = `查询失败：${err.message || '网络异常'}`
        console.error('[查询] 看守位查询失败', err)
      } finally {
        this.queryLoading = false
      }
    },

    /**
     * 查询巡航轨迹
     *
     * 来源: 后端改造项12, 2022版A.2.4.11
     * API: GET /api/device/control/cruise_track_query/{deviceId}/{channelId}
     */
    async queryCruiseTrackData() {
      this.queryLoading = true
      this.cruiseTrackError = null
      try {
        const { data } = await queryCruiseTrack(this.deviceId, this.channelId)
        if (data && data.CruiseTrackList && data.CruiseTrackList.CruiseTrack) {
          const tracks = data.CruiseTrackList.CruiseTrack
          this.cruiseTrackList = Array.isArray(tracks) ? tracks : [tracks]
        } else if (Array.isArray(data)) {
          this.cruiseTrackList = data
        } else if (data && data.list) {
          this.cruiseTrackList = data.list
        } else {
          this.cruiseTrackList = []
        }
        if (this.cruiseTrackList.length === 0) {
          this.cruiseTrackError = '设备暂无巡航轨迹数据'
        }
      } catch (err) {
        this.cruiseTrackError = `查询失败：${err.message || '网络异常'}`
        console.error('[查询] 巡航轨迹查询失败', err)
      } finally {
        this.queryLoading = false
      }
    },

    /**
     * 查询 PTZ 精准状态
     *
     * 来源: 后端改造项13, 2022版A.2.4.13
     * API: GET /api/device/control/ptz_precise_status_query/{deviceId}/{channelId}
     */
    async queryPtzPreciseStatusData() {
      this.queryLoading = true
      this.ptzStatusError = null
      try {
        const { data } = await queryPtzPreciseStatus(this.deviceId, this.channelId)
        if (data && data.Pan !== undefined) {
          this.ptzStatus = {
            pan: parseFloat(data.Pan),
            tilt: parseFloat(data.Tilt),
            zoom: parseFloat(data.Zoom)
          }
        } else if (data && typeof data.pan !== 'undefined') {
          this.ptzStatus = data
        } else {
          this.ptzStatusError = '设备暂未返回 PTZ 精准状态'
        }
      } catch (err) {
        this.ptzStatusError = `查询失败：${err.message || '网络异常'}`
        console.error('[查询] PTZ精准状态查询失败', err)
      } finally {
        this.queryLoading = false
      }
    },

    // ---- 工具 ----

    handleClose() {
      this.dialogVisible = false
    }
  }
}
</script>
