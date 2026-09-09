package org.jsut.controller;

import lombok.extern.slf4j.Slf4j;
import org.jsut.inference.OnnxInferenceService;
import org.jsut.task.camera.CameraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/camera")
public class CameraController {

    @Autowired
    private CameraService cameraService;

    @Autowired
    private OnnxInferenceService inferenceService;

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
