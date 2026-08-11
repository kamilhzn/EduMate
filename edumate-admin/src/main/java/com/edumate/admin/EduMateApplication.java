package com.edumate.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication(scanBasePackages = "com.edumate")
public class EduMateApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(EduMateApplication.class, args);
    }

    /**
     * 手动加载项目根目录的 .env 文件为系统属性。
     * <p>在 Spring Boot 启动前执行，确保 LangChain4j DashScope 等自动配置能读取到 API Key。</p>
     */
    private static void loadDotEnv() {
        Path[] candidates = {
            Paths.get(".env"),                                    // 当前工作目录
            Paths.get("..", ".env"),                              // 父目录（模块运行时）
            Paths.get(System.getProperty("user.dir"), ".env"),    // user.dir
            Paths.get(System.getProperty("user.dir"), "..", ".env") // user.dir 的父目录
        };

        for (Path path : candidates) {
            if (Files.exists(path) && Files.isReadable(path)) {
                try {
                    int count = 0;
                    for (String line : Files.readAllLines(path)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String value = line.substring(idx + 1).trim();
                            // 去除可能的引号包裹
                            if (value.length() >= 2
                                    && ((value.startsWith("\"") && value.endsWith("\""))
                                    || (value.startsWith("'") && value.endsWith("'")))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            // 仅当系统环境变量未设置时才写入系统属性
                            // 这样 IDEA Edit Configuration 中的 Environment 仍可覆盖 .env
                            if (System.getenv(key) == null && System.getProperty(key) == null) {
                                System.setProperty(key, value);
                                count++;
                            }
                        }
                    }
                    System.out.println("[.env] Loaded " + count + " variables from: " + path.toAbsolutePath().normalize());
                } catch (IOException e) {
                    System.err.println("[.env] Failed to load: " + e.getMessage());
                }
                return;
            }
        }
        System.out.println("[.env] No .env file found in any candidate path.");
    }
}
