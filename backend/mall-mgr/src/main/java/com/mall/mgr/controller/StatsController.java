package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.BizOrder;
import com.mall.mgr.entity.Merchant;
import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.RefundOrder;
import com.mall.mgr.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计接口（mall-mgr 平台管理服务，指导书 4.6）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/statis
 * <p>统计口径：支付订单 orderState 为 0=成功 或 2=已退款 视为有效交易额。
 * 接口清单：
 * - POST /statis/platform  平台交易统计（总额、平台利润、渠道占比、七日走势、基础费率）
 * - POST /statis/mch       商户交易统计（今日/本月收入、成功率、待发货/退款中、渠道交易、七日走势）
 */
@RestController
@RequiredArgsConstructor
public class StatsController {

    private final PayOrderRepository payOrderRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final BizOrderRepository bizOrderRepository;
    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final com.mall.mgr.repository.PayChannelRepository payChannelRepository;
    private final UserContext userContext;

    private static final DateTimeFormatter D8 = DateTimeFormatter.ofPattern("MM-dd");

    /** 4.6.1 平台交易统计（days=趋势/占比的时间范围天数，默认 7） */
    @PostMapping("/statis/platform")
    public LinkedHashMap<String, Object> platform(@org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Object> body) {
        int days = 7;
        if (body != null && body.get("days") != null) {
            try { days = Math.max(1, Integer.parseInt(String.valueOf(body.get("days")))); } catch (NumberFormatException ignored) { }
        }
        List<PayOrder> success = payOrderRepository.findAll().stream()
                .filter(p -> p.getOrderState() != null && (p.getOrderState() == 0 || p.getOrderState() == 2))
                .collect(Collectors.toList());

        // 支付方式占比按所选时间范围过滤（无付款时间的归入范围外）
        LocalDateTime rangeStart = LocalDateTime.of(LocalDate.now().minusDays(days - 1), LocalTime.MIN);
        List<PayOrder> ranged = success.stream()
                .filter(p -> p.getPayTime() != null && p.getPayTime().isAfter(rangeStart))
                .collect(Collectors.toList());

        BigDecimal totalMoney = success.stream()
                .map(p -> p.getMoney() == null ? BigDecimal.ZERO : p.getMoney())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal platformProfit = success.stream()
                .map(p -> p.getFee() == null ? BigDecimal.ZERO : p.getFee())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 渠道占比（范围内）
        Map<String, List<PayOrder>> byWay = ranged.stream()
                .collect(Collectors.groupingBy(p -> p.getWayCode() == null ? "OTHER" : p.getWayCode()));
        List<Map<String, Object>> channelRatio = new ArrayList<>();
        byWay.forEach((way, list) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("wayCode", PayController.cnWay(way));
            BigDecimal money = list.stream().map(p -> p.getMoney() == null ? BigDecimal.ZERO : p.getMoney())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal rangeMoney = ranged.stream().map(p -> p.getMoney() == null ? BigDecimal.ZERO : p.getMoney())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String ratio = rangeMoney.signum() == 0 ? "0%" :
                    money.multiply(new BigDecimal("100")).divide(rangeMoney, 1, RoundingMode.HALF_UP) + "%";
            item.put("ratio", ratio);
            item.put("count", list.size());
            channelRatio.add(item);
        });

        // 走势（收入/退款）：按所选天数（收入=全量成功单按日累计，超出范围自动不进 key）
        Map<String, BigDecimal> incomeByDay = new LinkedHashMap<>();
        Map<String, BigDecimal> refundByDay = new LinkedHashMap<>();
        List<String> dates = new ArrayList<>();
        List<String> isoKeys = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            dates.add(d.format(D8));
            isoKeys.add(d.toString());
            incomeByDay.put(d.toString(), BigDecimal.ZERO);
            refundByDay.put(d.toString(), BigDecimal.ZERO);
        }
        for (PayOrder p : success) {
            if (p.getPayTime() == null) continue;
            String key = p.getPayTime().toLocalDate().toString();
            incomeByDay.computeIfPresent(key, (k, v) -> v.add(p.getMoney() == null ? BigDecimal.ZERO : p.getMoney()));
        }
        LocalDateTime rangeStartTime = LocalDateTime.of(today.minusDays(days - 1), LocalTime.MIN);
        for (RefundOrder r : refundOrderRepository.findByRefundState(1)) {
            if (r.getRefundTime() == null || r.getRefundTime().isBefore(rangeStartTime)) continue;
            String key = r.getRefundTime().toLocalDate().toString();
            refundByDay.computeIfPresent(key, (k, v) -> v.add(r.getRefundMoney() == null ? BigDecimal.ZERO : r.getRefundMoney()));
        }

        LinkedHashMap<String, Object> resp = R.ok("统计成功");
        resp.put("totalMoney", totalMoney.toPlainString());
        resp.put("totalCount", success.size());
        resp.put("platformProfit", platformProfit.toPlainString());
        resp.put("channelRatio", channelRatio);
        // 各渠道基础费率（来自渠道配置）
        resp.put("wechatRate", channelRate("WECHAT"));
        resp.put("alipayRate", channelRate("ALIPAY"));
        resp.put("unionpayRate", channelRate("UNIONPAY"));
        resp.put("mchRate", avgMerchantRate());
        Map<String, Object> seven = new LinkedHashMap<>();
        seven.put("dates", dates);
        seven.put("income", isoKeys.stream().map(d -> incomeByDay.getOrDefault(d, BigDecimal.ZERO).toPlainString()).collect(Collectors.toList()));
        seven.put("refund", isoKeys.stream().map(d -> refundByDay.getOrDefault(d, BigDecimal.ZERO).toPlainString()).collect(Collectors.toList()));
        resp.put("sevenDayData", seven);
        return resp;
    }

    /** 4.6.2 商户交易统计（商户号一律取登录态，请求体 shopNum 不采信，防止越权查看他人经营数据） */
    @PostMapping("/statis/mch")
    public LinkedHashMap<String, Object> mch(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = userContext.userNum(request);
        if (shopNum == null || shopNum.isBlank() || "null".equals(shopNum)) throw new BizException("请先登录商户账号");
        Merchant m = merchantRepository.findById(shopNum).orElseThrow(() -> new BizException("商户不存在"));

        LocalDateTime start = LocalDateTime.of(LocalDate.now().minusDays(6), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now();
        List<PayOrder> mchSuccess = payOrderRepository.findByShopNumAndPayTimeBetween(shopNum, start, end).stream()
                .filter(p -> p.getOrderState() != null && (p.getOrderState() == 0 || p.getOrderState() == 2))
                .collect(Collectors.toList());

        // 今日
        LocalDateTime dayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        List<PayOrder> today = mchSuccess.stream().filter(p -> p.getPayTime().isAfter(dayStart)).collect(Collectors.toList());
        BigDecimal todayIncome = today.stream().map(p -> p.getMoney() == null ? BigDecimal.ZERO : p.getMoney())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 本月
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);
        BigDecimal monthIncome = mchSuccess.stream().filter(p -> p.getPayTime().isAfter(monthStart))
                .map(p -> p.getMoney() == null ? BigDecimal.ZERO : p.getMoney())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 渠道交易
        Map<String, List<PayOrder>> byWay = mchSuccess.stream()
                .collect(Collectors.groupingBy(p -> p.getWayCode() == null ? "OTHER" : p.getWayCode()));
        List<Map<String, Object>> channelTradeData = new ArrayList<>();
        byWay.forEach((way, list) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("wayCode", PayController.cnWay(way));
            item.put("tradeMoney", list.stream().map(p -> p.getMoney() == null ? BigDecimal.ZERO : p.getMoney())
                    .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString());
            item.put("tradeCount", list.size());
            channelTradeData.add(item);
        });

        // 七日走势（周几）：isoKeys 对账、days 展示
        Map<String, BigDecimal> byDay = new LinkedHashMap<>();
        List<String> days = new ArrayList<>();
        List<String> isoKeys = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            days.add(d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA));
            isoKeys.add(d.toString());
            byDay.put(d.toString(), BigDecimal.ZERO);
        }
        for (PayOrder p : mchSuccess) {
            String key = p.getPayTime().toLocalDate().toString();
            byDay.computeIfPresent(key, (k, v) -> v.add(p.getMoney() == null ? BigDecimal.ZERO : p.getMoney()));
        }

        // 失败/总数 → 成功率
        List<PayOrder> allMch = payOrderRepository.findAll().stream()
                .filter(p -> shopNum.equals(p.getShopNum())).collect(Collectors.toList());
        long doneCount = allMch.stream().filter(p -> p.getOrderState() != null && p.getOrderState() >= 0 && p.getOrderState() != 3).count();
        long successCount = allMch.stream().filter(p -> p.getOrderState() != null && (p.getOrderState() == 0 || p.getOrderState() == 2)).count();
        String successRate = doneCount == 0 ? "100%" :
                new BigDecimal(successCount * 100).divide(new BigDecimal(doneCount), 1, RoundingMode.HALF_UP) + "%";

        LinkedHashMap<String, Object> resp = R.ok("统计查询成功");
        resp.put("todayIncome", todayIncome.toPlainString());
        resp.put("todayOrders", today.size());
        resp.put("monthIncome", monthIncome.toPlainString());
        resp.put("totalIncome", m.getTotalIncome() == null ? "0" : m.getTotalIncome().toPlainString());
        resp.put("totalServiceFee", m.getTotalFee() == null ? "0" : m.getTotalFee().toPlainString());
        resp.put("actualIncome", m.getBalance() == null ? "0" : m.getBalance().toPlainString());
        resp.put("totalTradeCount", successCount);
        resp.put("successRate", successRate);
        resp.put("pendingShip", bizOrderRepository.findByShopNumAndState(shopNum, 1).size());
        resp.put("refunding", refundOrderRepository.findByShopNum(shopNum).stream()
                .filter(r -> r.getRefundState() != null && r.getRefundState() == 0).count());
        resp.put("goodsCount", productRepository.findByShopNum(shopNum).size());
        resp.put("channelTradeData", channelTradeData);
        Map<String, Object> seven = new LinkedHashMap<>();
        seven.put("days", days);
        seven.put("values", isoKeys.stream().map(d -> byDay.getOrDefault(d, BigDecimal.ZERO).toPlainString()).collect(Collectors.toList()));
        resp.put("sevenDayTradeData", seven);
        return resp;
    }

    /** 渠道基础费率展示（如 "0.3%"） */
    private String channelRate(String wayCode) {
        return payChannelRepository.findById(wayCode)
                .map(c -> c.getRate() == null ? "--" : c.getRate() + "%")
                .orElse("--");
    }

    /** 商户平均差异化费率 */
    private String avgMerchantRate() {
        List<Merchant> mchs = merchantRepository.findAll().stream()
                .filter(m -> m.getMchRate() != null && !m.getMchRate().isBlank())
                .collect(Collectors.toList());
        if (mchs.isEmpty()) return "--";
        BigDecimal sum = mchs.stream()
                .map(m -> new BigDecimal(m.getMchRate().replace("%", "")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(mchs.size()), 2, RoundingMode.HALF_UP) + "%";
    }
}
