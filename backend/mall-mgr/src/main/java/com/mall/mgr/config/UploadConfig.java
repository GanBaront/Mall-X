package com.mall.mgr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传文件存储与静态访问配置（mall-mgr 平台管理服务）
 * <p>存储目录：mall.upload.dir 配置（默认 ./uploads，即 mall-mgr 运行目录下 uploads 文件夹），
 * 启动时自动创建；静态映射：GET /upload/** 直接读磁盘文件，
 * 网关侧已把 /upload 加入免鉴权白名单（浏览器 img 标签无法携带 token 请求头）。
 */
@Configuration
public class UploadConfig implements WebMvcConfigurer {

    /** 上传文件存储目录（相对于 mall-mgr 运行目录，可绝对路径覆盖） */
    @Value("${mall.upload.dir:uploads}")
    private String uploadDir;

    /** 取存储目录绝对路径（不存在则自动创建），UploadController 与静态映射共用 */
    public Path uploadDir() {
        try {
            Path p = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(p);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建上传目录：" + uploadDir, e);
        }
    }

    /** 静态资源映射：/upload/** → 磁盘目录（Web 端 img 可直接访问） */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadDir().toString().replace('\\', '/') + "/");
    }
}
