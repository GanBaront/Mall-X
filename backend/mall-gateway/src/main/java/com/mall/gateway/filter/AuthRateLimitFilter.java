package com.mall.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关统一过滤器：
 * 1. IP 限流（指导书 4.7：超限统一返回 {"message":"系统繁忙，请稍后再试"}）
 *    —— 课程环境未部署 Redis，这里用内存滑动窗口实现；
 *      生产环境应替换为 Redis+Lua 原子限流（见 README 注释）。
 * 2. JWT 统一鉴权，解析 token 并把用户身份透传给下游微服务。
 *
 * 统一响应格式（与前端约定）：HTTP 200 + {"id":"-1","message":"..."}
 */
@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    @Value("${mall.jwt.secret}")
    private String jwtSecret;

    @Value("${mall.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${mall.rate-limit.max-requests:200}")
    private long maxRequests;

    @Value("${mall.rate-limit.window-seconds:10}")
    private long windowSeconds;

    /** 免鉴权白名单 */
    private static final String[] WHITE_LIST = {
            "/admin/login", "/mch/login", "/mch/register", "/api/login", "/api/register",
            "/pay/callback",          // 支付回调由签名验签保护
            "/alipay/notify",         // 渠道异步通知
            "/api/products",          // 商品浏览允许游客访问（详情/列表）
            "/api/products/detail",
            "/api/category/list",     // 商品类目树（首页/分类页游客可浏览，与管理员类目同源）
            "/api/pay/ways",          // 可用支付方式（下单页展示，管理员启停后即时生效）
            "/upload",                // 上传图片静态访问（img 标签无法带 token 请求头）
            "/static",                // 商品库真实照片 static/goods/**（jar 内 classpath 静态资源，公开内容）
            "/actuator"
    };

    /** ip -> [窗口起点, 计数] */
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /**
     * 全局过滤器核心方法：按「限流 -> 鉴权」顺序处理每个请求。
     *   1) 限流：开启限流且非白名单路径时，按客户端 IP 滑动窗口统计请求数，超限返回「系统繁忙，请稍后再试」；
     *   2) 鉴权：非白名单路径校验 token——缺失返回「未登录」、JWT 验签/解析失败返回「登录状态无效」；
     *      校验通过后将 userId/username/role/num 写入请求头，透传给下游微服务。
     *
     * @param exchange 当前请求/响应上下文
     * @param chain    网关过滤器链
     * @return 响应 Mono（继续链路或短路返回 JSON）
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawPath = exchange.getRequest().getPath().value();
        // 含点段的路径一律拒绝：规范化仅用于本地判定，若放行原始路径，下游容器会自行规范化命中真实接口（鉴权绕过）
        if (rawPath.contains("/./") || rawPath.contains("/../") || rawPath.endsWith("/.") || rawPath.endsWith("/..")) {
            return writeJson(exchange, "{\"id\":\"-1\",\"message\":\"非法请求路径\"}");
        }
        String path = normalize(rawPath);
        String clientIp = resolveIp(exchange.getRequest());

        // 1) 限流
        if (rateLimitEnabled && !whiteListed(path) && overLimit(clientIp)) {
            return writeJson(exchange, "{\"id\":\"-1\",\"message\":\"系统繁忙，请稍后再试\"}");
        }

        // 安全校验前置：无论白名单与否，先剥离客户端自带的身份透传头（防止伪造 X-Mall-* 冒充他人身份）
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-Mall-User-Id");
                    h.remove("X-Mall-Username");
                    h.remove("X-Mall-Role");
                    h.remove("X-Mall-User-Num");
                })
                .build();
        exchange = exchange.mutate().request(stripped).build();

        // 2) 鉴权
        if (!whiteListed(path)) {
            String token = exchange.getRequest().getHeaders().getFirst("token");
            if (token == null || token.isBlank()) {
                return writeJson(exchange, "{\"id\":\"-1\",\"message\":\"未登录或登录已过期\"}");
            }
            try {
                // JWT 校验：用配置的 HS256 密钥验签并解析声明（claims）
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                        .parseClaimsJws(token).getBody();
                String role = String.valueOf(claims.get("role", String.class));
                // 角色校验：管理员/商户接口按路径前缀限定角色，防止普通用户直接调用管理/商户接口
                String roleError = checkRole(path, role);
                if (roleError != null) {
                    return writeJson(exchange, roleError);
                }
                // 身份透传：headers(...set) 为覆盖式写入，客户端同名头已在上方剥离，双重保险
                final ServerHttpRequest mutated = exchange.getRequest().mutate()
                        .headers(h -> {
                            h.set("X-Mall-User-Id", String.valueOf(claims.get("userId", Number.class) != null
                                    ? claims.get("userId", Number.class).longValue() : 0));
                            h.set("X-Mall-Username", String.valueOf(claims.get("username", String.class)));
                            h.set("X-Mall-Role", role);
                            h.set("X-Mall-User-Num", String.valueOf(claims.get("num", String.class)));
                        })
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            } catch (Exception e) {
                return writeJson(exchange, "{\"id\":\"-1\",\"message\":\"登录状态无效，请重新登录\"}");
            }
        }
        return chain.filter(exchange);
    }

    /**
     * 按路径前缀校验角色：
     *   /admin/**（登录除外）          → 仅 admin；
     *   /api/mch/**、/refund/**        → 仅 merchant（商户后台/退款处理）；
     *   /pay/list/mch                  → 仅 merchant（商户订单列表）；
     *   /statis/platform               → 仅 admin（平台级经营统计）；
     *   其余登录路径                   → 已登录即可（用户/商户/管理员共用，如 /pay/create、/api/orders）。
     *
     * @param path 规范化后的请求路径（不含查询串）
     * @param role JWT 中的角色（admin/merchant/user）
     * @return null=通过；否则返回拒绝响应 JSON
     */
    private String checkRole(String path, String role) {
        if (path.startsWith("/admin/") || "/statis/platform".equals(path)) {
            if (!"admin".equals(role)) {
                return "{\"id\":\"-1\",\"message\":\"无权访问管理后台\"}";
            }
            return null;
        }
        if (path.startsWith("/api/mch/") || path.startsWith("/refund/") || "/pay/list/mch".equals(path)) {
            if (!"merchant".equals(role)) {
                return "{\"id\":\"-1\",\"message\":\"无权访问商户接口\"}";
            }
        }
        return null;
    }

    /**
     * 路径点段规范化：逐段消费，遇 "." 忽略、遇 ".." 弹出上一段；不影响正常路径。
     * @param path 原始路径
     * @return 规范化后的路径
     */
    private static String normalize(String path) {
        if (!path.contains("/.")) return path;
        String[] segs = path.split("/");
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String seg : segs) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) {
                stack.pollLast();
            } else {
                stack.addLast(seg);
            }
        }
        return "/" + String.join("/", stack);
    }

    /**
     * 判断请求路径是否命中免鉴权白名单。
     * 支持精确匹配、前缀匹配（w + "/" 或 w + "?"）；模拟收银台页面 /mock/cashier 也直接放行。
     *
     * @param path 请求路径
     * @return true=命中白名单，跳过 JWT 鉴权（注意：限流仍对非白名单外的路径生效）
     */
    private boolean whiteListed(String path) {
        // path 为规范化后的路径（不含查询串），等值与前缀匹配即可覆盖全部白名单场景
        for (String w : WHITE_LIST) {
            if (path.equals(w) || path.startsWith(w + "/")) {
                return true;
            }
        }
        // 模拟收银台页面由浏览器直接访问
        return path.startsWith("/mock/cashier");
    }

    /**
     * 基于内存滑动窗口判断某 IP 是否超限。
     * 算法：维护 ip -> {windowStart, count}。若距窗口起点已超过 windowSeconds，则重置窗口起点与计数器（窗口滑动）；
     *      否则在当前窗口内计数自增 1，超过 maxRequests 即判定限流。
     * 说明：课程环境未部署 Redis，采用内存实现；生产环境应替换为 Redis+Lua 原子限流（见 README）。
     *
     * @param ip 客户端 IP
     * @return true=已超限，需拒绝请求
     */
    private boolean overLimit(String ip) {
        long now = System.currentTimeMillis();
        WindowCounter wc = counters.computeIfAbsent(ip, k -> new WindowCounter());
        synchronized (wc) {
            if (now - wc.windowStart > windowSeconds * 1000) {
                wc.windowStart = now;
                wc.count = new AtomicLong(0);
            }
            return wc.count.incrementAndGet() > maxRequests;
        }
    }

    /**
     * 解析客户端真实 IP：优先取 X-Forwarded-For 首段（经反向代理/网关时），否则取 RemoteAddress。
     *
     * @param request 服务端请求
     * @return 客户端 IP 字符串
     */
    private String resolveIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return String.valueOf(request.getRemoteAddress());
    }

    /**
     * 短路响应 JSON：统一 HTTP 200 + body，业务码放 id 字段（如 "-1"），便于前端统一处理。
     *
     * @param exchange 当前上下文
     * @param json     响应 JSON 字符串
     * @return 响应 Mono
     */
    private Mono<Void> writeJson(ServerWebExchange exchange, String json) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK); // 统一 HTTP 200，业务码放 id 字段，便于前端 invoke 统一处理
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器执行顺序：返回 -100，确保在路由等业务过滤器之前优先执行（先鉴权、限流，再路由下游）。
     *
     * @return 执行优先级（数值越小越靠前）
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /** 滑动窗口计数器：windowStart 为窗口起点毫秒时间戳，count 为窗口内累计请求数 */
    private static class WindowCounter {
        long windowStart = System.currentTimeMillis();
        AtomicLong count = new AtomicLong(0);
    }
}
