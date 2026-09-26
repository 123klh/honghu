package com.honghu.service;

import com.honghu.auth.TokenStore;
import com.honghu.common.BusinessException;
import com.honghu.pojo.Manager;
import com.honghu.pojo.User;
import com.honghu.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final ManagerService managerService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    public AuthService(ManagerService managerService, UserService userService,
                      PasswordEncoder passwordEncoder, JwtUtil jwtUtil, TokenStore tokenStore) {
        this.managerService = managerService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
    }

    public Map<String, Object> login(String userName, String password, String userType) {
        if ("manager".equals(userType)) {
            Manager manager = managerService.getByManagerName(userName);
            if (manager == null || !passwordEncoder.matches(password, manager.getPasswordHash())) {
                throw new BusinessException(401, "用户名或密码错误");
            }

            String subjectKey = "manager:" + manager.getManagerId();
            tokenStore.revokeAll(subjectKey);

            String token = jwtUtil.generate(manager.getManagerId(), manager.getManagerName(), "manager", manager.getRole());
            tokenStore.save(token, subjectKey, jwtUtil.getExpirationSeconds());

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userInfo", Map.of(
                "id", manager.getManagerId(),
                "userName", manager.getManagerName(),
                "type", "manager",
                "role", manager.getRole()
            ));
            return result;
        } else {
            User user = userService.getByUserName(userName);
            if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BusinessException(401, "用户名或密码错误");
            }

            String subjectKey = "user:" + user.getUserId();
            tokenStore.revokeAll(subjectKey);

            String token = jwtUtil.generate(user.getUserId(), user.getUserName(), "user", null);
            tokenStore.save(token, subjectKey, jwtUtil.getExpirationSeconds());

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userInfo", Map.of(
                "id", user.getUserId(),
                "userName", user.getUserName(),
                "type", "user"
            ));
            return result;
        }
    }

    public void logout(String token) {
        if (token != null) {
            tokenStore.revoke(token);
        }
    }

    public void register(User user, String password) {
        if (userService.getByUserName(user.getUserName()) != null) {
            throw new BusinessException(409, "用户名已存在");
        }
        // gender 列是 enum('男','女')，空串会被 MySQL 拒绝，统一收敛为 null
        if (user.getGender() != null && !"男".equals(user.getGender()) && !"女".equals(user.getGender())) {
            user.setGender(null);
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        userService.register(user);
    }
}
