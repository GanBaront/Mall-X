package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.*;
import com.mall.mgr.repository.*;
import com.mall.mgr.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商户后台接口：商品管理、发货、消息、资金提现、店铺信息（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/api/mch
 * <p>商户通过网关透传的 shopNum（或请求体 shopNum）标识身份，所有写操作均校验归属，杜绝越权操作其他商户数据。
 * 接口清单：/api/mch/goods、/api/mch/goods/save、/api/mch/ship、/api/mch/notices、
 * /api/mch/funds、/api/mch/withdraw、/api/mch/shop、/api/mch/shop/save。
 */
@RestController
@RequiredArgsConstructor
public class MerchantController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;
    private final BizOrderRepository bizOrderRepository;
    private final MchNoticeRepository mchNoticeRepository;
    private final PayTransferRepository payTransferRepository;
    private final RefundService refundService;
    private final UserContext userContext;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 商户商品列表 */
    /** 商户商品列表（keyword 商品名称/描述模糊筛选由后端完成） */
    @PostMapping("/api/mch/goods")
    public LinkedHashMap<String, Object> goods(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        String keyword = PayController.str(body.get("keyword"));
        List<Map<String, Object>> records = new ArrayList<>();
        for (Product p : productRepository.findByShopNum(shopNum)) {
            if (keyword != null
                    && (p.getName() == null || !p.getName().contains(keyword))
                    && (p.getDescr() == null || !p.getDescr().contains(keyword))) {
                continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", p.getId());
            vo.put("name", p.getName());
            vo.put("image", p.getImage());
            vo.put("price", p.getPrice() == null ? "0" : p.getPrice().toPlainString());
            vo.put("stock", p.getStock());
            vo.put("sales", p.getSales());
            vo.put("category", p.getCategory());
            vo.put("state", p.getState());
            vo.put("desc", p.getDescr());
            records.add(vo);
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /** 商品新增/编辑/上下架 */
    @PostMapping("/api/mch/goods/save")
    public LinkedHashMap<String, Object> saveGoods(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        Product p;
        if (body.get("id") != null && !String.valueOf(body.get("id")).isEmpty()) {
            p = productRepository.findById(Long.parseLong(String.valueOf(body.get("id"))))
                    .orElseThrow(() -> new BizException("商品不存在"));
            if (!shopNum.equals(p.getShopNum())) throw new BizException("无权操作其他商户商品");
        } else {
            p = new Product();
            p.setShopNum(shopNum);
            p.setSales(0);
            p.setCreatedAt(LocalDateTime.now());
            p.setState(1);
        }
        if (body.get("name") != null) p.setName(PayController.str(body.get("name")));
        if (body.get("price") != null) {
            try {
                java.math.BigDecimal price = new java.math.BigDecimal(String.valueOf(body.get("price")));
                if (price.compareTo(java.math.BigDecimal.ZERO) <= 0) throw new BizException("商品价格必须大于 0");
                p.setPrice(price);
            } catch (BizException e) {
                throw e;
            } catch (NumberFormatException e) {
                throw new BizException("商品价格格式无效");
            }
        }
        if (body.get("stock") != null) {
            try {
                int stock = Integer.parseInt(String.valueOf(body.get("stock")));
                if (stock < 0) throw new BizException("库存不能为负数");
                p.setStock(stock);
            } catch (BizException e) {
                throw e;
            } catch (NumberFormatException e) {
                throw new BizException("库存格式无效");
            }
        }
        // 商品分类必须来自平台类目树（pms_category，与管理员端「商品类目」同源），防止随意填写导致前端分类页对不上
        if (body.get("category") != null) {
            String cat = PayController.str(body.get("category"));
            if (cat == null || categoryRepository.findByName(cat).isEmpty()) {
                throw new BizException("商品分类无效，请重新选择");
            }
            p.setCategory(cat);
        }
        if (p.getCategory() == null || p.getCategory().isBlank()) throw new BizException("请选择商品分类");
        if (body.get("state") != null) p.setState(Integer.parseInt(String.valueOf(body.get("state"))));
        if (body.get("desc") != null) p.setDescr(PayController.str(body.get("desc")));
        if (body.get("descr") != null) p.setDescr(PayController.str(body.get("descr")));
        if (body.get("image") != null) p.setImage(PayController.str(body.get("image")));
        productRepository.save(p);
        return R.ok("商品保存成功");
    }

    /**
     * 商品删除（POST /api/mch/goods/delete，body 传 { id }）。
     * 校验商品归属当前商户后物理删除（课程演示环境，无历史订单统计依赖商品存在）。
     */
    @PostMapping("/api/mch/goods/delete")
    public LinkedHashMap<String, Object> deleteGoods(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        if (body.get("id") == null || String.valueOf(body.get("id")).isEmpty()) {
            throw new BizException("缺少参数：id");
        }
        Product p = productRepository.findById(Long.parseLong(String.valueOf(body.get("id"))))
                .orElseThrow(() -> new BizException("商品不存在"));
        if (!shopNum.equals(p.getShopNum())) throw new BizException("无权删除其他商户商品");
        productRepository.delete(p);
        return R.ok("商品已删除");
    }

    /** 订单发货：1-待发货 → 2-待收货 */
    @PostMapping("/api/mch/ship")
    public LinkedHashMap<String, Object> ship(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        String orderNum = PayController.str(body.get("orderNum"));
        String logisticsNo = PayController.str(body.get("logisticsNo"));
        if (orderNum == null || logisticsNo == null) throw new BizException("请填写订单号和物流单号");
        BizOrder biz = bizOrderRepository.findById(orderNum).orElseThrow(() -> new BizException("订单不存在"));
        if (!shopNum.equals(biz.getShopNum())) throw new BizException("无权操作其他商户订单");
        if (biz.getState() != 1) throw new BizException("仅待发货订单可发货");
        biz.setState(2);
        biz.setLogisticsNo(logisticsNo);
        biz.setShipTime(LocalDateTime.now()); // 记录发货时间（订单详情展示用）
        biz.setUpdatedAt(LocalDateTime.now());
        bizOrderRepository.save(biz);
        return R.ok("发货成功");
    }

    /** 商户消息通知 */
    @PostMapping("/api/mch/notices")
    public LinkedHashMap<String, Object> notices(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        List<Map<String, Object>> records = new ArrayList<>();
        for (MchNotice n : mchNoticeRepository.findByShopNumOrderByCreatedAtDesc(shopNum)) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", n.getId());
            vo.put("title", n.getTitle());
            vo.put("content", n.getContent());
            vo.put("type", n.getNoticeType());
            vo.put("unread", n.getState() != null && n.getState() == 0);
            vo.put("time", n.getCreatedAt() == null ? "" : n.getCreatedAt().format(DT));
            records.add(vo);
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /**
     * 标记消息已读（POST /api/mch/notice/read）
     * <p>body 传 { id } 标记单条（校验消息归属当前商户），或 { all: true } 全部已读；
     * 已读状态落库（mch_notice.state=1），刷新/重新登录不回退。
     */
    @PostMapping("/api/mch/notice/read")
    public LinkedHashMap<String, Object> markNoticeRead(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        int marked = 0;
        if (Boolean.parseBoolean(String.valueOf(body.get("all")))) {
            for (MchNotice n : mchNoticeRepository.findByShopNumOrderByCreatedAtDesc(shopNum)) {
                if (n.getState() == null || n.getState() == 0) {
                    n.setState(1);
                    mchNoticeRepository.save(n);
                    marked++;
                }
            }
        } else if (body.get("id") != null) {
            Long id = Long.parseLong(String.valueOf(body.get("id")));
            MchNotice n = mchNoticeRepository.findById(id)
                    .orElseThrow(() -> new BizException("消息不存在"));
            if (!shopNum.equals(n.getShopNum())) throw new BizException("无权操作该消息");
            if (n.getState() == null || n.getState() == 0) {
                n.setState(1);
                mchNoticeRepository.save(n);
            }
            marked = 1;
        } else {
            throw new BizException("缺少参数：id 或 all");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("marked", marked);
        return R.ok("已标记为已读", data);
    }

    /** 资金总览 + 提现记录 */
    @PostMapping("/api/mch/funds")
    public LinkedHashMap<String, Object> funds(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        Merchant m = merchantRepository.findById(shopNum).orElseThrow(() -> new BizException("商户不存在"));
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("balance", m.getBalance() == null ? "0" : m.getBalance().toPlainString());
        resp.put("totalIncome", m.getTotalIncome() == null ? "0" : m.getTotalIncome().toPlainString());
        resp.put("totalServiceFee", m.getTotalFee() == null ? "0" : m.getTotalFee().toPlainString());
        resp.put("withdrawn", m.getWithdrawn() == null ? "0" : m.getWithdrawn().toPlainString());
        List<Map<String, Object>> records = new ArrayList<>();
        for (PayTransfer t : payTransferRepository.findByShopNumOrderByCreatedAtDesc(shopNum)) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", t.getTransferId());
            vo.put("amount", t.getAmount() == null ? "0" : t.getAmount().toPlainString());
            vo.put("account", t.getEntryAccount());
            vo.put("state", t.getState());
            vo.put("time", t.getCreatedAt() == null ? "" : t.getCreatedAt().format(DT));
            records.add(vo);
        }
        resp.put("records", records);
        return resp;
    }

    /**
     * 申请提现。
     * 事务内完成「转账单落库 + 余额原子扣减」：余额扣减用数据库条件更新
     * （balance >= amount 才生效），防止并发两笔提现同时读到原余额造成双花；
     * 两笔写操作同一事务，避免崩溃时转账单已存而余额未扣。
     */
    @PostMapping("/api/mch/withdraw")
    @org.springframework.transaction.annotation.Transactional
    public LinkedHashMap<String, Object> withdraw(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(body.get("amount")));
        } catch (NumberFormatException e) {
            throw new BizException("提现金额格式无效");
        }
        String account = PayController.str(body.get("account"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("提现金额必须大于 0");
        if (amount.compareTo(new BigDecimal("1")) < 0) throw new BizException("单笔提现最低 1.00 元");
        Merchant m = merchantRepository.findById(shopNum).orElseThrow(() -> new BizException("商户不存在"));

        // 余额条件原子扣减：balance >= amount 才生效，并发双花在数据库层被拒绝
        int updated = merchantRepository.withdrawIfEnough(shopNum, amount.setScale(2, java.math.RoundingMode.HALF_UP));
        if (updated == 0) throw new BizException("可提现余额不足");

        PayTransfer t = new PayTransfer();
        // 单号加 4 位随机后缀：纯毫秒时间戳并发同毫秒会主键冲突
        t.setTransferId("T" + System.currentTimeMillis() + String.format("%04d", new java.util.Random().nextInt(10000)));
        t.setShopNum(shopNum);
        t.setShopName(m.getShopName());
        t.setEntryAccount(account == null ? "余额账户" : account);
        t.setEntryName(m.getShopName());
        t.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        t.setState(0);
        t.setCreatedAt(LocalDateTime.now());
        payTransferRepository.save(t);
        return R.ok("提现申请已提交，预计 1-3 个工作日到账");
    }

    /** 店铺信息 */
    @PostMapping("/api/mch/shop")
    public LinkedHashMap<String, Object> shopInfo(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        Merchant m = merchantRepository.findById(shopNum).orElseThrow(() -> new BizException("商户不存在"));
        Map<String, Object> vo = new HashMap<>();
        vo.put("shopNum", m.getShopNum());
        vo.put("shopName", m.getShopName());
        vo.put("shopPhone", m.getShopPhone());
        vo.put("shopAddress", m.getShopAddress());
        vo.put("shopDesc", m.getShopDesc());
        vo.put("shopState", m.getShopState());
        vo.put("mchRate", m.getMchRate() == null ? "" : m.getMchRate() + "%");
        vo.put("applyTime", m.getApplyTime() == null ? "" : m.getApplyTime().format(DT));
        vo.put("createdAt", m.getCreatedAt() == null ? "" : m.getCreatedAt().format(DT));
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("record", vo);
        return resp;
    }

    /** 保存店铺信息 */
    @PostMapping("/api/mch/shop/save")
    public LinkedHashMap<String, Object> saveShop(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String shopNum = shopNum(body, request);
        Merchant m = merchantRepository.findById(shopNum).orElseThrow(() -> new BizException("商户不存在"));
        if (body.get("shopName") != null) m.setShopName(PayController.str(body.get("shopName")));
        if (body.get("shopPhone") != null) m.setShopPhone(PayController.str(body.get("shopPhone")));
        if (body.get("shopAddress") != null) m.setShopAddress(PayController.str(body.get("shopAddress")));
        if (body.get("shopDesc") != null) m.setShopDesc(PayController.str(body.get("shopDesc")));
        merchantRepository.save(m);
        return R.ok("店铺信息已保存");
    }

    /**
     * 商户身份解析：一律以网关透传的登录态（X-Mall-User-Num，商户登录时即 shopNum）为准，
     * 请求体 shopNum 不再采信——防止任意登录用户传他人 shopNum 越权操作他人店铺。
     */
    private String shopNum(Map<String, Object> body, HttpServletRequest request) {
        String shopNum = userContext.userNum(request);
        if (shopNum == null || shopNum.isBlank() || "null".equals(shopNum)) {
            throw new BizException("请先登录商户账号");
        }
        return shopNum;
    }
}
