package com.mall.mgr.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * MD5 签名工具（mall-mgr 平台管理服务公共工具）
 * <p>指导书通用规范：请求参数按 key 升序拼接 k=v&...&key=密钥，MD5 后转大写。
 * 是否强制验签由 mall.sign.required（默认 false）控制；密钥 mall.sign.key。
 */
@Component
public class SignUtil {

    @Value("${mall.sign.required:false}")
    private boolean required;

    @Value("${mall.sign.key}")
    private String signKey;

    /** 是否开启强制签名校验（对应配置 mall.sign.required） */
    public boolean isRequired() {
        return required;
    }

    /**
     * 对请求参数生成 MD5 签名。
     * @param params 待签名参数；自动按 key 升序（TreeMap）拼接 k=v&...，剔除 null、空串与 sign 字段，末尾追加 &key=密钥
     * @return 大写 MD5 签名串
     */
    public String sign(Map<String, Object> params) {
        TreeMap<String, Object> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (v != null && !"sign".equals(k) && !String.valueOf(v).isEmpty()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(k).append('=').append(v);
            }
        });
        sb.append("&key=").append(signKey);
        return md5(sb.toString());
    }

    /** 验签；未开启强制验签（mall.sign.required=false）时直接通过（返回 true） */
    public boolean verify(Map<String, Object> params) {
        return verify(params, false);
    }

    /**
     * 验签（可强制）。
     * @param params 待验签参数
     * @param force  true=无论 mall.sign.required 开关如何都必须验签（支付回调等安全敏感端点使用，
     *               防止开关默认关闭时任何人伪造支付成功）
     */
    public boolean verify(Map<String, Object> params, boolean force) {
        // 开关未开启且非强制场景则跳过验签，便于联调；开启后或强制场景缺失 sign 视为非法请求
        if (!required && !force) return true;
        Object sign = params.get("sign");
        if (sign == null || String.valueOf(sign).isEmpty()) return false;
        return String.valueOf(sign).equalsIgnoreCase(sign(params));
    }

    public static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
