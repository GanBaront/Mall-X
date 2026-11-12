package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.MktCoupon;
import com.mall.mgr.repository.MktCouponRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 营销卡券控制器（mall-mgr 平台管理服务）：领券中心幸运转盘 + 我的卡券。
 * <p>所属模块：mall-mgr；接口前缀：/api/coupon（需登录，按网关透传身份归属）。
 * 接口清单：
 * - POST /api/coupon/draw    幸运转盘抽奖：后端加权随机决定奖品并立即生成券记录，返回扇区下标供前端动画定位
 * - POST /api/coupon/list    我的卡券列表（可选 state 筛选：0 未使用 / 1 已使用）
 * - POST /api/coupon/usable  我的未使用卡券（下单选券时展示，已自动过滤过期券）
 * 奖品池：8 个扇区（4 档现金红包 + 4 档折扣券），加权随机；每人每天限抽 3 次。
 * 时效：券自发放起 15 天内有效（expireAt），过期不可用于下单抵扣，「我的卡券」中标记已过期。
 */
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final MktCouponRepository couponRepository;
    private final UserContext userContext;

    /** 每人每日抽奖次数上限 */
    private static final int DAILY_LIMIT = 3;

    /** userNum -> 锁：同一用户的抽奖串行执行，防止并发请求同时通过每日限次校验超抽刷券 */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> drawLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 转盘奖品池（下标即转盘扇区号，weight 为加权随机权重） */
    private static final Prize[] PRIZES = {
            new Prize("¥2 现金红包", 2, null, 25),
            new Prize("9 折优惠券", 0, new BigDecimal("0.90"), 16),
            new Prize("¥5 现金红包", 5, null, 16),
            new Prize("8.8 折优惠券", 0, new BigDecimal("0.88"), 13),
            new Prize("¥10 现金红包", 10, null, 9),
            new Prize("8.5 折优惠券", 0, new BigDecimal("0.85"), 9),
            new Prize("¥20 现金红包", 20, null, 5),
            new Prize("8 折优惠券", 0, new BigDecimal("0.80"), 7)
    };

    /** 奖品定义：name 名称 / cashAmount 现红包金额（折扣券为 null）/ discountRate 折扣率（红包为 null）/ weight 抽中权重 */
    private record Prize(String name, int cashAmount, BigDecimal discountRate, int weight) {
    }

    /**
     * 幸运转盘抽奖（POST /api/coupon/draw）。
     * 校验每日限次后加权随机选取奖品，立即生成未使用券记录（发券即入库，防止前端伪造）。
     * 返回：prizeIndex（扇区下标 0-7，前端转盘动画转到该格）、prize（券信息）、leftTimes（今日剩余次数）。
     */
    @PostMapping("/api/coupon/draw")
    public LinkedHashMap<String, Object> draw(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userNum = userContext.userNum(request);
        if (userNum == null || userNum.isBlank() || "null".equals(userNum)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        // 同一用户抽奖串行化：count-then-insert 在锁内执行，防止并发超抽
        synchronized (drawLocks.computeIfAbsent(userNum, k -> new Object())) {
            return doDraw(userNum);
        }
    }

    /** 抽奖实际执行（调用方已持有该用户的抽奖锁） */
    private LinkedHashMap<String, Object> doDraw(String userNum) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = couponRepository.findByUserNumAndCreatedAtAfter(userNum, todayStart).size();
        if (todayCount >= DAILY_LIMIT) {
            throw new BizException("今日抽奖次数已用完，明天再来吧");
        }

        // 加权随机选取奖品扇区
        int totalWeight = 0;
        for (Prize p : PRIZES) totalWeight += p.weight();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int prizeIndex = 0;
        for (int i = 0; i < PRIZES.length; i++) {
            roll -= PRIZES[i].weight();
            if (roll < 0) {
                prizeIndex = i;
                break;
            }
        }
        Prize prize = PRIZES[prizeIndex];

        // 发券入库（未使用），有效期 = 发放时间 + 15 天
        MktCoupon coupon = new MktCoupon();
        coupon.setUserNum(userNum);
        coupon.setName(prize.name());
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setExpireAt(LocalDateTime.now().plusDays(MktCoupon.VALID_DAYS));
        if (prize.cashAmount() > 0) {
            coupon.setType(2);
            coupon.setAmount(BigDecimal.valueOf(prize.cashAmount()));
            coupon.setState(0);
        } else {
            coupon.setType(1);
            coupon.setDiscountRate(prize.discountRate());
            coupon.setState(0);
        }
        couponRepository.save(coupon);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("prizeIndex", prizeIndex);
        data.put("leftTimes", DAILY_LIMIT - todayCount - 1);
        data.put("coupon", couponVo(coupon));
        return R.ok("抽奖成功", data);
    }

    /**
     * 我的卡券列表（POST /api/coupon/list，可选 state：0 未使用 / 1 已使用）。
     * 返回：records 券数组（name/type/amount/discountRate/state/createdAt/usedAt）。
     */
    @PostMapping("/api/coupon/list")
    public LinkedHashMap<String, Object> list(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userNum = userContext.userNum(request);
        if (userNum == null || userNum.isBlank() || "null".equals(userNum)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        Integer state = body.get("state") != null
                ? Integer.parseInt(String.valueOf(body.get("state"))) : null;
        List<MktCoupon> all = couponRepository.findByUserNumOrderByCreatedAtDesc(userNum);
        List<Map<String, Object>> records = new ArrayList<>();
        for (MktCoupon c : all) {
            if (state != null && !state.equals(c.getState())) continue;
            records.add(couponVo(c));
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /**
     * 我的未使用卡券（POST /api/coupon/usable）：下单选券时展示，全部无门槛可用。
     * 返回：records 券数组（id 用于下单时传入 couponId）。
     */
    @PostMapping("/api/coupon/usable")
    public LinkedHashMap<String, Object> usable(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userNum = userContext.userNum(request);
        if (userNum == null || userNum.isBlank() || "null".equals(userNum)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (MktCoupon c : couponRepository.findByUserNumAndState(userNum, 0)) {
            if (c.isExpired()) continue; // 过期券不下发到选券列表
            records.add(couponVo(c));
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /** 券信息 VO（统一字段输出） */
    static Map<String, Object> couponVo(MktCoupon c) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", c.getId());
        vo.put("name", c.getName());
        vo.put("type", c.getType());
        vo.put("amount", c.getAmount() == null ? "0" : c.getAmount().toPlainString());
        vo.put("discountRate", c.getDiscountRate() == null ? "1" : c.getDiscountRate().toPlainString());
        vo.put("state", c.getState());
        vo.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
        vo.put("usedAt", c.getUsedAt() == null ? "" : c.getUsedAt().toString());
        vo.put("usedOrderNum", c.getUsedOrderNum() == null ? "" : c.getUsedOrderNum());
        vo.put("expireAt", c.getExpireAt() == null ? "" : c.getExpireAt().toString());
        vo.put("expired", c.isExpired());
        return vo;
    }
}
