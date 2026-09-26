package com.honghu.controller;

import com.honghu.common.Result;
import com.honghu.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public Result<CurrentUserResponse> currentUser() {
        UserContext.Principal principal = UserContext.get();
        if (principal == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(new CurrentUserResponse(
            principal.id(),
            principal.name(),
            principal.type(),
            principal.role()
        ));
    }

    public record CurrentUserResponse(Long id, String name, String type, String role) {}
}
