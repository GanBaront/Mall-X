package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.JwtUtil;
import com.mall.mgr.common.R;
import com.mall.mgr.entity.Merchant;
import com.mall.mgr.entity.SysUser;
import com.mall.mgr.repository.MerchantRepository;
import com.mall.mgr.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 统一认证控制器（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr 平台管理服务；接口前缀：/api、/admin、/mch
 * <p>采用一套账号体系打通三角色（admin / merchant / user），按账号自动判定角色。
 * 接口清单：
 * - POST /api/login      统一登录：按账号自动判定 admin / merchant / user
 * - POST /admin/login    平台管理员登录
 * - POST /mch/login      商户登录（指导书 4.1.2）
 * - POST /api/register   购物端用户注册
 * - POST /mch/register   商户入驻注册（指导书 4.1.1）
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final SysUserRepository sysUserRepository;
    private final MerchantRepository merchantRepository;
    private final JwtUtil jwtUtil;

    /**
     * 统一登录（POST /api/login）
     * <p>按账号自动判定角色：先查商户表（merchant），命中则签发 merchant 角色令牌；
     * 否则查系统用户表（sys_user），按角色前缀判定 admin / user。
     * 关键参数：username、password（来自请求体）。
     * 返回：role、token、shopNum/shopName/shopState（商户）或 username/realName/userNum（用户），并包装为统一响应。
     */
    @PostMapping("/api/login")
    public LinkedHashMap<String, Object> uniLogin(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (username == null || password == null) throw new BizException("请输入账号密码");

        // 商户
        Merchant mch = merchantRepository.findByUsername(username).orElse(null);
        if (mch != null) {
            if (!mch.getPassword().equals(password)) throw new BizException("账号或密码错误");
            // 审核门禁：仅审核通过(shopState=1)的商户可进入商家工作台
            if (mch.getShopState() == null || mch.getShopState() == 0) {
                throw new BizException("店铺正在等待平台审核，审核通过后方可登录");
            }
            if (mch.getShopState() == 2) {
                throw new BizException("商户已被禁用或入驻未通过，请联系平台");
            }
            String token = jwtUtil.issue(mch.getUsername(), "merchant", mch.getShopNum());
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("role", "merchant");
            data.put("token", token);
            data.put("shopNum", mch.getShopNum());
            data.put("shopName", mch.getShopName());
            data.put("shopState", mch.getShopState());
            return R.ok("登录成功", data);
        }
        // 管理员 / 普通用户
        SysUser user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (!user.getPassword().equals(password)) throw new BizException("账号或密码错误");
        if (user.getState() == null || user.getState() != 1) throw new BizException("账号已被禁用");
        // 角色判定：ROLE_ 前缀且非 ROLE_USER 视为管理员，否则为普通用户
        boolean isAdmin = user.getRole() != null && user.getRole().startsWith("ROLE_") && !"ROLE_USER".equals(user.getRole());
        String role = isAdmin ? "admin" : "user";
        String token = jwtUtil.issue(user.getUsername(), role, "U" + user.getUsername());
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("role", role);
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        // 昵称/头像：个人中心展示用（nickname 为空时前端回退显示账号，avatar 未上传为空）
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("userNum", "U" + user.getUsername());
        return R.ok("登录成功", data);
    }

    /**
     * 平台管理员登录（POST /admin/login）
     * <p>仅允许 ROLE_ 前缀且非 ROLE_USER 的账号登录，校验密码与账号启用状态（state=1）。
     * 关键参数：username、password。
     * 返回：token、username、realName、role，并包装为统一响应。
     */
    @PostMapping("/admin/login")
    public LinkedHashMap<String, Object> adminLogin(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        SysUser user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (!user.getPassword().equals(password)) throw new BizException("账号或密码错误");
        if (user.getState() == null || user.getState() != 1) throw new BizException("账号已被禁用");
        if (user.getRole() == null || !user.getRole().startsWith("ROLE_") || "ROLE_USER".equals(user.getRole())) {
            throw new BizException("该账号不是管理员账号");
        }
        String token = jwtUtil.issue(user.getUsername(), "admin", "A" + user.getUsername());
        HashMap<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("role", user.getRole());
        return R.ok("登录成功", data);
    }

    /**
     * 商户登录（POST /mch/login，指导书 4.1.2）
     * <p>仅校验商户表（merchant）账号密码，成功签发 merchant 角色令牌；
     * 审核门禁：shopState=0 待审核 / 2 禁用驳回时拒绝登录。
     * 关键参数：username、password。
     * 返回：token、shopNum、shopName、shopState，并包装为统一响应。
     */
    @PostMapping("/mch/login")
    public LinkedHashMap<String, Object> mchLogin(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        Merchant mch = merchantRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (!mch.getPassword().equals(password)) throw new BizException("账号或密码错误");
        if (mch.getShopState() == null || mch.getShopState() == 0) {
            throw new BizException("店铺正在等待平台审核，审核通过后方可登录");
        }
        if (mch.getShopState() == 2) {
            throw new BizException("商户已被禁用或入驻未通过，请联系平台");
        }
        String token = jwtUtil.issue(mch.getUsername(), "merchant", mch.getShopNum());
        HashMap<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("shopNum", mch.getShopNum());
        data.put("shopName", mch.getShopName());
        data.put("shopState", mch.getShopState());
        return R.ok("登录成功", data);
    }

    /**
     * 购物端用户注册（POST /api/register）
     * <p>账号至少 2 位、密码至少 6 位；商户表与用户表均查重，避免账号冲突。
     * 关键参数：username、password。注册后角色固定为 ROLE_USER、state=1。
     * 返回：统一响应提示“注册成功，请登录”。
     */
    @PostMapping("/api/register")
    public LinkedHashMap<String, Object> userRegister(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (username == null || password == null) throw new BizException("请输入账号和密码");
        if (username.length() < 2 || password.length() < 6) throw new BizException("账号至少2位，密码至少6位");
        if (sysUserRepository.findByUsername(username).isPresent()
                || merchantRepository.findByUsername(username).isPresent()) {
            throw new BizException("该账号已被注册");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(password);
        u.setRealName(username);
        u.setRole("ROLE_USER");
        u.setState(1);
        u.setCreatedAt(LocalDateTime.now());
        sysUserRepository.save(u);
        return R.ok("注册成功，请登录");
    }

    /**
     * 商户入驻注册（POST /mch/register，指导书 4.1.1）
     * <p>创建商户记录，shopState 初始为 0-待审核，由平台管理员审核后开通。
     * 关键参数：shopName、shopPhone、shopAddress、username、password。
     * 返回：统一响应含 shopNum，提示“商户注册成功，等待审核”。
     */
    @PostMapping("/mch/register")
    public LinkedHashMap<String, Object> mchRegister(@RequestBody Map<String, Object> body) {
        String shopName = str(body.get("shopName"));
        String shopPhone = str(body.get("shopPhone"));
        String shopAddress = str(body.get("shopAddress"));
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (shopName == null || shopPhone == null || shopAddress == null || username == null || password == null) {
            throw new BizException("请完整填写入驻资料");
        }
        if (merchantRepository.findByUsername(username).isPresent()
                || sysUserRepository.findByUsername(username).isPresent()) {
            throw new BizException("登录账号已存在");
        }
        String shopNum = "M" + System.currentTimeMillis();
        Merchant mch = new Merchant();
        mch.setShopNum(shopNum);
        mch.setUsername(username);
        mch.setPassword(password);
        mch.setShopName(shopName);
        mch.setShopPhone(shopPhone);
        mch.setShopAddress(shopAddress);
        mch.setShopState(0); // 待审核
        mch.setApplyTime(LocalDateTime.now());
        mch.setTotalIncome(java.math.BigDecimal.ZERO);
        mch.setTotalFee(java.math.BigDecimal.ZERO);
        mch.setBalance(java.math.BigDecimal.ZERO);
        mch.setWithdrawn(java.math.BigDecimal.ZERO);
        mch.setCreatedAt(LocalDateTime.now());
        merchantRepository.save(mch);
        Map<String, Object> data = new HashMap<>();
        data.put("shopNum", shopNum);
        return R.ok("商户注册成功，等待审核", data);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
