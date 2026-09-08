package org.jsut.inference;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

// 将视频帧转换为 YOLO 模型输入格式
@Slf4j
@Component
public class ImagePreprocessor {

    @Value("${onnx.input-size:640}")
    private int inputSize;

    public PreprocessResult preprocess(Mat frame) {
        int origW = frame.cols();
        int origH = frame.rows();

        Mat resized = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.resize(frame, resized, new Size(inputSize, inputSize));
        resized = convertBGR2RGB(resized);
        float[] tensorData = matToNCHW(resized);
        resized.release();

        return new PreprocessResult(tensorData, origW, origH, inputSize, inputSize);
    }

    // OpenCV 默认 BGR，YOLO 训练用 RGB
    private Mat convertBGR2RGB(Mat mat) {
        Mat rgb = new Mat();
        cvtColor(mat, rgb, COLOR_BGR2RGB);
        mat.release();
        return rgb;
    }

    // Mat(HWC) 转 NCHW float 数组，同时除以 255 归一化
    private float[] matToNCHW(Mat mat) {
        int h = mat.rows();
        int w = mat.cols();
        int c = mat.channels();
        float[] tensor = new float[1 * c * h * w];

        long totalBytes = (long) h * w * c;
        byte[] pixelData = new byte[(int) totalBytes];
        mat.data().get(pixelData);

        int planeSize = h * w;
        for (int i = 0; i < planeSize; i++) {
            int idx = i * c;
            for (int ch = 0; ch < c; ch++) {
                int pixel = pixelData[idx + ch] & 0xFF;
                tensor[ch * planeSize + i] = pixel / 255.0f;
            }
        }

        return tensor;
    }

    public int getInputSize() {
        return inputSize;
    }

    public record PreprocessResult(float[] tensorData, int origWidth, int origHeight, int resizedWidth, int resizedHeight) {}
}
