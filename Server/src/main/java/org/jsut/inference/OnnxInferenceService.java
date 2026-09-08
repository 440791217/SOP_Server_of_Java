package org.jsut.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.jsut.task.camera.GlobalFrameCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 从全局缓存取帧，执行 ONNX 推理，返回检测结果
@Slf4j
@Service
public class OnnxInferenceService {

    @Autowired
    private OnnxModelManager modelManager;

    @Autowired
    private ImagePreprocessor preprocessor;

    @Autowired
    private DetectionPostprocessor postprocessor;

    @Autowired
    private GlobalFrameCache frameCache;

    public DetectionResult detect(String cameraId) {
        Mat frame = frameCache.getLatestFrame(cameraId);
        if (frame == null || frame.empty()) {
            log.warn("[检测] 相机 [{}] 无可用帧", cameraId);
            return new DetectionResult(cameraId, System.currentTimeMillis(), List.of(), 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            ImagePreprocessor.PreprocessResult prep = preprocessor.preprocess(frame);

            long[] inputShape = {1, 3, prep.resizedHeight(), prep.resizedWidth()};
            OnnxTensor inputTensor = OnnxTensor.createTensor(modelManager.getEnv(), FloatBuffer.wrap(prep.tensorData()), inputShape);

            String inputName = modelManager.getInputName();
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, inputTensor);

            try (OrtSession.Result output = modelManager.getSession().run(inputs)) {
                String outputName = modelManager.getOutputName();
                float[] rawOutput = (float[]) output.get(outputName).get().getValue();
                long[] outputShape = {1, 84, 8400};

                List<DetectionPostprocessor.Detection> detections =
                        postprocessor.postprocess(rawOutput, outputShape,
                                prep.origWidth(), prep.origHeight(),
                                prep.resizedWidth(), prep.resizedHeight());

                long inferTime = System.currentTimeMillis() - startTime;
                log.info("[检测] 相机 [{}] 检测到 {} 个目标, 耗时 {}ms", cameraId, detections.size(), inferTime);

                return new DetectionResult(cameraId, System.currentTimeMillis(), detections, inferTime);
            }

        } catch (OrtException e) {
            log.error("[检测] 推理异常", e);
            throw new RuntimeException("推理失败: " + e.getMessage(), e);
        } finally {
            frame.release();
        }
    }

    public record DetectionResult(String cameraId, long timestamp,
                                  List<DetectionPostprocessor.Detection> detections,
                                  long inferTimeMs) {}
}
