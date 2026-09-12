package org.jsut.task.camera;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CameraService {

    @Autowired
    private RtspThreadPoolManager threadPoolManager;

    @Autowired
    private GlobalFrameCache frameCache;

    public void startCamera(String cameraId, String rtspUrl) {
        threadPoolManager.submitGrabberTask(cameraId, () -> {
            log.info("[采集] 相机 [{}] 开始采集 RTSP: {}", cameraId, rtspUrl);

            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl)) {
                grabber.setOption("rtsp_transport", "tcp");
                grabber.setOption("fflags", "nobuffer");
                grabber.setOption("max_delay", "0");
                grabber.start();
                log.info("[采集] 相机 [{}] grabber.start() 成功, {}x{}@{}fps", cameraId, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getFrameRate());

                OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
                Frame frame;

                // 丢弃前25帧，等解码器拿到关键帧，避免花屏
                int warmupFrames = 25;
                while (warmupFrames-- > 0) {
                    frame = grabber.grabImage();
                    if (frame != null) {
                        log.debug("[采集] 相机 [{}] 丢弃预热帧, 剩余 {}", cameraId, warmupFrames);
                    }
                }
                log.info("[采集] 相机 [{}] 预热完成，开始缓存帧", cameraId);

                // 连续取帧排空缓冲区，每40ms才处理缓存一帧（25fps）
                long lastCacheTime = 0;

                while (!Thread.currentThread().isInterrupted()) {
                    frame = grabber.grabImage();
                    if (frame == null) {
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastCacheTime < 40) {
                        continue;
                    }
                    lastCacheTime = now;

                    Mat mat = converter.convert(frame);
                    if (mat != null && !mat.empty()) {
                        Mat cloned = mat.clone();
                        int origW = cloned.cols();
                        int origH = cloned.rows();

                        Mat display = resizeForDisplay(cloned, 1280);
                        byte[] jpeg = encodeJpeg(display, 60);
                        int jW = display.cols();
                        int jH = display.rows();
                        display.release();

                        frameCache.putFrame(cameraId, cloned, jpeg, jW, jH, origW, origH);
                    }
                }
            } catch (Exception e) {
                log.error("[采集] 相机 [{}] 采集异常中断", cameraId, e);
            } finally {
                frameCache.remove(cameraId);
                log.info("[采集] 相机 [{}] 缓存已清理，采集退出", cameraId);
            }
        });
    }

    public void stopCamera(String cameraId) {
        threadPoolManager.stopGrabberTask(cameraId);
    }

    private Mat resizeForDisplay(Mat src, int maxWidth) {
        if (src.cols() <= maxWidth) {
            Mat dst = new Mat();
            src.copyTo(dst);
            return dst;
        }
        int newWidth = maxWidth;
        int newHeight = (int) Math.round((double) src.rows() * maxWidth / src.cols());
        Mat dst = new Mat();
        opencv_imgproc.resize(src, dst, new Size(newWidth, newHeight), 0, 0, opencv_imgproc.INTER_LINEAR);
        return dst;
    }

    private byte[] encodeJpeg(Mat mat, int quality) {
        BytePointer buf = new BytePointer();
        IntPointer params = new IntPointer(opencv_imgcodecs.IMWRITE_JPEG_QUALITY, quality);
        boolean ok = opencv_imgcodecs.imencode(".jpg", mat, buf, params);
        params.close();
        if (!ok) return null;
        int size = (int) buf.limit();
        byte[] bytes = new byte[size];
        buf.position(0);
        buf.get(bytes);
        buf.close();
        return bytes;
    }
}
