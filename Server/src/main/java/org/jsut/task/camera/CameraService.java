package org.jsut.task.camera;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
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
        // 提交纯拉流死循环任务
        threadPoolManager.submitGrabberTask(cameraId, () -> {
            log.info("[采集] 相机 [{}] 开始采集 RTSP: {}", cameraId, rtspUrl);

            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl)) {
                grabber.setOption("rtsp_transport", "tcp");
                grabber.setOption("fflags", "nobuffer");
                grabber.setOption("max_delay", "0");
                grabber.start();

                OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
                Frame frame;

                while (!Thread.currentThread().isInterrupted()) {
                    frame = grabber.grabImage();
                    if (frame == null) {
                        continue;
                    }

                    Mat mat = converter.convert(frame);
                    if (mat != null && !mat.empty()) {
                        // 放入全局缓存中（注意 clone 避免底层 grabber 内部复用覆盖）
                        frameCache.putFrame(cameraId, mat.clone());
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
}
