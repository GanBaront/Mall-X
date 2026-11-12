package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.entity.Category;
import com.mall.mgr.entity.PayChannel;
import com.mall.mgr.entity.PayWay;
import com.mall.mgr.entity.SysLog;
import com.mall.mgr.entity.SysUser;
import com.mall.mgr.repository.CategoryRepository;
import com.mall.mgr.repository.PayChannelRepository;
import com.mall.mgr.repository.PayWayRepository;
import com.mall.mgr.repository.SysLogRepository;
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
 * 平台管理端：支付渠道配置（4.5.2）、支付方式、商品类目、系统管理（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/admin
 * <p>接口清单：/admin/channel/list、/admin/channel/config、/admin/pay/way/list、/admin/pay/way/state、
 * /admin/category/tree、/admin/category/save、/admin/category/delete、
 * /admin/sys/user/list、/admin/sys/user/save、/admin/sys/user/state、/admin/sys/user/delete、
 * /admin/sys/role/list、/admin/sys/log/list。
 * 敏感字段（appSecret、apiV3Key）保存时以 “•” 开头视为“不修改”，避免明文回写覆盖。
 */
@RestController
@RequiredArgsConstructor
public class AdminConfigController {

    private final PayChannelRepository payChannelRepository;
    private final PayWayRepository payWayRepository;
    private final CategoryRepository categoryRepository;
    private final SysLogRepository sysLogRepository;
    private final com.mall.mgr.repository.SysUserRepository sysUserRepository;
    private final com.mall.mgr.service.PayService payService;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 渠道配置列表 */
    @PostMapping("/admin/channel/list")
    public LinkedHashMap<String, Object> channelList() {
        List<Map<String, Object>> records = payChannelRepository.findAll().stream()
                .map(c -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("wayCode", c.getWayCode());
                    vo.put("name", c.getName());
                    vo.put("state", c.getState());
                    vo.put("appId", c.getAppId());
                    vo.put("appSecret", c.getAppSecret());
                    vo.put("mchId", c.getMchId());
                    vo.put("apiV3Key", c.getApiV3Key());
                    vo.put("rate", c.getRate());
                    vo.put("remark", c.getRemark());
                    return vo;
                }).collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /** 保存渠道配置（指导书 4.5.2） */
    @PostMapping("/admin/channel/config")
    public LinkedHashMap<String, Object> saveChannel(@RequestBody Map<String, Object> body) {
        String wayCode = PayController.str(body.get("wayCode"));
        if (wayCode == null) throw new BizException("缺少参数：wayCode");
        PayChannel c = payChannelRepository.findById(wayCode).orElseGet(() -> {
            PayChannel nc = new PayChannel();
            nc.setWayCode(wayCode);
            return nc;
        });
        if (body.get("name") != null) c.setName(PayController.str(body.get("name")));
        if (body.get("state") != null) c.setState(Integer.parseInt(String.valueOf(body.get("state"))));
        if (body.get("appId") != null) c.setAppId(PayController.str(body.get("appId")));
        if (body.get("appSecret") != null && !String.valueOf(body.get("appSecret")).startsWith("•")) {
            c.setAppSecret(PayController.str(body.get("appSecret")));
        }
        if (body.get("mchId") != null) c.setMchId(PayController.str(body.get("mchId")));
        if (body.get("apiV3Key") != null && !String.valueOf(body.get("apiV3Key")).startsWith("•")) {
            c.setApiV3Key(PayController.str(body.get("apiV3Key")));
        }
        if (body.get("rate") != null) {
            c.setRate(PayController.str(body.get("rate")).replace("%", ""));
        }
        if (body.get("remark") != null) c.setRemark(PayController.str(body.get("remark")));
        payChannelRepository.save(c);
        saveLog(body, "支付配置", "保存支付渠道「" + c.getName() + "」配置");
        return R.ok("渠道配置保存成功");
    }

    /** 支付方式列表（含所属支付接口状态与综合可用状态，供「支付方式」页展示联动关系） */
    @PostMapping("/admin/pay/way/list")
    public LinkedHashMap<String, Object> payWayList() {
        List<Map<String, Object>> records = payWayRepository.findAll().stream()
                .map(w -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("wayCode", w.getWayCode());
                    vo.put("name", w.getName());
                    vo.put("wayType", w.getWayType());
                    vo.put("state", w.getState());
                    vo.put("rate", w.getRate());
                    vo.put("remark", w.getRemark());
                    // 所属支付接口（渠道）：接口停用则该支付方式整体不可用
                    PayChannel ch = payService.channelOf(w.getWayCode());
                    vo.put("channelCode", ch == null ? "" : ch.getWayCode());
                    vo.put("channelName", ch == null ? "未配置" : ch.getName());
                    vo.put("channelState", ch == null ? null : ch.getState());
                    vo.put("usable", payService.isWayEnabled(w.getWayCode()));
                    return vo;
                }).collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /**
     * 启用/停用支付方式（POST /admin/pay/way/state）。
     * 关键参数：wayCode、state（1-启用 0-停用）。停用后用户端下单不再展示/可选该方式。
     */
    @PostMapping("/admin/pay/way/state")
    public LinkedHashMap<String, Object> payWayState(@RequestBody Map<String, Object> body) {
        String wayCode = PayController.str(body.get("wayCode"));
        Integer state = body.get("state") != null ? Integer.parseInt(String.valueOf(body.get("state"))) : null;
        if (wayCode == null || state == null) throw new BizException("缺少参数：wayCode/state");
        PayWay w = payWayRepository.findById(wayCode).orElseThrow(() -> new BizException("支付方式不存在"));
        w.setState(state);
        payWayRepository.save(w);
        saveLog(body, "支付配置", (state == 1 ? "启用" : "停用") + "支付方式「" + w.getName() + "」");
        return R.ok("操作成功");
    }

    /* ==================== 商品类目 ==================== */

    /**
     * 商品类目树（POST /admin/category/tree）
     * <p>以 parentId=0 为根节点递归构建层级结构（含 children），按 sort 升序排列。
     * 返回：records 为类目树（id/name/sort/state/children）。
     */
    @PostMapping("/admin/category/tree")
    public LinkedHashMap<String, Object> categoryTree() {
        List<Category> all = categoryRepository.findAll();
        List<Map<String, Object>> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .sorted((a, b) -> Integer.compare(a.getSort() == null ? 0 : a.getSort(), b.getSort() == null ? 0 : b.getSort()))
                .map(root -> node(root, all)).collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", roots);
        return resp;
    }

    /**
     * 商品类目新增/编辑（POST /admin/category/save）
     * <p>带 id 视为编辑，否则新增；仅更新请求体中非空字段。
     * 关键参数：id（可选）、name、parentId、sort、state。
     * 返回：统一响应提示“类目保存成功”。
     */
    @PostMapping("/admin/category/save")
    public LinkedHashMap<String, Object> saveCategory(@RequestBody Map<String, Object> body) {
        Category c;
        if (body.get("id") != null && !String.valueOf(body.get("id")).isEmpty()) {
            c = categoryRepository.findById(Long.parseLong(String.valueOf(body.get("id"))))
                    .orElseThrow(() -> new BizException("类目不存在"));
        } else {
            c = new Category();
        }
        if (body.get("name") != null) c.setName(PayController.str(body.get("name")));
        if (body.get("parentId") != null) c.setParentId(Long.parseLong(String.valueOf(body.get("parentId"))));
        if (body.get("sort") != null) c.setSort(Integer.parseInt(String.valueOf(body.get("sort"))));
        if (body.get("state") != null) c.setState(Integer.parseInt(String.valueOf(body.get("state"))));
        categoryRepository.save(c);
        return R.ok("类目保存成功");
    }

    /**
     * 商品类目删除（POST /admin/category/delete）
     * <p>存在子类目时禁止删除，需先删除子类目，避免产生孤儿节点。
     * 关键参数：id。
     * 返回：统一响应提示“类目删除成功”。
     */
    @PostMapping("/admin/category/delete")
    public LinkedHashMap<String, Object> deleteCategory(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.parseLong(String.valueOf(body.get("id"))) : null;
        if (id == null) throw new BizException("缺少参数：id");
        List<Category> children = categoryRepository.findByParentId(id);
        if (!children.isEmpty()) throw new BizException("请先删除子类目");
        categoryRepository.deleteById(id);
        return R.ok("类目删除成功");
    }

    /* ==================== 系统管理 ==================== */

    /** 后台操作员列表（用户名/角色筛选由后端完成） */
    @PostMapping("/admin/sys/user/list")
    public LinkedHashMap<String, Object> sysUserList(@RequestBody Map<String, Object> params) {
        String username = PayController.str(params.get("username"));
        String role = PayController.str(params.get("role"));
        List<Map<String, Object>> filtered = sysUserRepository.findAll().stream()
                .filter(u -> u.getRole() != null && u.getRole().startsWith("ROLE_") && !"ROLE_USER".equals(u.getRole()))
                .filter(u -> username == null || u.getUsername().contains(username))
                .filter(u -> role == null || role.equals(u.getRole()))
                .map(u -> {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("id", u.getId());
                    vo.put("username", u.getUsername());
                    vo.put("realName", u.getRealName());
                    vo.put("role", u.getRole());
                    vo.put("roleName", roleName(u.getRole()));
                    vo.put("state", u.getState());
                    vo.put("createdAt", u.getCreatedAt() == null ? "" : u.getCreatedAt().format(DT));
                    return vo;
                }).collect(Collectors.toList());
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", filtered);
        return resp;
    }

    /**
     * 角色权限列表（POST /admin/sys/role/list）
     * 返回平台后台的固定角色（编码/名称/权限描述）及各角色下的账号数量，
     * 供「用户角色管理」页的角色下拉筛选与角色权限表格展示。
     */
    @PostMapping("/admin/sys/role/list")
    public LinkedHashMap<String, Object> sysRoleList() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(roleVo("ROLE_SUPER_ADMIN", "超级管理员", "拥有平台全部管理权限"));
        records.add(roleVo("ROLE_OPS", "运营管理员", "运营管理：支付渠道、支付方式、商品类目"));
        records.add(roleVo("ROLE_AUDIT", "商户审核员", "商户入驻审核与差异化费率管理"));
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        resp.put("records", records);
        return resp;
    }

    /** 构造角色视图并统计该角色下的账号数量 */
    private Map<String, Object> roleVo(String id, String name, String desc) {
        long count = sysUserRepository.findAll().stream()
                .filter(u -> id.equals(u.getRole()))
                .count();
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", id);
        vo.put("name", name);
        vo.put("desc", desc);
        vo.put("userCount", count);
        return vo;
    }

    /**
     * 系统操作日志分页查询（POST /admin/sys/log/list）
     * 支持操作人模糊、模块精确、创建时间区间过滤，按操作时间倒序分页返回。
     * 关键参数：pageNum、pageSize、username、module、startTime、endTime（yyyy-MM-dd HH:mm:ss）。
     */
    @PostMapping("/admin/sys/log/list")
    public LinkedHashMap<String, Object> sysLogList(@RequestBody Map<String, Object> params) {
        String username = PayController.str(params.get("username"));
        String module = PayController.str(params.get("module"));
        int pageNum = PayController.intVal(params.get("pageNum"), 1);
        int pageSize = PayController.intVal(params.get("pageSize"), 10);
        LocalDateTime start = parseTime(PayController.str(params.get("startTime")));
        LocalDateTime end = parseTime(PayController.str(params.get("endTime")));
        List<SysLog> all = sysLogRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(SysLog::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .filter(l -> username == null || (l.getUsername() != null && l.getUsername().contains(username)))
                .filter(l -> module == null || module.equals(l.getModule()))
                .filter(l -> start == null || (l.getCreatedAt() != null && !l.getCreatedAt().isBefore(start)))
                .filter(l -> end == null || (l.getCreatedAt() != null && l.getCreatedAt().isBefore(end.plusSeconds(1))))
                .collect(Collectors.toList());
        int total = all.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        List<Map<String, Object>> records = new ArrayList<>();
        if (from < total) {
            for (SysLog l : all.subList(from, Math.min(total, from + pageSize))) {
                Map<String, Object> vo = new HashMap<>();
                vo.put("id", l.getId());
                vo.put("username", l.getUsername());
                vo.put("module", l.getModule());
                vo.put("action", l.getAction());
                vo.put("ip", l.getIp());
                vo.put("result", l.getResult());
                vo.put("createdAt", l.getCreatedAt() == null ? "" : l.getCreatedAt().format(DT));
                records.add(vo);
            }
        }
        LinkedHashMap<String, Object> resp = R.ok("查询成功");
        R.putPage(resp, total, pageNum, pageSize, records);
        return resp;
    }

    /** "yyyy-MM-dd HH:mm:ss" 字符串转时间，空串或格式错误返回 null（不过滤） */
    private LocalDateTime parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 新增/编辑后台操作员账号（POST /admin/sys/user/save）
     * 新增：登录账号唯一 + 初始密码必填；编辑：密码留空表示不修改原密码。
     * 关键参数：id（编辑时必传）、username、realName、role、password、state。
     */
    @PostMapping("/admin/sys/user/save")
    public LinkedHashMap<String, Object> sysUserSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.parseLong(String.valueOf(body.get("id"))) : null;
        String username = PayController.str(body.get("username"));
        String realName = PayController.str(body.get("realName"));
        String role = PayController.str(body.get("role"));
        String password = PayController.str(body.get("password"));
        Integer state = body.get("state") != null ? Integer.parseInt(String.valueOf(body.get("state"))) : 1;
        if (username == null || realName == null || role == null) throw new BizException("请填写完整账号信息");
        SysUser user;
        if (id == null) {
            if (password == null) throw new BizException("请输入初始密码");
            if (sysUserRepository.findByUsername(username).isPresent()) throw new BizException("登录账号已存在");
            user = new SysUser();
            user.setCreatedAt(LocalDateTime.now());
        } else {
            user = sysUserRepository.findById(id).orElseThrow(() -> new BizException("账号不存在"));
            if (!user.getUsername().equals(username)
                    && sysUserRepository.findByUsername(username).isPresent()) {
                throw new BizException("登录账号已存在");
            }
        }
        user.setUsername(username);
        user.setRealName(realName);
        user.setRole(role);
        user.setState(state);
        if (password != null) user.setPassword(password);
        sysUserRepository.save(user);
        saveLog(body, "系统管理", (id == null ? "新增" : "编辑") + "后台账号「" + username + "」，角色：" + roleName(role));
        return R.ok("保存成功");
    }

    /**
     * 启用/禁用后台操作员账号（POST /admin/sys/user/state）
     * 关键参数：id、state（1-启用 0-禁用）。禁用后该账号无法登录后台。
     */
    @PostMapping("/admin/sys/user/state")
    public LinkedHashMap<String, Object> sysUserState(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.parseLong(String.valueOf(body.get("id"))) : null;
        Integer state = body.get("state") != null ? Integer.parseInt(String.valueOf(body.get("state"))) : 1;
        if (id == null) throw new BizException("缺少参数：id");
        SysUser user = sysUserRepository.findById(id).orElseThrow(() -> new BizException("账号不存在"));
        user.setState(state);
        sysUserRepository.save(user);
        saveLog(body, "系统管理", (state == 1 ? "启用" : "禁用") + "后台账号「" + user.getUsername() + "」");
        return R.ok("操作成功");
    }

    /**
     * 删除后台操作员账号（POST /admin/sys/user/delete）
     * 防呆：不允许删除当前登录账号（_opUser 由前端注入的当前操作人）。
     * 关键参数：id。
     */
    @PostMapping("/admin/sys/user/delete")
    public LinkedHashMap<String, Object> sysUserDelete(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null ? Long.parseLong(String.valueOf(body.get("id"))) : null;
        if (id == null) throw new BizException("缺少参数：id");
        SysUser user = sysUserRepository.findById(id).orElseThrow(() -> new BizException("账号不存在"));
        String op = PayController.str(body.get("_opUser"));
        if (op != null && op.equals(user.getUsername())) throw new BizException("不能删除当前登录账号");
        sysUserRepository.delete(user);
        saveLog(body, "系统管理", "删除后台账号「" + user.getUsername() + "」");
        return R.ok("删除成功");
    }

    private Map<String, Object> node(Category c, List<Category> all) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", c.getId());
        vo.put("name", c.getName());
        vo.put("sort", c.getSort());
        vo.put("state", c.getState());
        List<Map<String, Object>> children = all.stream()
                .filter(x -> c.getId().equals(x.getParentId()))
                .map(child -> node(child, all)).collect(Collectors.toList());
        vo.put("children", children);
        return vo;
    }

    private String roleName(String role) {
        return switch (role) {
            case "ROLE_SUPER_ADMIN" -> "超级管理员";
            case "ROLE_OPS" -> "运营管理员";
            case "ROLE_AUDIT" -> "商户审核员";
            case "ROLE_USER" -> "普通用户";
            default -> role;
        };
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
