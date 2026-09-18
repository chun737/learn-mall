<script setup lang="ts">
/**
 * 跑马灯推荐位：搜索框下方匀速流动的商品图片带（10 张循环滚动）。
 * 自己取数、自己动画——App.vue 里只需要 <GoodsMarquee /> 一行。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchProducts, type ProductItem } from '@/api/product'

// 商品列表：跑马灯唯一的"数据源"
const list = ref<ProductItem[]>([])

// 图片加载失败的序号集合 → 失败的图渲染成占位块（@error 时记录）
const imgFail = ref(new Set<number>())

// 路由器：点击商品跳详情页
const router = useRouter()

// 把 10 个商品复制成 20 个 → 轨道平移一半时画面恰好和起点重合 → 无缝循环
// computed：list 变化时自动重算，这是"声明式"的复制
const displayList = computed(() => [...list.value, ...list.value])

// 点击卡片 → 进入该商品详情页
function goDetail(item: ProductItem) {
  router.push(`/product/${item.id}`)
}

onMounted(async () => {
  // 取销量前 10：sortBy=salesDesc 直接映射后端排序参数
  try {
    const page = await searchProducts({ sortBy: 'salesDesc', pageSize: 10 })
    list.value = page.list ?? []
  } catch {
    // 跑马灯是装饰位，加载失败静默降级为空——不弹错误打扰主流程
    list.value = []
  }
})
</script>

<template>
  <!-- 外层窗口：只露一行高度，超出部分被 overflow 裁掉 -->
  <div class="marquee">
    <!-- 轨道：所有卡片排成一条，整体做向左平移动画 -->
    <div class="track">
      <div
        v-for="(item, index) in displayList"
        :key="index"
        class="card"
        @click="goDetail(item)"
      >
        <!-- 真图：加载失败时记录到 imgFail，触发 Vue 切换成占位块 -->
        <img
          v-if="item.mainImage && !imgFail.has(index % 10)"
          :src="item.mainImage"
          @error="imgFail.add(index % 10)"
        />
        <!-- 占位块：无图商品不至于开天窗 -->
        <div v-else class="ph">{{ item.productName }}</div>
        <p class="name">{{ item.productName }}</p>
        <p class="price">¥{{ Number(item.minPrice).toFixed(2) }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 外层窗口：横向滚动的"取景框" */
.marquee {
  overflow: hidden;          /* 超出窗口的内容裁掉——没有它页面会被撑出横向滚动条 */
  margin: 0 0 12px;          /* 撑满右列宽度 + 底部间距 */
  border-radius: 20px;
  background: #fff;
}
/* 轨道：所有卡片排成一条线，整体匀速左移 */
.track {
  display: flex;             /* 卡片横排 */
  gap: 5px;
  width: max-content;        /* 轨道宽度 = 内容总宽（flex 默认会被压缩，必须放开） */
  padding: 0px;
  animation: scroll 25s linear infinite;  /* 动画名 25 秒一圈 匀速 无限循环 */
  animation-delay: -12.5s;   /* 从半程开始播：一进页面就是滚到一半的状态，立刻有内容看 */
}
/* 动画定义：平移轨道自身宽度的一半（即第一组 10 张的宽度） */
@keyframes scroll {
  from { transform: translateX(0); }
  to   { transform: translateX(-50%); }
}
/* 悬停暂停：用户看得清、点得到 */
.marquee:hover .track {
  animation-play-state: paused;
}
/* 单张卡片 */
.card {
  flex-shrink: 0;            /* 禁止被 flex 压缩变形——每张卡保持固定宽 */
  width: 140px;
  cursor: pointer;
}
.card img,
.ph {
  width: 140px;
  height: 140px;
  border-radius: 8px;
  object-fit: cover;         /* 图比例不齐也裁成正方形 */
  display: block;
}
/* 占位块：居中显示商品名 */
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #999;
  background: linear-gradient(135deg, #f6f6f6, #ededed);
  padding: 8px;
  text-align: center;
  box-sizing: border-box;
}
.name {
  margin: 6px 0 0;
  font-size: 12px;
  color: #333;
  white-space: nowrap;       /* 名字超宽不换行 */
  overflow: hidden;
  text-overflow: ellipsis;   /* 超出部分显示省略号 */
}
.price {
  margin: 2px 0 0;
  font-size: 13px;
  color: #e1251b;
  font-weight: 700;
}
</style>
