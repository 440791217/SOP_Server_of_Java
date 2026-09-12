package org.jsut.task.camera;

import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局最新帧缓存池
 * 供 RTSP 采集线程写入，供检测、WebSocket、前端页面随时按需获取
 */
@Component
public class GlobalFrameCache {

    // 内部结构体：保存当前相机的最新帧和时间戳
    private static class FrameHolder {
        private Mat currentMat = null;
        private long timestamp = 0;

        // 预压缩 JPEG 缓存
        private byte[] jpegBytes = null;
        private int jpegWidth = 0;
        private int jpegHeight = 0;
        private int origWidth = 0;
        private int origHeight = 0;

        // 最新检测结果缓存（推理线程写入，请求线程直接读取）
        private List<?> detections = null;
        private long inferTimeMs = 0;
        private long inferTimestamp = 0;

        // 更新最新帧（拉流线程调用）
        public synchronized void update(Mat newMat) {
            if (this.currentMat != null && !this.currentMat.empty()) {
                this.currentMat.release();
            }
            this.currentMat = newMat;
            this.timestamp = System.currentTimeMillis();
        }

        public synchronized void update(Mat newMat, byte[] jpeg, int jW, int jH, int oW, int oH) {
            if (this.currentMat != null && !this.currentMat.empty()) {
                this.currentMat.release();
            }
            this.currentMat = newMat;
            this.jpegBytes = jpeg;
            this.jpegWidth = jW;
            this.jpegHeight = jH;
            this.origWidth = oW;
            this.origHeight = oH;
            this.timestamp = System.currentTimeMillis();
        }

        public synchronized Mat getLatestClone() {
            if (this.currentMat == null || this.currentMat.empty()) {
                return null;
            }
            return this.currentMat.clone();
        }

        public synchronized byte[] getJpegBytes() { return jpegBytes; }
        public synchronized int getJpegWidth() { return jpegWidth; }
        public synchronized int getJpegHeight() { return jpegHeight; }
        public synchronized int getOrigWidth() { return origWidth; }
        public synchronized int getOrigHeight() { return origHeight; }

        @SuppressWarnings("unchecked")
        public synchronized <T> List<T> getDetections() { return (List<T>) detections; }
        public synchronized long getInferTimeMs() { return inferTimeMs; }

        public synchronized void updateDetections(List<?> det, long time) {
            this.detections = det;
            this.inferTimeMs = time;
            this.inferTimestamp = System.currentTimeMillis();
        }

        // 清理释放
        public synchronized void clear() {
            if (this.currentMat != null && !this.currentMat.empty()) {
                this.currentMat.release();
                this.currentMat = null;
            }
            this.jpegBytes = null;
            this.detections = null;
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
     * RTSP 采集线程调用：更新帧 + 预压缩 JPEG
     */
    public void putFrame(String cameraId, Mat newMat, byte[] jpeg, int jW, int jH, int oW, int oH) {
        cacheMap.computeIfAbsent(cameraId, k -> new FrameHolder()).update(newMat, jpeg, jW, jH, oW, oH);
    }

    /**
     * 推理线程调用：更新某相机的检测结果缓存
     */
    public void putDetections(String cameraId, List<?> detections, long inferTimeMs) {
        cacheMap.computeIfAbsent(cameraId, k -> new FrameHolder()).updateDetections(detections, inferTimeMs);
    }

    /**
     * 外部业务（检测/显示）随时调用：拿取指定相机的当前最新帧
     */
    public Mat getLatestFrame(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getLatestClone() : null;
    }

    /** 请求线程直接读预压缩 JPEG，零处理 */
    public byte[] getJpegBytes(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getJpegBytes() : null;
    }

    public int getJpegWidth(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getJpegWidth() : 0;
    }

    public int getJpegHeight(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getJpegHeight() : 0;
    }

    public int getOrigWidth(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getOrigWidth() : 0;
    }

    public int getOrigHeight(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getOrigHeight() : 0;
    }

    /** 请求线程直接读缓存的检测结果，零处理 */
    @SuppressWarnings("unchecked")
    public <T> List<T> getDetections(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getDetections() : null;
    }

    public long getInferTimeMs(String cameraId) {
        FrameHolder holder = cacheMap.get(cameraId);
        return holder != null ? holder.getInferTimeMs() : 0;
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