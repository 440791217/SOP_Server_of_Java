package org.jsut.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.jsut.common.ApiResponse;
import org.jsut.inference.ImagePreprocessor;
import org.jsut.inference.DetectionPostprocessor;
import org.jsut.inference.OnnxInferenceService;
import org.jsut.task.camera.CameraService;
import org.jsut.task.camera.GlobalFrameCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/camera")
public class CameraController {

    @Autowired
    private CameraService cameraService;

    @Autowired
    private OnnxInferenceService inferenceService;

    @Autowired
    private GlobalFrameCache frameCache;

    @Autowired
    private ImagePreprocessor preprocessor;

    @Autowired
    private DetectionPostprocessor postprocessor;

    // 推理异步线程池
    private final ExecutorService inferExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> inferringCameras = ConcurrentHashMap.newKeySet();

    // 队列容量1，新请求来了如果队列满了直接丢弃，避免堆积
    private final ExecutorService detectExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    /**
     * 直接返回缓存的 JPEG 帧，用 HttpServletResponse 写字节流绕过 GlobalResponseAdvice 包装
     */
    @GetMapping("/frame/{cameraId}")
    public void getFrame(@PathVariable String cameraId, HttpServletResponse response) {
        byte[] jpeg = frameCache.getJpegBytes(cameraId);
        if (jpeg == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setContentLength(jpeg.length);
        try (OutputStream os = response.getOutputStream()) {
            os.write(jpeg);
            os.flush();
        } catch (Exception e) {
            log.error("[API] 写入JPEG帧失败", e);
        }
    }

    /**
     * 接收前端上传的图片，直接推理返回检测结果（不依赖RTSP拉流）
     * 前端推图→服务器推理→返回坐标。GPU单线程串行，避免并发冲突。
     */
    @PostMapping("/detect")
    public ApiResponse<Map<String, Object>> detectFromImage(@RequestParam("image") MultipartFile imageFile) {
        try {
            byte[] jpegBytes = imageFile.getBytes();
            log.info("[检测] 收到图片，{} 字节", jpegBytes.length);

            Future<ApiResponse<Map<String, Object>>> future = detectExecutor.submit(() -> {
                java.io.File tmpFile = null;
                Mat mat = null;
                try {
                    // 写临时文件 + imread 解码
                    tmpFile = java.io.File.createTempFile("detect_", ".jpg");
                    java.nio.file.Files.write(tmpFile.toPath(), jpegBytes);

                    mat = opencv_imgcodecs.imread(tmpFile.getAbsolutePath(), opencv_imgcodecs.IMREAD_COLOR);
                    if (mat == null || mat.empty()) {
                        log.error("[检测] 图片解码失败");
                        return ApiResponse.error(500, "图片解码失败");
                    }

                    // 预处理
                    ImagePreprocessor.PreprocessResult prep = preprocessor.preprocess(mat);

                    long[] inputShape = {1, 3, prep.resizedHeight(), prep.resizedWidth()};
                    ai.onnxruntime.OnnxTensor inputTensor = ai.onnxruntime.OnnxTensor.createTensor(
                            inferenceService.getModelManager().getEnv(),
                            java.nio.FloatBuffer.wrap(prep.tensorData()), inputShape);

                    String inputName = inferenceService.getModelManager().getInputName();
                    Map<String, ai.onnxruntime.OnnxTensor> inputs = new HashMap<>();
                    inputs.put(inputName, inputTensor);

                    long inferStart = System.currentTimeMillis();
                    try (ai.onnxruntime.OrtSession.Result output = inferenceService.getModelManager().getSession().run(inputs)) {
                        String outputName = inferenceService.getModelManager().getOutputName();
                        Object outputValue = output.get(outputName).get().getValue();
                        float[] rawOutput;
                        if (outputValue instanceof float[] oneD) {
                            rawOutput = oneD;
                        } else {
                            float[][][] arr3d = (float[][][]) outputValue;
                            int total = arr3d[0].length * arr3d[0][0].length;
                            rawOutput = new float[total];
                            for (int i = 0; i < arr3d[0].length; i++) {
                                System.arraycopy(arr3d[0][i], 0, rawOutput, i * arr3d[0][i].length, arr3d[0][i].length);
                            }
                        }
                        long[] outputShape = {1, 84, 8400};

                        List<DetectionPostprocessor.Detection> detections =
                                postprocessor.postprocess(rawOutput, outputShape,
                                        prep.origWidth(), prep.origHeight(),
                                        prep.resizedWidth(), prep.resizedHeight());

                        long inferTime = System.currentTimeMillis() - inferStart;
                        log.info("[检测] 完成，{} 个目标，耗时 {}ms", detections.size(), inferTime);

                        Map<String, Object> data = new HashMap<>();
                        data.put("detections", detections);
                        data.put("inferTimeMs", inferTime);
                        data.put("origWidth", prep.origWidth());
                        data.put("origHeight", prep.origHeight());
                        return ApiResponse.success(data);
                    }
                } catch (Exception e) {
                    log.error("[检测] 推理异常", e);
                    return ApiResponse.error(500, "检测失败: " + e.getMessage());
                } finally {
                    if (mat != null && !mat.empty()) mat.release();
                    if (tmpFile != null && tmpFile.exists()) tmpFile.delete();
                }
            });

            return future.get();

        } catch (Exception e) {
            log.error("[检测] 请求处理失败", e);
            return ApiResponse.error(500, "检测失败: " + e.getMessage());
        }
    }

    /**
     * 返回缓存的检测结果，同时异步触发下一轮推理
     */
    @GetMapping("/detections/{cameraId}")
    public ApiResponse<Map<String, Object>> getDetections(@PathVariable String cameraId,
                                              @RequestParam(defaultValue = "false") boolean infer) {
        // 异步触发推理
        if (infer && !inferringCameras.contains(cameraId)) {
            inferringCameras.add(cameraId);
            inferExecutor.submit(() -> {
                try {
                    OnnxInferenceService.DetectionResult result = inferenceService.detect(cameraId);
                    frameCache.putDetections(cameraId, result.detections(), result.inferTimeMs());
                } catch (Exception e) {
                    log.error("[API] 异步推理异常", e);
                } finally {
                    inferringCameras.remove(cameraId);
                }
            });
        }

        Map<String, Object> data = new HashMap<>();
        data.put("cameraId", cameraId);
        data.put("detections", frameCache.getDetections(cameraId) != null ? frameCache.getDetections(cameraId) : List.of());
        data.put("inferTimeMs", frameCache.getInferTimeMs(cameraId));
        data.put("origWidth", frameCache.getOrigWidth(cameraId));
        data.put("origHeight", frameCache.getOrigHeight(cameraId));
        return ApiResponse.success(data);
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody Map<String, String> request) {
        String cameraId = request.get("cameraId");
        String url = request.get("url");
        cameraService.startCamera(cameraId, url);
        return Map.of("code", 200, "message", "camera started: " + cameraId);
    }

    @PostMapping("/stop")
    public Map<String, Object> stop(@RequestBody Map<String, String> request) {
        String cameraId = request.get("cameraId");
        cameraService.stopCamera(cameraId);
        return Map.of("code", 200, "message", "camera stopped: " + cameraId);
    }

    @GetMapping("/test-detect")
    public Map<String, Object> testDetect(@RequestParam String url) {
        String cameraId = "test-detect";
        cameraService.startCamera(cameraId, url);
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        OnnxInferenceService.DetectionResult result = inferenceService.detect(cameraId);
        cameraService.stopCamera(cameraId);
        return Map.of("code", 200, "cameraId", result.cameraId(),
                "detections", result.detections().size(),
                "inferTimeMs", result.inferTimeMs(),
                "raw", result.detections());
    }
}
