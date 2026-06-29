# ptzControls.vue 改造补丁说明

## 来源

- F1: PTZ 精准控制面板（后端改造项7，2022版A.2.3.1.11）
- F3: 目标跟踪控制（后端改造项9，2022版A.2.3.1.14）

## 改造文件

`web/src/views/common/ptzControls.vue`

## 改造原则

不修改原有 PTZ 控制面板的代码和结构，在文件末尾以增量方式追加以下内容。

---

## 1. 新增 Props

在现有 `props` 对象中新增 `isVersion2022` 属性：

```javascript
props: {
  // ... 已有 props ...

  /**
   * 设备是否为 2022 版本
   * 来源: 审计A2, 通用组件无法获取设备版本，需由父组件 live/index.vue 传入
   * 用法: <ptz-controls :is-version2022="device.isVersion2022()" />
   */
  isVersion2022: {
    type: Boolean,
    default: false
  }
}
```

## 2. 新增 Data 属性

在 `data()` 返回对象中新增：

```javascript
data() {
  return {
    // ... 已有 data ...

    // ===== F1改造: PTZ精准控制 =====
    // 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11
    preciseVisible: false,   // 精准控制面板展开状态
    precisePan: 0,           // 水平角度，范围 -180.00 ~ 180.00
    preciseTilt: 0,          // 垂直角度，范围 -90.00 ~ 90.00
    preciseZoom: 1,          // 变倍倍数，范围 0 ~ 20

    // ===== F3改造: 目标跟踪 =====
    // 来源: 后端改造项9, 2022版A.2.3.1.14
    trackMode: 'Auto'        // 目标跟踪模式: 'Auto'（自动跟踪）/ 'Manual'（手动跟踪）
  }
}
```

## 3. 新增 Methods

在 `methods` 对象中新增：

```javascript
methods: {
  // ... 已有 methods ...

  /**
   * PTZ 精准控制
   * 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11 PTZ精准控制
   * 发出 ptz-precise 事件，由父组件 live/index.vue 监听并调用 ptzPrecise API
   */
  handlePtzPrecise() {
    this.$emit('ptz-precise', {
      pan: this.precisePan,
      tilt: this.preciseTilt,
      zoom: this.preciseZoom
    })
  }
}
```

## 4. 新增 Template

在 `</template>` 闭合标签**之前**，PTZ 控制面板底部追加以下 HTML：

```vue
<!-- ===== F1改造: PTZ精准控制面板 ===== -->
<!-- 来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11 -->
<!-- 规范要求: 2022版新增PTZ精准控制, 支持pan/Tilt/zoom精确角度设置 -->
<!-- D7已修复: 补充展开/收起按钮触发 preciseVisible -->
<div v-if="isVersion2022" class="ptz-precise-toggle" @click="preciseVisible = !preciseVisible">
  <i :class="preciseVisible ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" />
  <span>精准控制（GB/T 28181-2022）</span>
</div>
<div v-show="preciseVisible" class="ptz-precise-section" style="transition: all 0.3s;">
  <el-form :inline="true" size="mini" label-width="60px">
    <el-form-item label="水平角">
      <el-input-number v-model="precisePan"
        :precision="2" :step="0.1" :min="-180" :max="180"
        controls-position="right" size="mini" style="width: 120px" />
    </el-form-item>
    <el-form-item label="垂直角">
      <el-input-number v-model="preciseTilt"
        :precision="2" :step="0.1" :min="-90" :max="90"
        controls-position="right" size="mini" style="width: 120px" />
    </el-form-item>
    <el-form-item label="变倍">
      <el-input-number v-model="preciseZoom"
        :precision="1" :step="0.5" :min="0" :max="20"
        controls-position="right" size="mini" style="width: 120px" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" size="mini" @click="handlePtzPrecise">发送</el-button>
    </el-form-item>
  </el-form>
</div>

<!-- ===== F3改造: 目标跟踪控制按钮 ===== -->
<!-- 来源: 后端改造项9, 设计文档第10.1节, 2022版A.2.3.1.14 -->
<!-- 规范要求: 2022版新增目标跟踪, action: Auto(自动)/Manual(手动) -->
<!-- 注: 停止跟踪由后端超时自动处理，非前端按钮功能 -->
<div v-if="isVersion2022" class="ptz-func-row">
  <div class="ptz-func-btn" :class="{ active: trackMode === 'Auto' }"
    title="自动跟踪" @click="$emit('target-track', 'Auto')">
    <i class="iconfont icon-track" /><span>自动跟踪</span>
  </div>
  <div class="ptz-func-btn" :class="{ active: trackMode === 'Manual' }"
    title="手动跟踪" @click="$emit('target-track', 'Manual')">
    <i class="iconfont icon-track-manual" /><span>手动跟踪</span>
  </div>
</div>
```

## 5. 父组件集成

在 `live/index.vue`（使用 ptzControls 的父组件）中：

```vue
<ptz-controls
  @ptz-precise="handlePtzPrecise"
  @target-track="handleTargetTrack"
  :is-version2022="device.isVersion2022()"
/>
```

```javascript
import { ptzPrecise, targetTrack } from '@/api/frontEnd'

methods: {
  async handlePtzPrecise({ pan, tilt, zoom }) {
    try {
      await ptzPrecise({
        deviceId: this.device.deviceId,
        channelId: this.channel.channelId,
        pan, tilt, zoom
      })
      this.$message.success('PTZ命令已发送')
    } catch (err) {
      this.$message.error('PTZ命令发送失败')
    }
  },

  async handleTargetTrack(action) {
    try {
      await targetTrack({
        deviceId: this.device.deviceId,
        channelId: this.channel.channelId,
        action
      })
      this.$message.success(`目标跟踪已切换为${action === 'Auto' ? '自动' : '手动'}模式`)
    } catch (err) {
      this.$message.error('目标跟踪命令发送失败')
    }
  }
}
```
