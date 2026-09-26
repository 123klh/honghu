package com.honghu.controller;

import com.honghu.auth.TokenStore;
import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.pojo.User;
import com.honghu.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    @PostMapping("/user/login")
    public Result<?> loginUser(@RequestBody @Validated LoginRequest request) {
        return Result.success(authService.login(request.getUserName(), request.getPassword(), "user"));
    }

    @PostMapping("/manager/login")
    public Result<?> loginManager(@RequestBody @Validated LoginRequest request) {
        return Result.success(authService.login(request.getUserName(), request.getPassword(), "manager"));
    }

    @PostMapping("/user/register")
    public Result<Void> register(@RequestBody @Validated RegisterRequest request) {
        User user = new User();
        user.setUserName(request.getUserName());
        user.setEmail(request.getEmail());
        user.setGender(request.getGender());
        user.setBirthday(request.getBirthday());
        user.setAddress(request.getAddress());

        authService.register(user, request.getPassword());
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            authService.logout(token);
        }
        return Result.success();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        String alt = request.getHeader("token");
        return alt == null || alt.isBlank() ? null : alt.trim();
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String userName;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
        private String userName;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 50, message = "密码长度必须在6-50之间")
        private String password;

        private String email;

        /** 数据库为 enum('男','女')，留空则不写入 */
        private String gender;
        private LocalDate birthday;
        private String address;
    }

    public record LoginResponse(String token) {}
}
