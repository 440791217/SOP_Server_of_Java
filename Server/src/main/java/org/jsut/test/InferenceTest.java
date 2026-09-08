package org.jsut.test;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

// 只验证 ONNX 推理，跳过 RTSP
public class InferenceTest {

    private static final String MODEL_PATH = "models/yolo12s.onnx";

    public static void main(String[] args) {
        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            SessionOptions opts = new SessionOptions();
            opts.setOptimizationLevel(OptLevel.BASIC_OPT);
            opts.addCUDA(0);

            OrtSession session = env.createSession(MODEL_PATH, opts);
            String inputName = session.getInputNames().iterator().next();
            String outputName = session.getOutputNames().iterator().next();

            System.out.println("[ONNX] GPU loaded: " + MODEL_PATH);
            System.out.println("[ONNX] input: " + inputName + " [1,3,640,640]");
            System.out.println("[ONNX] output: " + outputName + " [1,84,8400]");

            // 造一张全 0.5 灰度图
            float[] data = new float[1 * 3 * 640 * 640];
            for (int i = 0; i < data.length; i++) {
                data[i] = 0.5f;
            }

            long[] shape = {1, 3, 640, 640};
            OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);

            long start = System.currentTimeMillis();
            try (OrtSession.Result output = session.run(inputs)) {
                long time = System.currentTimeMillis() - start;
                float[][][] result = (float[][][]) output.get(outputName).get().getValue();
                System.out.println("[ONNX] inference done in " + time + "ms");
                System.out.println("[ONNX] output shape: [" + result.length + "," + result[0].length + "," + result[0][0].length + "]");

                int maxIdx = 0;
                float maxConf = 0;
                for (int i = 0; i < result[0][0].length; i++) {
                    for (int c = 4; c < result[0].length; c++) {
                        if (result[0][c][i] > maxConf) {
                            maxConf = result[0][c][i];
                            maxIdx = i;
                        }
                    }
                }
                System.out.println("[ONNX] max confidence: " + maxConf + " at anchor " + maxIdx);
                System.out.println("[ONNX] test passed");
            }

            session.close();
            env.close();
        } catch (Exception e) {
            System.out.println("[ONNX] test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
