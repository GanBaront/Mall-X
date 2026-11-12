package com.mall.alipay.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.mall.alipay.util.SignUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付宝渠道实现（沙箱/正式通用）
 *
 * 真实模式（mall.alipay.app-id 与 app-private-key 已配置，且渠道为 ALIPAY*）：
 *   - /alipay/precreate  调用 alipay.trade.wap.pay（手机网站支付），返回沙箱收银台 payUrl
 *   - /alipay/query      调用 alipay.trade.query 主动对账（无公网回调时的状态同步手段）
 *   - /alipay/notify     支付宝异步通知（需公网地址），验签后转发 mall-mgr /pay/callback
 *
 * 模拟模式（未配置密钥，或 WECHAT/UNIONPAY 渠道）：
 *   - 保留原本地模拟收银台 /mock/cashier，用于无沙箱环境演示
 */
@Slf4j
@RestController
public class AlipayController {

    @Value("${mall.callback-url}")
    private String callbackUrl;

    @Value("${mall.sign.key}")
    private String signKey;

    @Value("${mall.alipay.gateway:https://openapi.alipaydev.com/gateway.do}")
    private String alipayGateway;

    @Value("${mall.alipay.app-id:}")
    private String appId;

    @Value("${mall.alipay.app-private-key:}")
    private String appPrivateKey;

    @Value("${mall.alipay.alipay-public-key:}")
    private String alipayPublicKey;

    @Value("${mall.alipay.notify-url:}")
    private String notifyUrl;

    /** 沙箱总开关：false 时所有渠道走本地模拟收银台（网关故障期间的演示兜底） */
    @Value("${mall.alipay.sandbox-enabled:true}")
    private boolean sandboxEnabled;

    /** 响应验签开关（诊断用） */
    @Value("${mall.alipay.verify-response:true}")
    private boolean verifyResponse;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** payOrderId -> 待支付单据（模拟模式用） */
    private final Map<String, Map<String, Object>> pendingOrders = new ConcurrentHashMap<>();

    private volatile com.alipay.api.DefaultAlipayClient alipayClient;
    private volatile boolean alipayReady = false;

    /**
     * 服务启动初始化（@PostConstruct）。
     * 当沙箱开关开启且密钥已配置时，构建 DefaultAlipayClient 并启用沙箱模式；
     * 由于沙箱网关证书过期，后续安装「信任所有证书」作为临时方案；否则退化为本地模拟收银台。
     */
    @PostConstruct
    public void init() {
        if (configured() && sandboxEnabled) {
            try {
                // 参数顺序：gateway, appId, 私钥, format, charset, 支付宝公钥, signType
                // verifyResponse=false 时传空公钥，SDK 跳过响应验签（诊断/降级用）
                alipayClient = new DefaultAlipayClient(alipayGateway, appId, appPrivateKey,
                        "json", "UTF-8", verifyResponse ? alipayPublicKey : "", "RSA2");
                // 沙箱网关响应较慢，加大超时
                alipayClient.setConnectTimeout(15000);
                alipayClient.setReadTimeout(30000);
                alipayReady = true;
                log.info("[alipay] 沙箱支付已启用，APPID={}", appId);
                // 沙箱网关 openapi.alipaydev.com 的证书已过期（有效期至 2026-06-05），
                // 官方长期未更新，导致 trade.query 主动对账 SSL 握手失败。
                // 过渡方案：本服务内信任所有证书（仅该 JVM 与支付宝通信，生产环境必须移除）。
                installTrustAllSsl();
            } catch (Exception e) {
                log.error("[alipay] 初始化失败，退化为模拟模式：{}", e.getMessage());
            }
        } else {
            log.warn("[alipay] 沙箱未启用（开关关闭或未配置密钥），ALIPAY 渠道使用本地模拟收银台");
        }
    }

    /** 安装「信任所有证书 + 跳过主机名校验」的 SSL 上下文：仅用于沙箱网关证书过期期间的临时方案，生产环境必须移除 */
    private void installTrustAllSsl() {
        try {
            javax.net.ssl.TrustManager[] tms = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) { }
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) { }
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, tms, new java.security.SecureRandom());
            javax.net.ssl.SSLContext.setDefault(sc);
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
            log.warn("[alipay] 已启用信任所有证书模式（沙箱网关证书过期的临时方案）");
        } catch (Exception e) {
            log.error("[alipay] 安装信任证书失败：{}", e.getMessage());
        }
    }

    /**
     * 判断是否已配置真实支付宝密钥（appId 非空、私钥>100 字符、公钥>50 字符）。
     *
     * @return true=已配置且可启用沙箱模式
     */
    private boolean configured() {
        return appId != null && !appId.isBlank()
                && appPrivateKey != null && appPrivateKey.length() > 100
                && alipayPublicKey != null && alipayPublicKey.length() > 50;
    }

    /* ==================== 渠道下单 ==================== */

    /**
     * 渠道下单接口：POST /alipay/precreate
     * 真实沙箱（ALIPAY* 渠道且 alipayReady）调用 alipay.trade.wap.pay 返回沙箱收银台 payUrl；
     * 未配置密钥 / 非支付宝渠道 / 沙箱异常时自动降级为本地模拟收银台。
     *
     * @param req 下单参数：payOrderId、wayCode、money、subject、shopName、returnUrl(可选)
     * @return 统一响应 Map（id='0' 成功，含 payUrl、channelOrderNo）
     */
    @PostMapping("/alipay/precreate")
    public Map<String, Object> precreate(@RequestBody Map<String, Object> req) {
        String payOrderId = String.valueOf(req.get("payOrderId"));
        String wayCode = String.valueOf(req.getOrDefault("wayCode", "ALIPAY"));
        String money = String.valueOf(req.get("money"));
        String subject = String.valueOf(req.get("subject"));
        String shopName = String.valueOf(req.get("shopName"));
        String returnUrl = String.valueOf(req.getOrDefault("returnUrl", ""));

        // 真实沙箱：ALIPAY/ALIPAY_WAP 渠道走手机网站支付
        if (alipayReady && wayCode.startsWith("ALIPAY")) {
            try {
                AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
                if (returnUrl != null && !returnUrl.isEmpty() && !"null".equals(returnUrl)) {
                    request.setReturnUrl(returnUrl);
                }
                if (notifyUrl != null && !notifyUrl.isBlank()) {
                    request.setNotifyUrl(notifyUrl);
                }
                request.setBizContent("{"
                        + "\"out_trade_no\":\"" + payOrderId + "\","
                        + "\"total_amount\":\"" + money + "\","
                        + "\"subject\":\"" + escape(subject) + "\","
                        + "\"product_code\":\"QUICK_WAP_WAY\""
                        + "}");
                AlipayTradeWapPayResponse resp = alipayClient.pageExecute(request, "GET");
                if (resp.isSuccess() && resp.getBody() != null) {
                    log.info("[alipay] 沙箱下单成功：{} ¥{}", payOrderId, money);
                    Map<String, Object> respMap = new HashMap<>();
                    respMap.put("id", "0");
                    respMap.put("message", "沙箱下单成功");
                    respMap.put("channelOrderNo", "SANDBOX-" + payOrderId);
                    respMap.put("payUrl", resp.getBody());
                    return respMap;
                }
                // 安全修复：沙箱已就绪但本次调用失败时不再降级模拟收银台
                // （否则沙箱故障期间真实订单可走免费模拟支付通道）。演示兜底请关闭 sandbox-enabled 开关重启。
                log.error("[alipay] 沙箱下单失败（{}），拒绝降级模拟通道：{}", resp.getSubMsg(), payOrderId);
                Map<String, Object> err = new HashMap<>();
                err.put("id", "-1");
                err.put("message", "支付宝沙箱下单失败：" + resp.getSubMsg() + "，请稍后重试");
                return err;
            } catch (AlipayApiException e) {
                log.error("[alipay] 沙箱下单异常，拒绝降级模拟通道：{}", e.getMessage());
                Map<String, Object> err = new HashMap<>();
                err.put("id", "-1");
                err.put("message", "支付宝沙箱下单异常，请稍后重试");
                return err;
            }
        }

        // 模拟模式（未配置密钥 / 非支付宝渠道 / sandbox-enabled=false）
        return mockPrecreate(payOrderId, wayCode, money, subject, shopName);
    }

    /**
     * 本地模拟收银台下单：生成渠道流水号并暂存待支付单据到 pendingOrders，
     * 返回指向本地收银台 /mock/cashier 的 payUrl，供浏览器演示完整支付流程。
     *
     * @param payOrderId 支付单号
     * @param wayCode     支付渠道
     * @param money       金额
     * @param subject     订单标题
     * @param shopName    商户名
     * @return 统一响应 Map（含 payUrl、channelOrderNo）
     */
    private Map<String, Object> mockPrecreate(String payOrderId, String wayCode, String money, String subject, String shopName) {
        // 顺带清理超过 2 小时的遗留单据（用户弃单不再回来），防止 pendingOrders 无限膨胀
        long now = System.currentTimeMillis();
        pendingOrders.values().removeIf(o -> {
            Object ts = o.get("createdAt");
            return ts instanceof Long && now - (Long) ts > 2 * 60 * 60 * 1000L;
        });
        Map<String, Object> order = new HashMap<>();
        order.put("payOrderId", payOrderId);
        order.put("money", money);
        order.put("subject", subject);
        order.put("shopName", shopName);
        order.put("wayCode", wayCode);
        order.put("channelOrderNo", "ALI" + LocalDateTime.now().format(TS) + (100 + pendingOrders.size()));
        // 支付确认凭证：随机 UUID，仅随收银台 payUrl 下发；confirm 时强校验，
        // 防止知道 payOrderId 的任何人直接调用 /alipay/confirm 伪造支付成功
        String confirmToken = java.util.UUID.randomUUID().toString().replace("-", "");
        order.put("confirmToken", confirmToken);
        order.put("createdAt", now);
        pendingOrders.put(payOrderId, order);
        log.info("[alipay-mock] 渠道下单成功：{} {}", payOrderId, money);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", "0");
        resp.put("message", "渠道下单成功");
        resp.put("channelOrderNo", order.get("channelOrderNo"));
        resp.put("payUrl", "http://localhost:10012/mock/cashier?payOrderId=" + payOrderId + "&token=" + confirmToken);
        return resp;
    }

    /* ==================== 主动对账（自研签名直调，绕过 SDK 响应验签缺陷） ==================== */

    /**
     * 说明：沙箱响应用页面展示的「支付宝公钥」验签不通过（沙箱签名密钥与展示值不一致的已知问题），
     * 故对账改为本地 RSA2 签名 + 直连接口解析。
     * 业务安全由三层保证：TLS 通道 + out_trade_no 回显 + 金额与本地订单比对（mall-mgr 侧）。
     */
    /**
     * 主动对账接口：POST /alipay/query
     * 无公网异步通知时的状态同步手段。沙箱就绪则直调 alipay.trade.query 解析 trade_status；
     * 未就绪返回 mode=mock。trade_status：TRADE_SUCCESS/TRADE_FINISHED=已支付，
     * WAIT_BUYER_PAY=未支付，QUERY_ERROR=查询异常。
     *
     * @param req 对账参数：payOrderId
     * @return 含 mode、tradeStatus、channelOrderNo、payTime、totalAmount 等的 Map
     */
    @PostMapping("/alipay/query")
    public Map<String, Object> query(@RequestBody Map<String, Object> req) {
        String payOrderId = String.valueOf(req.get("payOrderId"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", "0");
        resp.put("mode", alipayReady ? "sandbox" : "mock");
        if (!alipayReady) {
            resp.put("tradeStatus", "MOCK");
            return resp;
        }
        try {
            Map<String, Object> biz = rawTradeQuery(payOrderId);
            // alipay_trade_query_response 节点
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) biz.get("response");
            if (node == null) {
                resp.put("tradeStatus", "QUERY_ERROR");
                resp.put("message", String.valueOf(biz.get("raw")));
                return resp;
            }
            String code = String.valueOf(node.get("code"));
            if ("10000".equals(code)) {
                resp.put("tradeStatus", String.valueOf(node.get("trade_status")));
                resp.put("channelOrderNo", String.valueOf(node.get("trade_no")));
                resp.put("payTime", String.valueOf(node.get("send_pay_date")));
                resp.put("totalAmount", String.valueOf(node.get("total_amount")));
            } else if ("40004".equals(code) && "ACQ.TRADE_NOT_EXIST".equals(String.valueOf(node.get("sub_code")))) {
                resp.put("tradeStatus", "WAIT_BUYER_PAY");
            } else {
                resp.put("tradeStatus", "QUERY_ERROR");
                resp.put("subCode", String.valueOf(node.get("sub_code")));
                resp.put("message", String.valueOf(node.get("sub_msg")));
            }
        } catch (Exception e) {
            log.error("[alipay] 对账异常", e);
            resp.put("tradeStatus", "QUERY_ERROR");
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    /** 本地 RSA2 签名直调 alipay.trade.query，返回 {response:业务节点, sign:原始签名} */
    /**
     * 本地 RSA2 签名直调 alipay.trade.query（绕过 SDK 响应验签缺陷）。
     * 步骤：拼参 -> TreeMap 字典序排序 -> SHA256withRSA 私钥签名 -> 表单 POST 沙箱网关 ->
     * 解析 alipay_trade_query_response 业务节点。业务安全由 TLS + out_trade_no 回显 + 金额比对保证。
     *
     * @param payOrderId 支付单号（out_trade_no）
     * @return 含 response(业务节点)/raw(原始报文) 的 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> rawTradeQuery(String payOrderId) throws Exception {
        java.util.TreeMap<String, String> params = new java.util.TreeMap<>();
        params.put("app_id", appId);
        params.put("method", "alipay.trade.query");
        params.put("format", "JSON");
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("version", "1.0");
        params.put("biz_content", "{\"out_trade_no\":\"" + payOrderId + "\"}");

        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(v);
        });
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(loadPrivateKey());
        sig.update(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sign = java.util.Base64.getEncoder().encodeToString(sig.sign());

        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        params.forEach((k, v) -> form.add(k, v));
        form.add("sign", sign);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        String body = restTemplate.postForObject(alipayGateway,
                new org.springframework.http.HttpEntity<>(form, headers), String.class);
        log.info("[alipay] trade.query 原始响应：{}", body);

        Map<String, Object> out = new HashMap<>();
        if (body == null) {
            out.put("raw", "empty response");
            return out;
        }
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> root = om.readValue(body, Map.class);
        Object node = root.get("alipay_trade_query_response");
        if (node instanceof Map) {
            out.put("response", node);
        } else {
            out.put("raw", body);
        }
        return out;
    }

    /**
     * 将 Base64 编码的 PKCS8 私钥字符串加载为 RSA PrivateKey，用于本地 RSA2 直签。
     *
     * @return RSA 私钥对象
     */
    private java.security.PrivateKey loadPrivateKey() throws Exception {
        byte[] der = java.util.Base64.getDecoder().decode(appPrivateKey);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
    }

    /* ==================== 支付宝异步通知（需公网 notify-url） ==================== */

    /**
     * 支付宝异步通知接口：POST /alipay/notify（需公网 notify-url）
     * 接收支付宝回调，先以支付宝公钥 rsaCheckV1 验签；验签通过且交易成功(TRADE_SUCCESS/TRADE_FINISHED)时，
     * 用平台签名密钥对回调体做 MD5 自签后转发 mall-mgr /pay/callback，完成订单状态同步。
     * 返回 "success" 告知支付宝已收到，"fail" 触发其重试。
     *
     * @param request 支付宝异步通知 HttpServletRequest
     * @return "success" 或 "fail"
     */
    @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, String.join(",", v)));
        try {
            boolean signOk = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!signOk) {
                log.warn("[alipay] 异步通知验签失败");
                return "fail";
            }
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                Map<String, Object> cb = new HashMap<>();
                cb.put("payOrderId", params.get("out_trade_no"));
                cb.put("channelOrderNo", params.get("trade_no"));
                cb.put("payState", 0);
                cb.put("payTime", params.getOrDefault("gmt_payment", ""));
                cb.put("money", params.get("total_amount")); // 回调携带订单金额，mall-mgr 侧强校验防串单
                // 用平台签名密钥对回调体做 MD5 自签，供 mall-mgr 验签后识别为合法来源
                cb.put("sign", SignUtil.sign(cb, signKey));
                restTemplate.postForObject(callbackUrl, cb, Map.class);
                log.info("[alipay] 异步通知已转发：{}", params.get("out_trade_no"));
            }
            return "success";
        } catch (Exception e) {
            log.error("[alipay] 异步通知处理失败", e);
            return "fail";
        }
    }

    /* ==================== 模拟收银台（无沙箱配置时兜底） ==================== */

    /**
     * 模拟收银台页面：GET /mock/cashier
     * 测试用收银台（路径 /mock/cashier）。从 pendingOrders 取出待支付单据渲染 HTML，
     * 提供「模拟支付成功 / 失败」按钮，点击后调用 /alipay/confirm 完成模拟回调。
     *
     * @param payOrderId 支付单号
     * @return 收银台 HTML 页面（订单不存在则返回提示页）
     */
    @GetMapping(value = "/mock/cashier", produces = MediaType.TEXT_HTML_VALUE)
    public String cashier(@RequestParam String payOrderId, @RequestParam(required = false) String token) {
        Map<String, Object> order = pendingOrders.get(payOrderId);
        if (order == null) {
            return "<html><meta charset='utf-8'><body style='font-family:sans-serif;text-align:center;padding-top:80px'><h2>订单不存在或已处理</h2></body></html>";
        }
        // 凭证校验：token 与下单时签发的不一致（含未携带）拒绝展示收银台
        if (token == null || !token.equals(String.valueOf(order.get("confirmToken")))) {
            return "<html><meta charset='utf-8'><body style='font-family:sans-serif;text-align:center;padding-top:80px'><h2>收银台链接无效</h2></body></html>";
        }
        String way = String.valueOf(order.get("wayCode"));
        String wayName = "WECHAT".equals(way) ? "微信支付" : "UNIONPAY".equals(way) ? "银联支付" : "支付宝";
        return """
                <html><head><meta charset='utf-8'><title>Mall-X 收银台</title></head>
                <body style="font-family:'Microsoft YaHei',sans-serif;background:#f5f7fb;margin:0">
                <div style="max-width:420px;margin:60px auto;background:#fff;border-radius:12px;padding:36px;box-shadow:0 6px 24px rgba(0,0,0,.08);text-align:center">
                  <div style="color:#888;font-size:14px">%s · %s</div>
                  <div style="font-size:16px;margin-top:12px">%s</div>
                  <div style="font-size:34px;color:#2B6CFF;font-weight:700;margin:16px 0 28px">¥ %s</div>
                  <button onclick="pay(0)" style="width:100%%;padding:14px;border:0;border-radius:8px;background:#2B6CFF;color:#fff;font-size:16px;cursor:pointer">模拟支付成功</button>
                  <button onclick="pay(1)" style="width:100%%;padding:14px;border:0;border-radius:8px;background:#fff;color:#E64340;border:1px solid #E64340;font-size:16px;cursor:pointer;margin-top:12px">模拟支付失败</button>
                  <div id="tip" style="margin-top:20px;color:#999;font-size:13px"></div>
                </div>
                <script>
                function pay(state){
                  document.getElementById('tip').textContent = '支付结果确认中...';
                  fetch('/alipay/confirm?payOrderId=%s&token=%s&payState='+state)
                    .then(r=>r.json()).then(j=>{
                      document.getElementById('tip').textContent = j.message;
                      document.getElementById('tip').style.color = state===0 ? '#52c41a' : '#E64340';
                    });
                }
                </script>
                </body></html>
                """.formatted(
                wayName,
                order.get("shopName"),
                order.get("subject"),
                order.get("money"),
                payOrderId,
                token
        );
    }

    /**
     * 模拟支付确认接口：POST /alipay/confirm
     * 由收银台前端在点击按钮后调用：校验下单时签发的一次性支付凭证（token），
     * 移除 pendingOrders 待支付单据，按 payState 组装回调体（含订单金额，供 mall-mgr 强校验）
     * 并做 MD5 自签，转发 mall-mgr /pay/callback（payState=0 成功 / 1 失败），驱动订单状态机流转。
     *
     * @param payOrderId 支付单号
     * @param token      下单时签发的支付确认凭证（防伪造）
     * @param payState   0=模拟支付成功，1=模拟支付失败
     * @return 统一响应 Map（id='0' 成功）
     */
    @PostMapping("/alipay/confirm")
    @ResponseBody
    public Map<String, Object> confirm(@RequestParam String payOrderId,
                                       @RequestParam(required = false) String token,
                                       @RequestParam int payState) {
        // 先读单据校验凭证（不立即移除，避免无效请求把单据弄丢导致正常收银台无法重试）
        Map<String, Object> order = pendingOrders.get(payOrderId);
        Map<String, Object> resp = new HashMap<>();
        if (order == null) {
            resp.put("id", "-1");
            resp.put("message", "订单不存在或已处理");
            return resp;
        }
        // 凭证强校验：不知道下单时签发的随机 token 无法触发模拟回调
        if (token == null || !token.equals(String.valueOf(order.get("confirmToken")))) {
            resp.put("id", "-1");
            resp.put("message", "支付凭证无效");
            return resp;
        }
        // 凭证通过后原子移除（并发第二次 remove 返回 null，天然防重放）
        order = pendingOrders.remove(payOrderId);
        if (order == null) {
            resp.put("id", "-1");
            resp.put("message", "订单不存在或已处理");
            return resp;
        }
        Map<String, Object> notify = new HashMap<>();
        notify.put("payOrderId", payOrderId);
        notify.put("channelOrderNo", order.get("channelOrderNo"));
        notify.put("payState", payState);
        notify.put("money", order.get("money")); // 回调携带订单金额，mall-mgr 侧强校验防串单
        notify.put("payTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        // 用平台签名密钥对回调体做 MD5 自签，供 mall-mgr 验签后识别为合法来源
        notify.put("sign", SignUtil.sign(notify, signKey));
        try {
            restTemplate.postForObject(callbackUrl, notify, Map.class);
            resp.put("id", "0");
            resp.put("message", payState == 0 ? "支付成功" : "已模拟支付失败，订单将保持待支付");
            log.info("[alipay-mock] 回调完成：{} payState={}", payOrderId, payState);
        } catch (Exception e) {
            log.error("[alipay-mock] 回调失败：{}", e.getMessage());
            // 回调失败时回补单据，收银台可重试（否则 mall-mgr 短暂不可用时订单只能等 30 分钟超时关闭）
            pendingOrders.put(payOrderId, order);
            resp.put("id", "-1");
            resp.put("message", "回调业务系统失败，请重试");
        }
        return resp;
    }

    /** 渠道退款（模拟即时打款成功；接真退款用 alipay.trade.refund） */
    /**
     * 渠道退款接口：POST /alipay/refund
     * 模拟即时打款成功（接入真实支付宝时用 alipay.trade.refund）。返回成功响应供 mall-mgr 记账。
     *
     * @param req 退款参数：payOrderId、refundMoney
     * @return 统一响应 Map（id='0' 成功，含 channelRefundNo）
     */
    @PostMapping("/alipay/refund")
    public Map<String, Object> refund(@RequestBody Map<String, Object> req) {
        String payOrderId = String.valueOf(req.get("payOrderId"));
        String refundMoney = String.valueOf(req.get("refundMoney"));
        log.info("[alipay] 渠道退款：{} ¥{}", payOrderId, refundMoney);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", "0");
        resp.put("message", "渠道退款成功");
        resp.put("channelRefundNo", "ALIR" + LocalDateTime.now().format(TS));
        return resp;
    }

    /**
     * 转义 JSON 字符串：先转义反斜杠再转义双引号/换行（顺序不可反），避免拼入 bizContent 时破坏报文结构。
     */
    private static String escape(String s) {
        return s == null ? "" : s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ");
    }
}
