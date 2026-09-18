<script setup lang="ts">
/**
 * 搜索结果页：/search?keyword=手机&sortBy=salesDesc
 *
 * 为什么关键词放 URL 的 query，而不是组件内的一个 ref：
 *   ① 刷新页面结果不丢  ② 链接能直接分享给别人  ③ 浏览器后退能回到上一次搜索
 *
 * ⭐「监听搜索」的关键在 watch(route.query)：
 *   从 /search?keyword=A 跳到 /search?keyword=B 时，路径没变、只有 query 变了，
 *   Vue Router 会**复用同一个组件实例**，onMounted 不会再执行。
 *   所以必须监听 query 的变化，用户在搜索页"再搜一次"才会真的刷新结果。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchProducts, type ProductItem } from '@/api/product'

const route = useRoute()
const router = useRouter()

/** 当前生效的关键词：唯一真相来自 URL */
const keyword = ref('')
/** 输入框自己的值：与 URL 解耦，用户打字时不会疯狂改 URL */
const input = ref('')

// ===== 结果与分页 =====
const PAGE_SIZE = 20                         // 每页条数
const list = ref<ProductItem[]>([])          // 已加载的商品（只增不减，顺序追加）
const pageNum = ref(1)                       // 下一页的页码
const total = ref(0)                         // 后端返回的命中总数
const loading = ref(false)
const finished = ref(false)                  // 已经取完，没有下一页
const errorMsg = ref('')
const imgFail = ref(new Set<number>())       // 图片加载失败的商品 id

/** 排序：label 是显示名，value 直接传给后端 sortBy */
const SORTS = [
  { label: '综合', value: 'default' },
  { label: '价格 ↑', value: 'priceAsc' },
  { label: '价格 ↓', value: 'priceDesc' },
  { label: '销量', value: 'salesDesc' },
  { label: '最新', value: 'newest' },
]
const sortBy = ref('default')

// 触底哨兵：进入视口 = 用户快滚到底了 → 加载下一页
const sentinel = ref<HTMLElement>()
let observer: IntersectionObserver | undefined

/** 清空结果、回到第一页（换关键词或换排序时调用） */
function reset() {
  list.value = []
  pageNum.value = 1
  total.value = 0
  finished.value = false
  errorMsg.value = ''
  imgFail.value = new Set()
}

/** 追加一页结果 */
async function loadPage() {
  if (loading.value || finished.value || !keyword.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const page = await searchProducts({
      keyword: keyword.value,
      sortBy: sortBy.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    const rows = page.list ?? []
    // ⭐ 搜索结果是「顺序翻页」，按后端排序直接追加，绝不洗牌。
    //   （推荐流可以随机，但搜索结果的顺序必须可预期，否则用户会以为搜错了）
    list.value = [...list.value, ...rows]
    total.value = page.total ?? 0
    pageNum.value += 1
    // 本页不满 或 已加载数达到总数 → 没有下一页了
    finished.value = rows.length < PAGE_SIZE || list.value.length >= total.value
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '搜索失败'
  } finally {
    loading.value = false
    maybeLoadMore()          // 关键补充：加载完复查哨兵，防止 observer 不再触发导致卡住
  }
}

/** 主动检查哨兵是否仍在「视口 + 300px 预加载带」内（等价于手动复算相交状态） */
function maybeLoadMore() {
  const el = sentinel.value
  if (!el) return
  if (el.getBoundingClientRect().top < window.innerHeight + 300) {
    loadPage()
  }
}

/** 点搜索按钮 / 按回车：把关键词写进 URL，剩下的交给 watch */
function submit() {
  const kw = input.value.trim()
  if (!kw) return
  if (kw === keyword.value) {
    // 关键词没变 → URL 不会变 → watch 不会触发，这里手动重搜一次
    reset()
    loadPage()
    return
  }
  router.push({ path: '/search', query: { ...route.query, keyword: kw } })
}

/** 换排序：写进 URL，watch 会带着新排序重搜 */
function changeSort(v: string) {
  if (sortBy.value === v) return
  router.push({ path: '/search', query: { ...route.query, sortBy: v } })
}

/**
 * ⭐ 核心：监听 URL 上的 keyword / sortBy。
 * immediate:true 让它同时承担"首次进入就搜索"的职责——
 * 不用再写一个 onMounted 去拉数据，一套逻辑管住"进入"和"再搜"两种情况。
 */
watch(
  () => [route.query.keyword, route.query.sortBy] as const,
  ([kw, sb]) => {
    keyword.value = String(kw ?? '').trim()
    // URL 上的 sortBy 只认白名单里的值，防止手改地址栏塞进非法参数
    sortBy.value =
      typeof sb === 'string' && SORTS.some((s) => s.value === sb) ? sb : 'default'
    input.value = keyword.value      // URL → 输入框回显
    reset()
    loadPage()
  },
  { immediate: true },
)

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) maybeLoadMore()
    },
    { rootMargin: '300px' },         // 提前 300px 预加载，滚动更顺滑
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <div class="page">
    <!-- ===== 顶部：返回 + 搜索栏 ===== -->
    <header class="head">
      <button class="back" @click="router.push('/')">← 首页</button>

      <div class="box">
        <input
          v-model="input"
          class="s-input"
          placeholder="输入商品关键词，回车搜索"
          @keyup.enter="submit"
        />
        <button class="s-btn" @click="submit">搜索</button>
      </div>
    </header>

    <!-- ===== 结果概要 + 排序 ===== -->
    <section class="panel">
      <div class="summary">
        <p class="kw-line">
          <template v-if="keyword">
            搜索「<b>{{ keyword }}</b>」
            <span class="count">共 {{ total }} 件商品</span>
          </template>
          <template v-else>在上方输入关键词开始搜索</template>
        </p>

        <div v-if="keyword" class="sorts">
          <span
            v-for="s in SORTS"
            :key="s.value"
            class="sort"
            :class="{ active: sortBy === s.value }"
            @click="changeSort(s.value)"
          >
            {{ s.label }}
          </span>
        </div>
      </div>

      <!-- ===== 结果网格：与「为你推荐」同款卡片样式 ===== -->
      <div v-if="list.length" class="grid">
        <div
          v-for="item in list"
          :key="item.id"
          class="card"
          @click="router.push(`/product/${item.id}`)"
        >
          <div class="img-wrap">
            <img
              v-if="item.mainImage && !imgFail.has(item.id)"
              :src="item.mainImage"
              @error="imgFail.add(item.id)"
            />
            <div v-else class="ph">{{ item.productName }}</div>
            <span class="badge">热卖</span>
            <span v-if="item.subTitle" class="ribbon">{{ item.subTitle }}</span>
          </div>

          <p class="name">{{ item.productName }}</p>

          <div class="tag-row">
            <span class="tag">已售 {{ item.sales }}</span>
            <span class="tag">店长推荐</span>
          </div>

          <div class="price-row">
            <span class="price"><i>¥</i>{{ Number(item.minPrice).toFixed(2) }}</span>
          </div>
        </div>
      </div>

      <!-- ===== 四种空态：只显示一种 ===== -->
      <p v-else-if="!keyword" class="hint">输入关键词，回车即可搜索</p>
      <p v-else-if="loading" class="hint">搜索中…</p>
      <p v-else-if="errorMsg" class="hint err">{{ errorMsg }}</p>
      <p v-else class="hint">没有找到与「{{ keyword }}」相关的商品，换个关键词试试</p>
    </section>

    <!-- ===== 底部：触底哨兵 + 状态提示 ===== -->
    <div ref="sentinel" class="sentinel">
      <span v-if="finished && list.length">— 已经到底啦 —</span>
      <span v-else-if="loading">加载中...</span>
      <span v-else-if="list.length">继续下滑查看更多 ↓</span>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 0 30px 20px;        /* 与首页保持一致的外边距 */
  min-height: 100vh;
  box-sizing: border-box;
}

/* ===== 顶部搜索栏 ===== */
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
}
.back {
  flex-shrink: 0;
  padding: 9px 16px;
  font-size: 14px;
  color: #333;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  cursor: pointer;
}
.back:hover {
  border-color: #e1251b;
  color: #e1251b;
}
.box {
  flex: 1;
  display: flex;
  gap: 10px;
  min-width: 0;
}
.s-input {
  flex: 1;
  min-width: 0;
  height: 42px;
  padding: 0 14px;
  font-size: 15px;
  border: 2px solid #e1251b;
  border-radius: 8px;
  outline: none;
  box-sizing: border-box;
}
.s-btn {
  flex-shrink: 0;
  padding: 0 26px;
  height: 42px;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  background: #e1251b;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}
.s-btn:hover {
  background: #c91f17;
}

/* ===== 结果面板 ===== */
.panel {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
}
.summary {
  margin-bottom: 12px;
}
.kw-line {
  margin: 0;
  font-size: 15px;
  color: #333;
}
.kw-line b {
  color: #e1251b;
}
.count {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

/* 排序：一排小胶囊 */
.sorts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.sort {
  padding: 5px 14px;
  font-size: 13px;
  color: #333;
  background: #f7f7f7;
  border: 1px solid transparent;
  border-radius: 14px;
  cursor: pointer;
  user-select: none;
}
.sort:hover {
  color: #e1251b;
}
.sort.active {
  color: #e1251b;
  background: #fff5f4;
  border-color: #e1251b;
  font-weight: 700;
}

/* ===== 6 列网格（与 GuessYouLike 同款） ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
}
.card {
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #f0f0f0;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}
.img-wrap {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #f5f5f5;         /* 图未加载出来时是灰块，不是刺眼的白洞 */
}
.img-wrap img,
.ph {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #999;
  text-align: center;
  padding: 10px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #f6f6f6, #ededed);
}
.badge {
  position: absolute;
  top: 0;
  left: 0;
  background: linear-gradient(135deg, #ff4d4f, #e1251b);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 0 0 10px 0;
}
.ribbon {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(225, 37, 27, 0.85);
  color: #fff;
  font-size: 12px;
  padding: 4px 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.name {
  margin: 10px 12px 0;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}
.tag-row {
  display: flex;
  gap: 6px;
  margin: 8px 12px 0;
}
.tag {
  font-size: 11px;
  color: #666;
  background: #f5f5f5;
  border-radius: 3px;
  padding: 2px 6px;
}
.price-row {
  margin: 8px 12px 12px;
}
.price {
  color: #e1251b;
  font-weight: 700;
  font-size: 22px;
}
.price i {
  font-style: normal;
  font-size: 14px;
}

/* ===== 空态 / 触底 ===== */
.hint {
  margin: 0;
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  color: #999;
}
.hint.err {
  color: #c0392b;
}
.sentinel {
  text-align: center;
  color: #999;
  font-size: 12px;
  padding: 16px 0 4px;
}
</style>
