package org.jsut.task.camera;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RTSP 视频流采集线程池管理器
 * 职责：专职负责管理各路相机的底层拉流采集生命周期，提供按 CameraId 启停与优雅销毁机制
 */
@Slf4j
@Component
public class RtspThreadPoolManager {

    // 系统允许同时接入的最大相机路数（防止无限创建线程打爆内存）
    private static final int MAX_CAMERA_COUNT = 8;

    // 专职拉流线程池：核心线程为0（闲时无开销），最大限制为 MAX_CAMERA_COUNT，不使用无界队列排队
    private final ExecutorService grabberExecutor = new ThreadPoolExecutor(
            0,
            MAX_CAMERA_COUNT,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new NamedThreadFactory("rtsp-grabber"),
            new ThreadPoolExecutor.AbortPolicy() // 超过最大路数直接拒收并抛异常
    );

    // 记录正在运行的拉流任务 (Key: cameraId, Value: Future)
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    /**
     * 提交一路相机的拉流采集任务
     *
     * @param cameraId 相机唯一标识
     * @param task     拉流 Runnable 逻辑
     */
    public synchronized void submitGrabberTask(String cameraId, Runnable task) {
        // 如果该相机正在拉流，先安全停止旧任务
        stopGrabberTask(cameraId);

        try {
            Future<?> future = grabberExecutor.submit(task);
            runningTasks.put(cameraId, future);
            log.info("[采集管理] 相机 [{}] 拉流采集任务已提交", cameraId);
        } catch (RejectedExecutionException e) {
            log.error("[采集管理] 相机 [{}] 启动失败，已达到系统最大相机路数上限: {}", cameraId, MAX_CAMERA_COUNT);
            throw new RuntimeException("相机接入路数已达上限，无法启动新流", e);
        }
    }

    /**
     * 停止指定相机的拉流采集
     *
     * @param cameraId 相机唯一标识
     */
    public synchronized void stopGrabberTask(String cameraId) {
        Future<?> future = runningTasks.remove(cameraId);
        if (future != null && !future.isDone()) {
            // 下发线程中断信号（通知 while 循环退出）
            future.cancel(true);
            log.info("[采集管理] 相机 [{}] 拉流任务已发送停止信号", cameraId);
        }
    }

    /**
     * 判断某路相机是否正在拉流
     */
    public boolean isRunning(String cameraId) {
        Future<?> future = runningTasks.get(cameraId);
        return future != null && !future.isDone() && !future.isCancelled();
    }

    /**
     * 获取当前正在拉流的相机数量
     */
    public int getActiveCount() {
        return runningTasks.size();
    }

    /**
     * Spring 容器关闭时，优雅释放所有拉流线程
     */
    @PreDestroy
    public void shutdown() {
        log.info("[采集管理] 正在停止所有 RTSP 拉流任务并关闭线程池...");

        // 1. 给所有相机下发中断信号
        runningTasks.keySet().forEach(this::stopGrabberTask);

        // 2. 销毁线程池
        grabberExecutor.shutdownNow();

        try {
            if (!grabberExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("[采集管理] 拉流线程池未在规定时间内退出");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[采集管理] 拉流线程池已完全释放");
    }

    /**
     * 线程命名前缀工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + threadNumber.getAndIncrement());
            t.setDaemon(true); // 设为守护线程，防止阻塞 JVM 停机
            return t;
        }
    }
}