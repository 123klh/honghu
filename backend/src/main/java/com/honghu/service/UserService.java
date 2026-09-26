package com.honghu.service;

import com.honghu.mapper.UserMapper;
import com.honghu.pojo.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getByUserName(String userName) {
        return userMapper.selectByUserName(userName);
    }

    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    public void register(User user) {
        userMapper.insert(user);
    }
}
