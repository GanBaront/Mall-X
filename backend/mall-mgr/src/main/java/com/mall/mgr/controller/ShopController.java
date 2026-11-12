package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.BizOrder;
import com.mall.mgr.entity.Category;
import com.mall.mgr.entity.Merchant;
import com.mall.mgr.entity.PayOrder;
import com.mall.mgr.entity.Product;
import com.mall.mgr.entity.RefundOrder;
import com.mall.mgr.repository.BizOrderRepository;
import com.mall.mgr.repository.CategoryRepository;
import com.mall.mgr.repository.MerchantRepository;
import com.mall.mgr.repository.PayOrderRepository;
import com.mall.mgr.repository.ProductRepository;
import com.mall.mgr.repository.RefundOrderRepository;
import com.mall.mgr.service.PayService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户购物接口：商品浏览、我的订单、确认收货、取消订单（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/api
 * <p>用户身份由网关透传的 userNum（或请求体 userNum）确定。购物端订单状态：
 * 0-待支付、1-待发货、2-待收货、3-已完成、4-已关闭。
 * 接口清单：/api/products、/api/products/detail、/api/orders、/api/order/detail、/api/order/confirm、/api/order/cancel。
 */
@RestController
@RequiredArgsConstructor
public class ShopController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BizOrderRepository bizOrderRepository;
    private final MerchantRepository merchantRepository;
    private final PayOrderRepository payOrderRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final PayService payService;
    private final UserContext userContext;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 商品列表（数据库分页版，指导书商品浏览）
     * <p>category=全部 时查所有在售商品；category 为类目树节点名时按「该节点 + 全部子孙类目」过滤
     * （与管理员端类目管理同源，选一级类目可带出其下所有二级类目商品）；
     * keyword 后端模糊匹配名称/描述；
     * pageNum/pageSize 分页参数（默认第 1 页、每页 20 条，上限 100），按销量倒序。
     * 返回：records（当前页商品）、total（总数）、pages（总页数），并包装为统一响应。
     */
    @PostMapping("/api/products")
    public LinkedHashMap<String, Object> products(@RequestBody Map<String, Object> body) {
        String category = str(body.get("category"));
        String keyword = str(body.get("keyword"));
        int pageNum = body.get("pageNum") != null ? Integer.parseInt(String.valueOf(body.get("pageNum"))) : 1;
        int pageSize = body.get("pageSize") != null ? Integer.parseInt(String.valueOf(body.get("pageSize"))) : 20;
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        // 类目子树展开：把请求里的单个类目名展开为「该节点 + 全部子孙类目名」集合
        Set<String> categoryNames = category == null || "全部".equals(category) ? null : expandCategoryNames(category);
        // 组合条件：在售 + 可选分类（子树） + 可选关键词（名称/描述模糊），全部在数据库层过滤分页
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("state"), 1));
            if (categoryNames != null) {
                // 类目不存在（空集合）时直接匹配空结果，避免退化为全量查询
                ps.add(categoryNames.isEmpty() ? cb.disjunction() : root.get("category").in(categoryNames));
            }
            if (keyword != null) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(cb.like(root.get("name"), like), cb.like(cb.coalesce(root.get("descr"), ""), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Product> page = productRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "sales")));
        List<Map<String, Object>> records = new ArrayList<>();
        for (Product p : page.getContent()) {
            records.add(toVo(p));
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        resp.put("total", page.getTotalElements());
        resp.put("pages", page.getTotalPages());
        return resp;
    }

    /**
     * 类目子树展开：按类目名找到节点（可能为一级或二级），收集该节点及其全部子孙类目的名称集合。
     * 名称存的是类目名（pms_product.category 为名称），同一名称可能存在多条类目记录，全部纳入。
     *
     * @param categoryName 请求传入的类目名（一级或二级类目）
     * @return 该类目及其子孙类目名集合；类目不存在时返回空集合
     */
    private Set<String> expandCategoryNames(String categoryName) {
        Set<String> names = new HashSet<>();
        List<Category> matches = categoryRepository.findByName(categoryName);
        for (Category start : matches) {
            collectNames(start, names);
        }
        return names;
    }

    /** 递归收集一个类目节点及其全部子孙类目的名称 */
    private void collectNames(Category node, Set<String> names) {
        names.add(node.getName());
        for (Category child : categoryRepository.findByParentId(node.getId())) {
            collectNames(child, names);
        }
    }

    /** 商品详情 */
    @PostMapping("/api/products/detail")
    public LinkedHashMap<String, Object> productDetail(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.parseLong(String.valueOf(body.get("id"))) : null;
        if (id == null) throw new BizException("缺少参数：id");
        Product p = productRepository.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("record", toVo(p));
        return resp;
    }

    /** 我的订单列表（userNum 一律以网关透传登录态为准，请求体 userNum 不采信，防越权查询他人订单） */
    @PostMapping("/api/orders")
    public LinkedHashMap<String, Object> myOrders(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userNum = userContext.userNum(request);
        if (userNum == null || userNum.isBlank() || "null".equals(userNum)) throw new BizException("请先登录");
        Integer state = body.get("state") != null ? Integer.parseInt(String.valueOf(body.get("state"))) : null;
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizOrder o : bizOrderRepository.findByUserNumOrderByCreatedAtDesc(userNum)) {
            if (state != null && !state.equals(o.getState())) continue;
            records.add(orderVo(o));
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /**
     * 确认收货：2-待收货 → 3-已完成；货款此时才结算入账商户（应收/营业额在此刻增加）。
     * 安全校验：仅订单本人可确认；状态流转用数据库条件更新保证幂等，防止并发重复入账。
     */
    @PostMapping("/api/order/confirm")
    @org.springframework.transaction.annotation.Transactional
    public LinkedHashMap<String, Object> confirm(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        BizOrder biz = findOrder(body);
        String userNum = userContext.userNum(request);
        if (userNum == null || !userNum.equals(biz.getUserNum())) throw new BizException("无权操作该订单");
        // 条件原子更新：仅当仍为 2-待收货 时置为 3-已完成（并发重复请求第二次影响行数为 0，直接拒绝）
        int updated = bizOrderRepository.confirmReceipt(biz.getOrderNum(), LocalDateTime.now());
        if (updated == 0) throw new BizException("当前订单状态不可确认收货");
        biz.setState(3);
        biz.setFinishTime(LocalDateTime.now()); // 记录成交时间（订单详情展示用）
        biz.setUpdatedAt(LocalDateTime.now());
        // 货款结算：仅支付成功且未退款的订单，累计营收 +实付、可提现余额 +实付-手续费、累计手续费 +手续费
        payOrderRepository.findById(biz.getPayOrderId()).ifPresent(po -> {
            if (po.getOrderState() != null && po.getOrderState() == 0) {
                merchantRepository.findById(biz.getShopNum()).ifPresent(m -> {
                    m.setTotalIncome(nvl(m.getTotalIncome()).add(nvl(po.getMoney())));
                    m.setBalance(nvl(m.getBalance()).add(nvl(po.getMoney())).subtract(nvl(po.getFee())));
                    m.setTotalFee(nvl(m.getTotalFee()).add(nvl(po.getFee())));
                    merchantRepository.save(m);
                });
            }
        });
        return R.ok("确认收货成功");
    }

    /**
     * 订单详情：履约单 + 支付单合并信息，供移动端「订单详情页」展示。
     * 仅订单本人可查（userNum 一律以网关透传登录态为准，请求体值不采信）。
     * 返回字段：商品/金额/状态、店铺名、收货人与地址、物流单号，
     * 以及支付单补充信息（渠道交易号、付款时间、支付状态）和三个流转时间
     * （付款时间 payTime、发货时间 shipTime、成交时间 finishTime，未发生为空串）。
     */
    @PostMapping("/api/order/detail")
    public LinkedHashMap<String, Object> orderDetail(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        BizOrder biz = findOrder(body);
        String userNum = userContext.userNum(request);
        if (userNum == null || !userNum.equals(biz.getUserNum())) throw new BizException("无权查看该订单");
        Map<String, Object> vo = orderVo(biz);
        vo.put("buyer", biz.getBuyer() == null ? "" : biz.getBuyer());
        vo.put("productId", biz.getProductId());
        // 店铺名称
        vo.put("shopName", "");
        if (biz.getShopNum() != null) {
            merchantRepository.findById(biz.getShopNum())
                    .ifPresent(m -> vo.put("shopName", m.getShopName()));
        }
        // 支付单补充信息：渠道交易号、支付状态、付款时间
        if (biz.getPayOrderId() != null) {
            PayOrder po = payOrderRepository.findById(biz.getPayOrderId()).orElse(null);
            if (po != null) {
                vo.put("channelOrderNo", po.getChannelOrderNo() == null ? "" : po.getChannelOrderNo());
                vo.put("payOrderState", po.getOrderState());
                vo.put("channelPayTime", po.getPayTime() == null ? "" : po.getPayTime().format(DT));
            }
        }
        // 三个流转时间（未发生为空串，前端展示为「-」）
        vo.put("payTime", biz.getPayTime() == null ? "" : biz.getPayTime().format(DT));
        vo.put("shipTime", biz.getShipTime() == null ? "" : biz.getShipTime().format(DT));
        vo.put("finishTime", biz.getFinishTime() == null ? "" : biz.getFinishTime().format(DT));
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("record", vo);
        return resp;
    }

    /**
     * 取消订单：0-待支付 → 4-已关闭（同时关闭支付单，并退回核销的优惠券）。
     * 安全校验：仅订单本人可取消；条件更新保证并发安全；
     * 支付单若仍为待支付(-1)则一并置为关闭(3)，防止取消后仍可支付成功、回调复活订单。
     */
    @PostMapping("/api/order/cancel")
    @org.springframework.transaction.annotation.Transactional
    public LinkedHashMap<String, Object> cancel(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        BizOrder biz = findOrder(body);
        String userNum = userContext.userNum(request);
        if (userNum == null || !userNum.equals(biz.getUserNum())) throw new BizException("无权操作该订单");
        // 与支付回调互斥：同一 payOrderId 串行（回调侧持同一把锁），防止取消与回调并发互相覆盖状态
        if (biz.getPayOrderId() != null) {
            synchronized (payService.lockOf(biz.getPayOrderId())) {
                doCancel(biz);
            }
        } else {
            doCancel(biz);
        }
        return R.ok("订单已取消");
    }

    /** 取消订单实际执行（调用方已持有支付单锁） */
    private void doCancel(BizOrder biz) {
        // 条件原子更新：仅当仍为 0-待支付 时置为 4-已关闭
        int updated = bizOrderRepository.cancelIfUnpaid(biz.getOrderNum(), LocalDateTime.now());
        if (updated == 0) throw new BizException("仅待支付订单可取消");
        // 同步关闭关联支付单（仅待支付单），防止取消后继续支付导致订单复活
        if (biz.getPayOrderId() != null) {
            payOrderRepository.closeIfUnpaidById(biz.getPayOrderId(), LocalDateTime.now());
        }
        // 退回该订单核销的优惠券（恢复为未使用）
        payService.refundCouponByOrder(biz.getOrderNum());
    }

    private BizOrder findOrder(Map<String, Object> body) {
        String orderNum = str(body.get("orderNum"));
        if (orderNum == null) throw new BizException("缺少参数：orderNum");
        return bizOrderRepository.findById(orderNum).orElseThrow(() -> new BizException("订单不存在"));
    }

    private Map<String, Object> toVo(Product p) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", p.getId());
        vo.put("name", p.getName());
        vo.put("price", p.getPrice() == null ? "0" : p.getPrice().toPlainString());
        vo.put("sales", p.getSales());
        vo.put("stock", p.getStock());
        vo.put("category", p.getCategory());
        vo.put("desc", p.getDescr());
        vo.put("image", p.getImage());
        vo.put("shopNum", p.getShopNum());
        merchantRepository.findById(p.getShopNum())
                .ifPresent(m -> vo.put("shopName", m.getShopName()));
        return vo;
    }

    /** 最新一次售后/退款状态：-1=无申请，0=审核中，1=已退款，2=商家拒绝（可重新申请） */
    private int latestRefundState(String payOrderId) {
        int state = -1;
        LocalDateTime latest = null;
        for (RefundOrder r : refundOrderRepository.findByPayOrderId(payOrderId)) {
            if (latest == null || (r.getApplyTime() != null && r.getApplyTime().isAfter(latest))) {
                latest = r.getApplyTime();
                state = r.getRefundState() == null ? -1 : r.getRefundState();
            }
        }
        return state;
    }

    private Map<String, Object> orderVo(BizOrder o) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("orderNum", o.getOrderNum());
        vo.put("payOrderId", o.getPayOrderId());
        vo.put("goodsName", o.getGoodsName());
        vo.put("image", o.getImage());
        vo.put("num", o.getNum());
        vo.put("price", o.getPrice() == null ? "0" : o.getPrice().toPlainString());
        vo.put("total", o.getTotal() == null ? "0" : o.getTotal().toPlainString());
        vo.put("state", o.getState());
        vo.put("wayCode", o.getWayCode());
        vo.put("address", o.getAddress());
        vo.put("logisticsNo", o.getLogisticsNo() == null ? "" : o.getLogisticsNo());
        vo.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().format(DT));
        vo.put("updatedAt", o.getUpdatedAt() == null ? "" : o.getUpdatedAt().format(DT));
        // 售后/退款状态（前端据此显示「申请售后」按钮或「售后中/已退款」标记）
        vo.put("refundState", o.getPayOrderId() == null ? -1 : latestRefundState(o.getPayOrderId()));
        return vo;
    }

    static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** BigDecimal 空值兜底：null 返回 0 */
    static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
