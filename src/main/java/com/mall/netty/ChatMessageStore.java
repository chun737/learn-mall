package com.mall.netty;

import com.mall.entity.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 消息落库的缓冲池（修 P1「没落库」+ P2「落库阻塞 EventLoop」）。
 *
 * <h3>为什么要攒批</h3>
 * 一条消息一次 INSERT，数据库的网络往返 + binlog 开销全都要摊到每条消息上。
 * 客服系统一忙，这个开销很可观。攒够 {@link #BATCH_SIZE} 条或等满 {@link #FLUSH_MS}
 * 再写一次，能把这部分固定开销摊薄 —— 和项目里
 * {@code OrderItemMapper.insertBatch}（秒杀批量建单）是同一个思路。
 *
 * <h3>为什么不阻塞 Netty</h3>
 * {@link #save} 只做"入队"，不做 IO。真正的写库发生在独立的 writer 线程里，
 * 所以哪怕数据库卡了 3 秒，EventLoop（管着成百上千条连接的那个线程）也不会被拖住。
 *
 * <h3>消息会不会丢</h3>
 * 队列满（数据库长时间不可用）时降级为同步写并告警 —— 宁可慢一下，也不静默丢消息。
 * 进程被 Ctrl+C 时由 shutdown hook 把队列剩余的刷完。
 */
public final class ChatMessageStore {

    /** 攒够多少条写一次 */
    private static final int BATCH_SIZE = 200;
    /** 或者最多等这么久（毫秒），保证低频时消息不会一直压在队列里 */
    private static final long FLUSH_MS = 300;
    /** 队列上限；满了说明数据库出问题了 */
    private static final int QUEUE_CAPACITY = 20_000;

    private static final BlockingQueue<ChatMessage> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private static volatile ChatMessageSink sink;
    private static volatile boolean started = false;

    private ChatMessageStore() {}

    /**
     * 启动 writer 线程，并注入"写到哪"。
     *
     * @param messageSink 落库实现；传 null 表示降级为只打控制台（本地没数据库时用）
     */
    public static synchronized void init(ChatMessageSink messageSink) {
        sink = messageSink;
        if (started) {
            return;
        }
        started = true;

        Thread writer = new Thread(ChatMessageStore::loop, "chat-msg-writer");
        writer.setDaemon(true);
        writer.start();

        // 进程退出（含 Ctrl+C）时把队列里剩下的刷完，否则最后几百毫秒的消息会丢
        Runtime.getRuntime().addShutdownHook(new Thread(ChatMessageStore::flush, "chat-msg-flush"));
    }

    /** 业务线程调用：只入队，不做 IO，不阻塞 */
    public static void save(ChatMessage msg) {
        if (msg == null) {
            return;
        }
        if (!QUEUE.offer(msg)) {
            // 队列满 = 数据库长时间写不进去。消息不能丢，降级为同步写并告警。
            System.out.println("[Chat] ⚠ 落库队列已满（数据库是否卡住？），本条降级为同步写入");
            writeQuietly(List.of(msg));
        }
    }

    /** 把队列里剩余的消息立刻写完（关闭服务、测试时用） */
    public static void flush() {
        List<ChatMessage> rest = new ArrayList<>();
        QUEUE.drainTo(rest);
        writeQuietly(rest);
    }

    /** 当前积压条数，用于排查 */
    public static int pending() {
        return QUEUE.size();
    }

    // ======================= 内部 =======================

    private static void loop() {
        List<ChatMessage> batch = new ArrayList<>(BATCH_SIZE);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ChatMessage first = QUEUE.poll(FLUSH_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;                            // 等超时了还没消息，继续等
                }
                batch.add(first);
                QUEUE.drainTo(batch, BATCH_SIZE - 1);     // 把此刻攒着的一次性捞出来
                writeQuietly(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flush();
    }

    private static void writeQuietly(List<ChatMessage> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        ChatMessageSink s = sink;
        if (s == null) {
            printFallback(batch);
            return;
        }
        try {
            s.write(batch);
        } catch (Exception e) {
            // 落库失败不能把 writer 线程搞死，否则后面全都不写了
            System.out.println("[Chat] ✗ 落库失败，本批 " + batch.size()
                    + " 条改为控制台记录：" + e.getMessage());
            printFallback(batch);
        }
    }

    private static void printFallback(List<ChatMessage> batch) {
        for (ChatMessage m : batch) {
            System.out.println("[落库降级] " + m.getFromId() + " → " + m.getToId()
                    + " : " + m.getContent());
        }
    }
}
