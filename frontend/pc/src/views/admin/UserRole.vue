<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：系统管理
  界面名称：用户角色管理页（账号增删改 / 角色分配）
  ============================================
-->
<template>
  <div class="page-container">
    <!-- 后台用户 -->
    <div class="panel-card section">
      <div class="section-title">
        <span>后台操作员账号</span>
        <div class="title-tools">
          <el-select v-model="roleFilter" placeholder="按角色筛选" clearable style="width: 160px">
            <el-option v-for="r in roleList" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <el-button type="primary" :icon="Plus" @click="openEdit()">新增账号</el-button>
        </div>
      </div>

      <el-table :data="filteredUserList" v-loading="loading" stripe>
        <el-table-column prop="username" label="登录账号" width="140" />
        <el-table-column prop="realName" label="姓名" width="170" />
        <el-table-column prop="roleName" label="角色" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.state) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.state) === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="toggleState(row)">{{ Number(row.state) === 1 ? '禁用' : '启用' }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 角色权限 -->
    <div class="panel-card section">
      <div class="section-title">
        <span>角色权限列表</span>
      </div>
      <el-table :data="roleList" v-loading="roleLoading" stripe>
        <el-table-column prop="name" label="角色名称" width="160" />
        <el-table-column prop="id" label="角色编码" width="200" />
        <el-table-column prop="desc" label="权限描述" min-width="260" />
        <el-table-column prop="userCount" label="账号数" width="100" />
      </el-table>
    </div>

    <!-- 账号编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editRow ? '编辑账号' : '新增账号'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="登录账号" required>
          <el-input v-model="editForm.username" :disabled="!!editRow" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="editForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="editForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roleList" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editRow" label="初始密码" required>
          <el-input v-model="editForm.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.state" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getRoleList, getUserList, saveUser, updateUserState, deleteUser } from '../../api/admin'

const loading = ref(false)     // 用户列表加载中状态
const roleLoading = ref(false) // 角色列表加载中状态
const userList = ref([])       // 操作员账号列表
const roleList = ref([])       // 角色列表
const roleFilter = ref(null)   // 角色筛选值

// 按角色筛选操作员账号（roleFilter 为空则返回全部）
const filteredUserList = computed(() => {
  if (!roleFilter.value) return userList.value
  return userList.value.filter(u => u.role === roleFilter.value)
})

// 拉取操作员账号列表
async function loadUsers() {
  loading.value = true
  try {
    const res = await getUserList()
    userList.value = res.records || []
  } catch (e) {
    ElMessage.error(e.message || '操作员列表加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

// 拉取角色权限列表
async function loadRoles() {
  roleLoading.value = true
  try {
    const res = await getRoleList()
    roleList.value = res.records || []
  } catch (e) {
    ElMessage.error(e.message || '角色列表加载失败，请检查后端服务')
  } finally {
    roleLoading.value = false
  }
}

/* ===== 账号编辑 ===== */
const editVisible = ref(false) // 编辑弹窗显隐
const editRow = ref(null)      // 当前编辑的账号行（null 表示新增）
const editForm = reactive({ username: '', realName: '', role: '', password: '', state: 1 }) // 账号表单

// 打开新增/编辑弹窗：有 row 为编辑并回填，无 row 为新增并重置
// 两个分支都显式清空 password，避免「先新增填了密码再取消、随后编辑账号」把残留密码提交覆盖他人登录密码
function openEdit(row) {
  editRow.value = row
  if (row) {
    editForm.username = row.username
    editForm.realName = row.realName
    editForm.role = row.role
    editForm.state = Number(row.state)
  } else {
    editForm.username = ''
    editForm.realName = ''
    editForm.role = ''
    editForm.state = 1
  }
  editForm.password = ''
  editVisible.value = true
}

// 保存账号：校验必填项后调用后端接口入库，成功后刷新列表
async function confirmSave() {
  if (!editForm.username.trim() || !editForm.realName.trim() || !editForm.role) {
    ElMessage.warning('请填写完整账号信息')
    return
  }
  if (!editRow.value && !editForm.password) {
    ElMessage.warning('请输入初始密码')
    return
  }
  const data = {
    username: editForm.username.trim(),
    realName: editForm.realName.trim(),
    role: editForm.role,
    state: editForm.state
  }
  if (editRow.value) {
    data.id = editRow.value.id
    if (editForm.password) data.password = editForm.password // 留空表示不修改密码
  } else {
    data.password = editForm.password
  }
  try {
    await saveUser(data)
    ElMessage.success('保存成功')
    editVisible.value = false
    loadUsers()
    loadRoles()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

// 切换账号状态（启用/禁用），二次确认后调用后端接口
async function toggleState(row) {
  const action = Number(row.state) === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确认${action}账号「${row.username}」？`, '状态变更', { type: 'warning' })
  try {
    await updateUserState({ id: row.id, state: Number(row.state) === 1 ? 0 : 1 })
    ElMessage.success(`${action}成功`)
    loadUsers()
    loadRoles()
  } catch (e) {
    ElMessage.error(e.message || `${action}失败`)
  }
}

// 删除账号，二次确认后调用后端接口（后端防呆：不能删除当前登录账号）
async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除账号「${row.username}」？`, '删除确认', { type: 'warning' })
  try {
    await deleteUser({ id: row.id })
    ElMessage.success('删除成功')
    loadUsers()
    loadRoles()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<style scoped>
.section {
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.title-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
