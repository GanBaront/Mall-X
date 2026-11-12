package com.mall.mgr.controller;

import com.mall.mgr.common.PageUtil;
import com.mall.mgr.common.R;
import com.mall.mgr.entity.*;
import com.mall.mgr.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台管理端：商户管理、应用、服务商、通知（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/admin、/api（商户侧通过网关带 shopNum 调用）
 * <p>接口清单：/admin/mch/list、/admin/mch/audit、/admin/mch/rate、/admin/mch/state、
 * /admin/mch/app/list、/admin/isv/list、/admin/mch/notice/list。
 */
@RestController
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantRepository merchantRepository;
    private final PayAppRepository payAppRepository;
    private final IsvRepository isvRepository;
    private final MchNoticeRepository mchNoticeRepository;
    private final SysLogRepository sysLogRepository;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 商户列表（审核状态/商户号/商户名称/关键字筛选由后端完成） */
    @PostMapping("/admin/mch/list")
    public LinkedHashMap<String, Object> mchList(@RequestBody Map<String, Object> params) {
        Integer shopState = params.get("shopState") != null && !String.valueOf(params.get("shopState")).isEmpty()
                ? Integer.parseInt(String.valueOf(params.get("shopState"))) : null;
        String keyword = PayController.str(params.get("keyword"));
        String shopNum = PayController.str(params.get("shopNum"));
        String shopName = PayController.str(params.get("shopName"));
        List<Map<String, Object>> filtered = merchantRepository.findAll().stream()
                .filter(m -> shopState == null || shopState.equals(m.getShopState()))
                .filter(m -> shopNum == null || (m.getShopNum() != null && m.getShopNum().contains(shopNum)))
                .filter(m -> shopName == null || (m.getShopName() != null && m.getShopName().contains(shopName)))
                .filter(m -> matchKeyword(m, keyword))
                .map(this::toVo)
                .collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", filtered.size());
        resp.put("records", PageUtil.paginate(filtered, params, v -> v));
        return resp;
    }

    /** 商户审核（指导书 4.5.1）：shopState 1-通过 2-驳回 */
    @PostMapping("/admin/mch/audit")
    public LinkedHashMap<String, Object> audit(@RequestBody Map<String, Object> body) {
        String shopNum = PayController.str(body.get("shopNum"));
        Integer shopState = body.get("shopState") != null ? Integer.parseInt(String.valueOf(body.get("shopState"))) : null;
        String auditRemark = PayController.str(body.get("auditRemark"));
        if (shopNum == null || shopState == null) throw new com.mall.mgr.common.BizException("缺少参数：shopNum/shopState");
        Merchant m = merchantRepository.findById(shopNum)
                .orElseThrow(() -> new com.mall.mgr.common.BizException("商户不存在"));
        // 状态机流转：shopState=2 驳回，其余（含 1）视为审核通过
        m.setShopState(shopState == 2 ? 2 : 1);
        m.setAuditRemark(auditRemark);
        merchantRepository.save(m);

        // 通知商户审核结果
        MchNotice n = new MchNotice();
        n.setShopNum(shopNum);
        n.setShopName(m.getShopName());
        n.setTitle("入驻审核结果");
        n.setContent(shopState == 1
                ? "恭喜，您的入驻申请已审核通过，可以开始上架商品经营。"
                : "很抱歉，您的入驻申请未通过：" + (auditRemark == null ? "资料不符合要求" : auditRemark));
        n.setNoticeType("审核通知");
        n.setState(0);
        n.setCreatedAt(LocalDateTime.now());
        mchNoticeRepository.save(n);

        saveLog(body, "商户管理", (shopState == 1 ? "审核通过商户「" : "驳回商户「") + m.getShopName() + "」");
        return R.ok("商户审核操作成功");
    }

    /** 商户费率配置（指导书 4.5.3） */
    @PostMapping("/admin/mch/rate")
    public LinkedHashMap<String, Object> rate(@RequestBody Map<String, Object> body) {
        String shopNum = PayController.str(body.get("shopNum"));
        String mchRate = PayController.str(body.get("mchRate"));
        if (shopNum == null || mchRate == null) throw new com.mall.mgr.common.BizException("缺少参数：shopNum/mchRate");
        Merchant m = merchantRepository.findById(shopNum)
                .orElseThrow(() -> new com.mall.mgr.common.BizException("商户不存在"));
        m.setMchRate(mchRate.replace("%", ""));
        merchantRepository.save(m);
        saveLog(body, "商户管理", "配置商户「" + m.getShopName() + "」差异化费率 " + mchRate + "%");
        return R.ok("商户费率配置成功");
    }

    /** 商户启用/禁用 */
    @PostMapping("/admin/mch/state")
    public LinkedHashMap<String, Object> state(@RequestBody Map<String, Object> body) {
        String shopNum = PayController.str(body.get("shopNum"));
        Integer shopState = body.get("shopState") != null ? Integer.parseInt(String.valueOf(body.get("shopState"))) : null;
        Merchant m = merchantRepository.findById(shopNum)
                .orElseThrow(() -> new com.mall.mgr.common.BizException("商户不存在"));
        m.setShopState(shopState);
        merchantRepository.save(m);
        saveLog(body, "商户管理", (shopState == 1 ? "启用" : "禁用") + "商户「" + m.getShopName() + "」");
        return R.ok("商户状态更新成功");
    }

    /** 商户应用列表（商户名称/APPID/状态筛选由后端完成） */
    @PostMapping("/admin/mch/app/list")
    public LinkedHashMap<String, Object> appList(@RequestBody Map<String, Object> params) {
        List<PayApp> all = payAppRepository.findAll();
        String shopNum = PayController.str(params.get("shopNum"));
        String shopName = PayController.str(params.get("shopName"));
        String appId = PayController.str(params.get("appId"));
        Integer state = params.get("state") != null && !String.valueOf(params.get("state")).isEmpty()
                ? Integer.parseInt(String.valueOf(params.get("state"))) : null;
        List<Map<String, Object>> vos = all.stream()
                .filter(a -> shopNum == null || shopNum.equals(a.getShopNum()))
                .filter(a -> appId == null || (a.getAppId() != null && a.getAppId().contains(appId)))
                .filter(a -> state == null || state.equals(a.getState()))
                .map(a -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("appId", a.getAppId());
                    vo.put("appName", a.getAppName());
                    vo.put("shopNum", a.getShopNum());
                    vo.put("shopName", merchantRepository.findById(a.getShopNum())
                            .map(m -> m.getShopName() == null ? "" : m.getShopName()).orElse(""));
                    vo.put("state", a.getState());
                    vo.put("remark", a.getRemark());
                    vo.put("createdAt", a.getCreatedAt() == null ? "" : a.getCreatedAt().format(DT));
                    return vo;
                })
                .collect(Collectors.toList());
        // 商户名称筛选：需要先解析出 shopName 再过滤
        if (shopName != null) {
            vos = vos.stream()
                    .filter(v -> String.valueOf(v.get("shopName")).contains(shopName))
                    .collect(Collectors.toList());
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", vos.size());
        resp.put("records", PageUtil.paginate(vos, params, v -> v));
        return resp;
    }

    /** 服务商列表（名称/编号模糊筛选由后端完成） */
    @PostMapping("/admin/isv/list")
    public LinkedHashMap<String, Object> isvList(@RequestBody Map<String, Object> params) {
        String isvName = PayController.str(params.get("isvName"));
        String isvNum = PayController.str(params.get("isvNum"));
        List<Map<String, Object>> filtered = isvRepository.findAll().stream()
                .filter(i -> isvName == null || (i.getIsvName() != null && i.getIsvName().contains(isvName)))
                .filter(i -> isvNum == null || (i.getIsvNum() != null && i.getIsvNum().contains(isvNum)))
                .map(i -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("isvNum", i.getIsvNum());
                    vo.put("isvName", i.getIsvName());
                    vo.put("linkPhone", i.getLinkPhone());
                    vo.put("contact", i.getContact());
                    vo.put("appId", i.getAppId());
                    vo.put("state", i.getState());
                    vo.put("createdAt", i.getCreatedAt() == null ? "" : i.getCreatedAt().format(DT));
                    return vo;
                }).collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", filtered.size());
        resp.put("records", PageUtil.paginate(filtered, params, v -> v));
        return resp;
    }

    /** 商户通知列表（标题/商户名称/通知类型筛选由后端完成） */
    @PostMapping("/admin/mch/notice/list")
    public LinkedHashMap<String, Object> noticeList(@RequestBody Map<String, Object> params) {
        String title = PayController.str(params.get("title"));
        String shopName = PayController.str(params.get("shopName"));
        String noticeType = PayController.str(params.get("noticeType"));
        List<Map<String, Object>> filtered = mchNoticeRepository.findAll().stream()
                .filter(n -> noticeType == null || noticeType.equals(n.getNoticeType()))
                .map(n -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("id", n.getId());
                    vo.put("title", n.getTitle());
                    vo.put("shopNum", n.getShopNum());
                    vo.put("shopName", n.getShopName() == null ? "全部商户" : n.getShopName());
                    vo.put("content", n.getContent());
                    vo.put("noticeType", n.getNoticeType());
                    vo.put("state", n.getState());
                    vo.put("createdAt", n.getCreatedAt() == null ? "" : n.getCreatedAt().format(DT));
                    return vo;
                })
                .filter(v -> title == null || String.valueOf(v.get("title")).contains(title))
                .filter(v -> shopName == null || String.valueOf(v.get("shopName")).contains(shopName))
                .collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("total", filtered.size());
        resp.put("records", PageUtil.paginate(filtered, params, v -> v));
        return resp;
    }

    private boolean matchKeyword(Merchant m, String keyword) {
        if (keyword == null) return true;
        return (m.getShopName() != null && m.getShopName().contains(keyword))
                || (m.getShopNum() != null && m.getShopNum().contains(keyword))
                || (m.getUsername() != null && m.getUsername().contains(keyword));
    }

    private Map<String, Object> toVo(Merchant m) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("shopNum", m.getShopNum());
        vo.put("shopName", m.getShopName());
        vo.put("shopPhone", m.getShopPhone());
        vo.put("shopAddress", m.getShopAddress());
        vo.put("username", m.getUsername());
        vo.put("shopState", m.getShopState());
        vo.put("auditRemark", m.getAuditRemark());
        vo.put("applyTime", m.getApplyTime() == null ? "" : m.getApplyTime().format(DT));
        vo.put("mchRate", m.getMchRate() == null ? "" : m.getMchRate() + "%");
        vo.put("balance", m.getBalance() == null ? "0" : m.getBalance().toPlainString());
        vo.put("totalIncome", m.getTotalIncome() == null ? "0" : m.getTotalIncome().toPlainString());
        return vo;
    }

    private void saveLog(Map<String, Object> req, String module, String action) {
        SysLog log = new SysLog();
        log.setUsername(PayController.str(req.get("_opUser")) == null ? "admin" : PayController.str(req.get("_opUser")));
        log.setModule(module);
        log.setAction(action);
        log.setIp("");
        log.setResult("成功");
        log.setCreatedAt(LocalDateTime.now());
        sysLogRepository.save(log);
    }
}
