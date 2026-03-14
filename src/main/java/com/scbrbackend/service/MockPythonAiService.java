package com.scbrbackend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 模拟的 AI 模型预测服务
 */
@Service
public class MockPythonAiService {

    private final Random random = new Random();

    /**
     * 模拟处理单帧视频图像并返回分析结果
     * @param base64Image 前端传来的 Base64 编码图片
     * @return 包含出勤人数、总分、具体行为坐标细节的 Map 结果
     */
    public Map<String, Object> predict(String base64Image) {
        // 模拟 AI 处理耗时，例如 200~300ms
        try {
            Thread.sleep(200 + random.nextInt(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("attendanceCount", 45 + random.nextInt(10)); // 随机 45~54 人出席
        result.put("totalScore", 80.0 + random.nextDouble() * 15.0); // 随机 80.0~95.0 分

        List<Map<String, Object>> details = new ArrayList<>();

        // 模拟 正常听课 (占多数)
        int normalCount = 35 + random.nextInt(10);
        details.add(createBehaviorDetail("正常听课", normalCount));

        // 模拟 玩手机 (可能触发低频抓拍 0~5)
        int phoneCount = random.nextInt(6);
        if (phoneCount > 0) {
            details.add(createBehaviorDetail("玩手机", phoneCount));
        }

        // 模拟 趴桌 (0~3)
        int sleepCount = random.nextInt(4);
        if (sleepCount > 0) {
            details.add(createBehaviorDetail("趴桌", sleepCount));
        }

        result.put("details", details);

        return result;
    }

    /**
     * 辅助方法：生成指定行为类别和人数的详情（附加随机坐标框集合）
     */
    private Map<String, Object> createBehaviorDetail(String behaviorType, int count) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("behaviorType", behaviorType);
        detail.put("count", count);

        // 构造简单的假坐标 (xyxy 格式) [100.0, 100.0, 150.0, 150.0]
        List<List<Double>> boundingBoxes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Double> box = new ArrayList<>();
            double x1 = random.nextDouble() * 500;
            double y1 = random.nextDouble() * 300;
            box.add(x1);
            box.add(y1);
            box.add(x1 + 50); // width
            box.add(y1 + 50); // height
            boundingBoxes.add(box);
        }
        detail.put("boundingBoxes", boundingBoxes);

        return detail;
    }
}
