package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.config.UploadConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传控制器（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr；接口前缀：/api（需登录，网关统一鉴权）。
 * 文件落到本地磁盘 mall.upload.dir（默认 ./uploads），经 WebMvc 静态映射
 * 以 /upload/** 对外访问（网关白名单放行，浏览器 img 标签可直接加载）。
 * 接口清单：
 * - POST /api/upload  上传图片（form-data 字段名 file），返回相对路径 path=/upload/xxx.jpg
 */
@RestController
@RequiredArgsConstructor
public class UploadController {

    /** 允许上传的图片扩展名（白名单校验，防止上传脚本文件） */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 单文件大小上限：2MB（头像/商品图足够，防大文件占满磁盘） */
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    private final UploadConfig uploadConfig;

    /**
     * 上传图片（POST /api/upload，multipart/form-data，字段名 file）
     * <p>校验：非空、扩展名白名单、大小 ≤ 2MB；文件名用 UUID 重命名防覆盖与路径穿越。
     * 返回：path（如 /upload/ab12cd.jpg），前端拼接网关地址后访问。
     */
    @PostMapping("/api/upload")
    public LinkedHashMap<String, Object> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("请选择要上传的图片");
        if (file.getSize() > MAX_SIZE) throw new BizException("图片大小不能超过 2MB");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext)) throw new BizException("仅支持 jpg/png/gif/webp 格式图片");

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path target = uploadConfig.uploadDir().resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new BizException("图片保存失败，请稍后重试");
        }
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("path", "/upload/" + filename);
        return R.ok("上传成功", data);
    }
}
