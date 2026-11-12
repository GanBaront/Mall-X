package com.mall.mgr.controller;

import com.mall.mgr.common.BizException;
import com.mall.mgr.common.R;
import com.mall.mgr.common.UserContext;
import com.mall.mgr.entity.SysUser;
import com.mall.mgr.repository.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户个人资料控制器（mall-mgr 平台管理服务）
 * <p>所属模块：mall-mgr；接口前缀：/api/user（购物端个人中心使用，需登录）。
 * 身份识别：网关统一鉴权后透传 X-Mall-Username 请求头，这里按账号查 sys_user 表。
 * 接口清单：
 * - POST /api/user/info          我的资料（昵称/头像/账号/编号）
 * - POST /api/user/profile/save  修改资料（昵称、头像路径）
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final SysUserRepository sysUserRepository;
    private final UserContext userContext;
    private final HttpServletRequest request;

    /**
     * 我的资料（POST /api/user/info）
     * <p>按网关透传的登录账号查询 sys_user，返回昵称/头像/真实姓名/用户编号。
     * 头像 avatar 存的是相对路径（如 /upload/avatar_x.jpg），前端按运行端自行拼接网关地址。
     * 返回：username/realName/nickname/avatar/userNum，并包装为统一响应。
     */
    @PostMapping("/api/user/info")
    public LinkedHashMap<String, Object> myInfo() {
        SysUser user = currentUser();
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("userNum", "U" + user.getUsername());
        return R.ok("查询成功", data);
    }

    /**
     * 修改资料（POST /api/user/profile/save）
     * <p>仅允许修改当前登录用户自己的昵称与头像（归属校验：按网关透传账号查库后更新）。
     * 关键参数：nickname（可选，2-20 位）、avatar（可选，须为 /upload/ 开头的站内路径）。
     * 返回：更新后的 nickname/avatar，并包装为统一响应。
     */
    @PostMapping("/api/user/profile/save")
    public LinkedHashMap<String, Object> saveProfile(@RequestBody Map<String, Object> body) {
        SysUser user = currentUser();
        String nickname = str(body.get("nickname"));
        String avatar = str(body.get("avatar"));
        if (nickname != null) {
            if (nickname.length() < 2 || nickname.length() > 20) throw new BizException("昵称需为 2-20 个字符");
            user.setNickname(nickname);
        }
        if (avatar != null) {
            // 只允许站内上传产生的路径，防止把头像指到任意外部地址
            if (!avatar.startsWith("/upload/")) throw new BizException("头像地址无效");
            user.setAvatar(avatar);
        }
        sysUserRepository.save(user);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        return R.ok("资料已更新", data);
    }

    /** 取当前登录用户（账号来自网关透传请求头），取不到视为登录态异常 */
    private SysUser currentUser() {
        String username = userContext.username(request);
        if (username == null || username.isBlank() || "null".equals(username)) {
            throw new BizException("登录状态无效，请重新登录");
        }
        return sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("登录账号不存在"));
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
