<script setup lang="ts">
/**
 * 今日热品秒杀 · 垂直流动框（首页左侧）
 *
 * 数据：GET /seckill?status=1 —— 只取「进行中」的秒杀活动（公开接口，无需登录）
 * 动效：把数据复制若干份，整体向上平移 50% 实现无缝循环
 *       —— 思路和横向跑马灯 GoodsMarquee 完全一样，只是把 X 换成 Y
 * 交互：鼠标悬停暂停（方便看清和点击）；点某条进入该商品详情页
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSeckillList, SECKILL_STATUS, type SeckillActivity } from '@/api/seckill'

const router = useRouter()

const list = ref<SeckillActivity[]>([])

/**
 * 复制若干份拼成滚动轨道。
 * 为什么要按数量决定份数：数据太少（比如只有 2 条）时，复制两份的总高度
 * 可能还不到容器高度，滚动过程中就会露出空白。少于 5 条时复制 4 份即可。
 * 注意份数必须是偶数，这样 translateY(-50%) 才刚好等于「一半份数」的高度。
 */
const displayList = computed(() => {
  const src = list.value
  if (!src.length) return []
  const times = src.length < 5 ? 4 : 2
  return Array.from({ length: times }, () => src).flat()
})

function goDetail(item: SeckillActivity) {
  router.push(`/product/${item.productId}`)
}

onMounted(async () => {
  try {
    const page = await getSeckillList({
      status: SECKILL_STATUS.ONGOING,
      pageNum: 1,
      pageSize: 10,
    })
    list.value = page.list ?? []
  } catch {
    list.value = []      // 装饰位：失败静默，不影响首页主功能
  }
})
</script>

<template>
  <div class="seckill">
    <div class="sk-head">
      <span class="sk-bolt">⚡</span>
      <span>今日热品秒杀</span>
    </div>

    <div class="sk-window">
      <p v-if="!list.length" class="sk-empty">暂无秒杀活动</p>

      <div v-else class="sk-track">
        <div
          v-for="(item, index) in displayList"
          :key="index"
          class="sk-item"
          @click="goDetail(item)"
        >
          <img v-if="item.mainImage" :src="item.mainImage" :alt="item.productName" />
          <div v-else class="sk-ph">无图</div>

          <div class="sk-info">
            <p class="sk-name">{{ item.productName }}</p>
            <p class="sk-price">
              <span class="sk-now">¥{{ item.seckillPrice }}</span>
              <del class="sk-old">¥{{ item.originalPrice }}</del>
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.seckill {
  width: 190px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;   /* 头部固定，滚动窗口吃掉剩余高度 */
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}

/* ===== 头部：主题红渐变 ===== */
.sk-head {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 9px 10px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #ff6057, #e1251b);
  flex-shrink: 0;
}
.sk-bolt {
  font-size: 14px;
}

/* ===== 取景框：超出部分裁掉 ===== */
.sk-window {
  flex: 1;
  min-height: 0;            /* 关键：允许收缩，否则会撑破父容器 */
  overflow: hidden;
  position: relative;
}
.sk-empty {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 12px;
  color: #aaa;
}

/* ===== 轨道：整体向上平移 50%（= 一半份数的高度）→ 无缝循环 ===== */
.sk-track {
  animation: skScroll 20s linear infinite;
}
@keyframes skScroll {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(-50%);
  }
}
/* 悬停暂停：用户看得清、点得到 */
.sk-window:hover .sk-track {
  animation-play-state: paused;
}

/* ===== 单条秒杀商品 ===== */
.sk-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f7f7f7;
}
.sk-item:hover .sk-name {
  color: #e1251b;
}
.sk-item img,
.sk-ph {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f5f5f5;
}
.sk-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #ccc;
}
.sk-info {
  min-width: 0;             /* 允许收缩，长名字才会出现省略号 */
}
.sk-name {
  margin: 0;
  font-size: 12px;
  color: #333;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sk-price {
  margin: 4px 0 0;
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.sk-now {
  font-size: 13px;
  font-weight: 700;
  color: #e1251b;
}
.sk-old {
  font-size: 11px;
  color: #bbb;
}
</style>
