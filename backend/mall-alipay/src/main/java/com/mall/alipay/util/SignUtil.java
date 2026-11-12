package com.mall.alipay.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/** MD5 签名（与 mall-mgr SignUtil 规则一致） */
public class SignUtil {

    /**
     * 生成 MD5 签名（生成步骤，与 mall-mgr SignUtil 规则一致）。
     *   1) 将参数按 key 字典序（TreeMap）升序排序；
     *   2) 跳过空值字段与 sign 字段，拼接为 k1=v1&k2=v2 形式；
     *   3) 末尾追加 &key={signKey}（拼接平台与渠道共享的签名密钥）；
     *   4) 对整串做 UTF-8 的 MD5，结果转大写十六进制即为签名。
     * 校验时由接收方用相同规则重新计算 sign 并比对，可检测参数被篡改。
     *
     * @param params  待签名参数 Map
     * @param signKey 签名密钥
     * @return 大写 MD5 签名串
     */
    public static String sign(Map<String, Object> params, String signKey) {
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

    /**
     * 计算字符串的 MD5 摘要（UTF-8 编码，输出大写十六进制）。
     *
     * @param text 待摘要原文
     * @return 32 位大写十六进制 MD5 串
     */
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
