<!--
  ============================================
  【前端 - PC 平台管理后台】
  所属部分：系统管理
  界面名称：商品类目管理页（树形类目增删改）
  ============================================
-->
<template>
  <div class="page-container">
    <div class="panel-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openEdit(null, 0)">新增一级类目</el-button>
      </div>

      <el-table
        :data="categoryTree"
        v-loading="loading"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        stripe
      >
        <el-table-column prop="name" label="类目名称" min-width="260" />
        <el-table-column prop="id" label="类目ID" width="110" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="Number(row.state) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.state) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row, 1)">新增子类目</el-button>
            <el-button link type="primary" @click="openEdit(row, -1)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 类目编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editTitle" width="460px">
      <el-form label-width="90px">
        <el-form-item label="上级类目">
          <el-input :model-value="parentName" disabled />
        </el-form-item>
        <el-form-item label="类目名称" required>
          <el-input v-model="editForm.name" placeholder="请输入类目名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.state" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { deleteCategory, getCategoryTree, saveCategory } from '../../api/admin'

const loading = ref(false)     // 列表加载中状态
const submitting = ref(false)  // 弹窗保存中状态
const categoryTree = ref([])   // 树形类目数据

// 编辑模式：0-新增一级 / 1-新增子级 / -1-编辑
const editMode = ref(0)
const editRow = ref(null)          // 当前操作的类目行
const editVisible = ref(false)     // 编辑弹窗显隐
const editForm = reactive({ name: '', sort: 0, state: 1 }) // 类目表单

// 弹窗标题：根据 editMode 显示「编辑类目 / 新增子类目 / 新增一级类目」
const editTitle = computed(() => {
  if (editMode.value === -1) return '编辑类目'
  return editMode.value === 1 ? '新增子类目' : '新增一级类目'
})

// 上级类目名称展示：一级类目显示「（无，一级类目）」
const parentName = computed(() => {
  if (editMode.value === 0) return '（无，一级类目）'
  return editRow.value?.name || ''
})

// 拉取商品类目树
async function loadData() {
  loading.value = true
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.records || []
  } catch {
    // 失败提示已由 api 层统一弹出，这里仅终止 loading
  } finally {
    loading.value = false
  }
}

// 打开新增/编辑弹窗：mode 决定新增一级/子级或编辑并回填
function openEdit(row, mode) {
  editRow.value = row
  editMode.value = mode
  if (mode === -1) {
    editForm.name = row.name
    editForm.sort = row.sort
    editForm.state = Number(row.state)
  } else {
    editForm.name = ''
    editForm.sort = 0
    editForm.state = 1
  }
  editVisible.value = true
}

// 保存类目：组装 payload（编辑带 id，新增子级带 parentId，一级 parentId=0）提交
async function confirmSave() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请输入类目名称')
    return
  }
  submitting.value = true
  try {
    const payload = {
      id: editMode.value === -1 ? editRow.value.id : undefined,
      // 编辑时不传 parentId（后端仅在该字段非 null 时更新，传 0 会把子类目强制改为一级）；
      // 新增子级带父 id，新增一级 parentId=0
      parentId: editMode.value === 1 ? editRow.value.id : (editMode.value === -1 ? undefined : 0),
      name: editForm.name.trim(),
      sort: editForm.sort,
      state: editForm.state
    }
    const res = await saveCategory(payload)
    ElMessage.success(res.message || '保存成功')
    editVisible.value = false
    loadData()
  } catch {
    // 失败提示已由 api 层统一弹出
  } finally {
    submitting.value = false
  }
}

// 删除类目：存在子类目时禁止删除，二次确认后调用删除接口
async function handleDelete(row) {
  if (row.children && row.children.length > 0) {
    ElMessage.warning('该类目下存在子类目，请先删除子类目')
    return
  }
  await ElMessageBox.confirm(`确认删除类目「${row.name}」？`, '删除确认', { type: 'warning' })
  const res = await deleteCategory({ id: row.id })
  ElMessage.success(res.message || '删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
