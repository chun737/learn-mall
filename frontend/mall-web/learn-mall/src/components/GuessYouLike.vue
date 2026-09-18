<script setup lang="ts">
/**
 * 为你推荐（无限滚动版）：
 * 静态网格展示已加载的商品（只增不减），用户滚动接近底部时
 * 通过 IntersectionObserver 自动追加下一批新商品。
 * 商品池耗尽时自动向后端取下一页（pageNum+1），全库取完为止。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchProducts, type ProductItem } from '@/api/product'

// 路由器：点击商品跳详情页
const router = useRouter()

const PER_PAGE = 10                       // 每批追加 10 个（4 列 × 2.5 行）
const FETCH_SIZE = 50                     // 每次向后端请求的页大小

const items = ref<ProductItem[]>([])      // 已展示的商品（只增不减）
const pool = ref<ProductItem[]>([])       // 本地缓冲池：取回来还没展示的
const pageNum = ref(1)                    // 后端页码：缓冲池耗尽时取下一页
const exhausted = ref(false)              // 后端全库取完 → 停止加载
const loading = ref(false)
const imgFail = ref(new Set<number>())

// 触底哨兵：进入视口 = 用户快滚到底了 → 加载下一批
const sentinel = ref<HTMLElement>()
let observer: IntersectionObserver | undefined

/** Fisher-Yates 洗牌（首批打乱，制造"千人千面"的开屏） */
function shuffle<T>(arr: T[]): T[] {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/** 追加一批：优先从本地缓冲池切，池空了再向后端取下一页 */
async function appendBatch() {
  if (loading.value || exhausted.value) return
  loading.value = true
  try {
    if (pool.value.length < PER_PAGE) {
      // 缓冲池不足一批 → 向后端取新页
      const page = await searchProducts({ pageNum: pageNum.value, pageSize: FETCH_SIZE })
      const rows = page.list ?? []
      if (rows.length === 0) {
        exhausted.value = true            // 全库取完
        return
      }
      pageNum.value += 1
      pool.value = [...pool.value, ...shuffle(rows)]   // 新页洗牌后入池
    }
    items.value = [...items.value, ...pool.value.splice(0, PER_PAGE)]
    // splice：从池头切一批出来接到展示列表尾部——原有商品原地不动
  } catch {
    // 静默失败：已展示的商品不受影响
  } finally {
    loading.value = false
    maybeAppend()   // ★ 关键补充：加载完复查哨兵——若还在加载范围内就继续追加，
                    //   解决"初始批次太少、哨兵一直停留在视野内导致 observer 不再触发"的死锁
  }
}

/** 主动检查哨兵是否仍在"视口 + 300px 预加载带"内（等价于手动复算相交状态） */
function maybeAppend() {
  const el = sentinel.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  if (rect.top < window.innerHeight + 300) {
    appendBatch()
  }
}

onMounted(() => {
  appendBatch()                            // 首批（会带洗牌）

  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        maybeAppend()
      }
    },
    { rootMargin: '300px' },               // 提前 300px 预加载，滚动更顺滑
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <section class="guess">
    <h3 class="guess-title">
      <span class="lock">🔒</span> 为你推荐
      <small class="sub">向下滚动发现更多好物</small>
    </h3>

    <!-- 已加载的商品：只增不减，网格 4 列（图片更大） -->
    <div class="grid">
      <div v-for="(item, index) in items" :key="item.id" class="card" @click="router.push(`/product/${item.id}`)">
        <div class="img-wrap">
          <img
            v-if="item.mainImage && !imgFail.has(item.id)"
            :src="item.mainImage"
            @error="imgFail.add(item.id)"
          />
          <div v-else class="ph">{{ item.productName }}</div>
          <span class="badge">热卖 TOP{{ index + 1 }}</span>
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

    <!-- 底部状态：哨兵 + 三种提示 -->
    <div ref="sentinel" class="sentinel">
      <span v-if="exhausted">— 已经到底啦 —</span>
      <span v-else-if="loading">加载中...</span>
      <span v-else>继续下滑查看更多 ↓</span>
    </div>
  </section>
</template>

<style scoped>
.guess {
  background: #fff;
  border-radius: 10px;
  margin: 0 0 12px;        /* 撑满右列宽度 */
  padding: 14px;
}
.guess-title {
  margin: 0 0 12px;
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.lock { font-size: 18px; }
.sub {
  font-size: 11px;
  color: #999;
  font-weight: 400;
}

/* 4 列网格：比 5 列每张卡宽约 25% → 图片更大 */
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
  transition: box-shadow 0.2s;
}
.card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}
.img-wrap {
  position: relative;
  aspect-ratio: 1 / 1;       /* 正方形大图：随列宽放大到约 180px */
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

.sentinel {
  text-align: center;
  color: #999;
  font-size: 12px;
  padding: 14px 0 4px;
}
</style>
