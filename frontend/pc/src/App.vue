<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：公共布局
  界面名称：应用根组件（挂载路由出口 <router-view/>，承载全部后台页面）
  ============================================
-->
<template>
  <router-view />
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
  background-color: #f0f2f5;
}

#app {
  min-height: 100vh;
}
</style>
