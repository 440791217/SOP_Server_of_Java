package org.jsut.inference;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 加载并管理 ONNX 模型会话
@Slf4j
@Component
public class OnnxModelManager {

    @Value("${onnx.model-path}")
    private String modelPath;

    @Value("${onnx.gpu.enabled:false}")
    private boolean gpuEnabled;

    @Value("${onnx.gpu.device-id:0}")
    private int gpuDeviceId;

    private OrtEnvironment env;
    private OrtSession session;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            SessionOptions opts = new SessionOptions();
            opts.setOptimizationLevel(OptLevel.BASIC_OPT);

            if (gpuEnabled) {
                opts.addCUDA(gpuDeviceId);
                opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR); // 只保留错误级别日志，抑制 CUDA EP 的性能警告
                log.info("[ONNX] GPU (CUDA) 已启用, device ID: {}", gpuDeviceId);
            } else {
                log.info("[ONNX] 运行在 CPU 模式");
            }

            session = env.createSession(modelPath, opts);
            log.info("[ONNX] 模型加载完成: {}", modelPath);
            log.info("[ONNX] 输入: {}", session.getInputInfo());
            log.info("[ONNX] 输出: {}", session.getOutputInfo());
        } catch (OrtException e) {
            log.error("[ONNX] 模型加载失败", e);
            throw new RuntimeException("ONNX 模型加载失败: " + e.getMessage(), e);
        }
    }

    public OrtSession getSession() {
        return session;
    }

    public OrtEnvironment getEnv() {
        return env;
    }

    public String getInputName() {
        return session.getInputNames().iterator().next();
    }

    public String getOutputName() {
        return session.getOutputNames().iterator().next();
    }

    @PreDestroy
    public void destroy() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
            log.info("[ONNX] 资源已释放");
        } catch (OrtException e) {
            log.error("[ONNX] 资源释放异常", e);
        }
    }
}
