package org.jsut.task.camera;

import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局最新帧缓存池（支持多路相机）
 * 供 RTSP 采集线程写入，供检测、WebSocket、前端页面随时按需获取
 */
@Component
public class GlobalFrameCache {

    // 内部结构体：保存当前相机的最新帧和时间戳
    private static class FrameHolder {
        private Mat currentMat = null;
        private long timestamp = 0;

        // 更新最新帧（拉流线程调用）
        public synchronized void update(Mat newMat) {
            // 释放掉被顶替的旧帧的堆外 C++ 内存，防止 OOM
            if (this.currentMat != null && !this.currentMat.empty()) {
                this.currentMat.release();
            }
            this.currentMat = newMat;
            this.timestamp = System.currentTimeMillis();
        }

        // 获取最新帧的深拷贝（外部检测/推流调用）
        public synchronized Mat getLatestClone() {
            if (this.currentMat == null || this.currentMat.empty()) {
                return null;
            }
            // 极其关键：必须 clone 一份出去！
            // 这样外部不管处理多久，拉流线程刷新覆盖都不会影响外部正在用的这块内存
            return this.currentMat.clone();
        }

        // 清理释放
        public synchronized void clear() {
            if (this.currentMat != null && !this.currentMat.empty()) {
                this.currentMat.release();
                this.currentMat = null;
            }
        }
    }

    // 每路相机对应一个 FrameHolder
    private final Map<String, FrameHolder> cacheMap = new ConcurrentHashMap<>();

    /**
     * RTSP 采集线程调用：更新某相机的最新帧
     */
    public void putFrame(String cameraId, Mat newMat) {
        cacheMap.computeIfAbsent(cameraId, k -> new FrameHolder()).update(newMat);
    }

    /**
     * 外部业务（检测/显示）随时调用：拿取指定相机的当前最新帧
     * ⚠️ 注意：调用方用完后必须手动调用 mat.release()！
     */
    public Mat getLatestFrame(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getLatestClone() : null;
    }

    /**
     * 停止相机时清理缓存
     */
    public void remove(String cameraId) {
        FrameHolder holder = cacheMap.remove(cameraId);
        if (holder != null) {
            holder.clear();
        }
    }
}