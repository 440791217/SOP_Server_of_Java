package org.jsut.inference;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 解析 YOLO 输出，过滤低置信度框，NMS 去重
@Slf4j
@Component
public class DetectionPostprocessor {

    @Value("${onnx.conf-threshold:0.25}")
    private float confThreshold;

    @Value("${onnx.iou-threshold:0.45}")
    private float iouThreshold;

    // YOLO 输出 [1, 84, 8400]: 84=4坐标+80类别, 8400=候选框
    public List<Detection> postprocess(float[] rawOutput, long[] shape, int origW, int origH, int inputW, int inputH) {
        int dims = (int) shape[1];
        int numAnchors = (int) shape[2];

        int boxCoords = 4;
        int numClasses = dims - boxCoords;

        List<Detection> detections = new ArrayList<>();

        for (int i = 0; i < numAnchors; i++) {
            float cx = rawOutput[0 * numAnchors + i];
            float cy = rawOutput[1 * numAnchors + i];
            float w = rawOutput[2 * numAnchors + i];
            float h = rawOutput[3 * numAnchors + i];

            int maxClassId = 0;
            float maxConf = 0;
            for (int c = 0; c < numClasses; c++) {
                float conf = rawOutput[(boxCoords + c) * numAnchors + i];
                if (conf > maxConf) {
                    maxConf = conf;
                    maxClassId = c;
                }
            }

            if (maxConf < confThreshold) continue;

            float x1 = (cx - w / 2) / inputW * origW;
            float y1 = (cy - h / 2) / inputH * origH;
            float x2 = (cx + w / 2) / inputW * origW;
            float y2 = (cy + h / 2) / inputH * origH;

            detections.add(new Detection(maxClassId, maxConf, x1, y1, x2, y2));
        }

        return nms(detections);
    }

    // 非极大值抑制：重叠度高的同类别框只保留概率最大的
    private List<Detection> nms(List<Detection> detections) {
        detections.sort(Comparator.comparingDouble(Detection::confidence).reversed());
        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            result.add(detections.get(i));
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                if (detections.get(i).clsId() == detections.get(j).clsId()
                        && iou(detections.get(i), detections.get(j)) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }

        return result;
    }

    private float iou(Detection a, Detection b) {
        float interX1 = Math.max(a.x1(), b.x1());
        float interY1 = Math.max(a.y1(), b.y1());
        float interX2 = Math.min(a.x2(), b.x2());
        float interY2 = Math.min(a.y2(), b.y2());

        float interW = Math.max(0, interX2 - interX1);
        float interH = Math.max(0, interY2 - interY1);
        float interArea = interW * interH;

        float areaA = (a.x2() - a.x1()) * (a.y2() - a.y1());
        float areaB = (b.x2() - b.x1()) * (b.y2() - b.y1());

        float union = areaA + areaB - interArea;
        return union > 0 ? interArea / union : 0;
    }

    public record Detection(int clsId, float confidence, float x1, float y1, float x2, float y2) {}
}
