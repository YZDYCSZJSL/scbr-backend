package com.scbrbackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest
class ScbrBackendApplicationTests {

    @Test
    void contextLoads() {
        // 1. 定义地址和数据
        String autodlApi = "https://u377605-b94b-0875d3bc.westc.gpuhub.com:8443/detect";
        String ossUrl = "https://aquarius01.oss-cn-hangzhou.aliyuncs.com/a03380f4-3a58-44d6-98d3-af0f1f9b9c83.png";

        // 2. 构造 JSON 字符串 (也可以用 Jackson/Gson 库来生成)
        String jsonPayload = "{\"url\": \"" + ossUrl + "\"}";

        // 3. 创建 HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 4. 构造请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(autodlApi))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // 5. 发送请求并处理结果
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                System.out.println("检测成功！返回结果：" + response.body());
                // 这里可以使用 Jackson 或 Gson 解析 response.body()
            } else {
                System.err.println("请求失败，状态码：" + response.statusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
