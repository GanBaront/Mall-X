<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：支付配置
  界面名称：支付接口配置页（渠道密钥 / 启用开关 / 基础费率）
  ============================================
-->
<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col v-for="ch in channelList" :key="ch.wayCode" :span="8">
        <div class="panel-card channel-card">
          <div class="channel-header">
            <span class="channel-name" :class="'ch-' + ch.wayCode.toLowerCase()">{{ ch.name }}</span>
            <el-switch
              v-model="ch.state"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
              inline-prompt
            />
          </div>

          <el-form label-width="90px" class="channel-form">
            <el-form-item label="应用ID">
              <el-input v-model="ch.appId" placeholder="渠道应用 APPID" />
            </el-form-item>
            <el-form-item label="应用密钥">
              <el-input v-model="ch.appSecret" type="password" show-password placeholder="渠道应用密钥" />
            </el-form-item>
            <el-form-item label="商户号">
              <el-input v-model="ch.mchId" placeholder="渠道商户号" />
            </el-form-item>
            <el-form-item label="接口密钥">
              <el-input v-model="ch.apiV3Key" type="password" show-password placeholder="API 接口密钥" />
            </el-form-item>
            <el-form-item label="基础费率">
              <el-input-number v-model="ch.rateNum" :min="0" :max="10" :step="0.01" :precision="2" />
              <span class="rate-unit">%</span>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="ch.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>

          <div class="channel-footer">
            <el-button type="primary" :loading="ch._saving" @click="handleSave(ch)">保存配置</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 配置说明提示 -->
    <el-alert
      class="page-tip"
      type="info"
      :closable="false"
      title="说明：渠道参数（接口参数/密钥）、渠道开关与基础费率统一在此配置，商户可在商户列表中配置差异化费率（优先级高于基础费率）。"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getChannelConfig, saveChannelConfig } from '../../api/admin'

const channelList = ref([]) // 支付渠道配置列表

// 查询渠道配置：将后端返回的 rate 字符串解析为数字 rateNum 便于 el-input-number 绑定，并初始化保存状态
async function loadData() {
  try {
    const res = await getChannelConfig()
    channelList.value = (res.records || []).map(ch => ({
      ...ch,
      rateNum: ch.rate ? parseFloat(ch.rate) : 0, // 费率字符串转数字
      _saving: false // 单卡片保存中标记
    }))
  } catch (e) {
    ElMessage.error(e.message || '渠道配置加载失败，请检查后端服务')
  }
}

// 保存单个渠道配置：组装参数（wayCode/state/appId/密钥/费率）提交后端
async function handleSave(ch) {
  ch._saving = true
  try {
    const res = await saveChannelConfig({
      wayCode: ch.wayCode,
      state: ch.state,
      appId: ch.appId,
      appSecret: ch.appSecret,
      mchId: ch.mchId,
      apiV3Key: ch.apiV3Key,
      rate: ch.rateNum
    })
    ElMessage.success(`${ch.name}：${res.message || '保存成功'}`)
  } finally {
    ch._saving = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.channel-card {
  display: flex;
  flex-direction: column;
  min-height: 430px;
}

.channel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.channel-name {
  font-size: 16px;
  font-weight: 700;
  color: #1f2329;
}

.ch-wechat {
  color: #07c160;
}

.ch-alipay {
  color: #1677ff;
}

.ch-unionpay {
  color: #e20b1c;
}

.channel-form {
  flex: 1;
}

.rate-unit {
  margin-left: 8px;
  color: #606266;
}

.channel-footer {
  text-align: right;
  padding-top: 8px;
}

.page-tip {
  margin-top: 16px;
}
</style>
