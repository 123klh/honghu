package com.honghu.service;

import com.honghu.mapper.ManagerMapper;
import com.honghu.pojo.Manager;
import org.springframework.stereotype.Service;

@Service
public class ManagerService {

    private final ManagerMapper managerMapper;

    public ManagerService(ManagerMapper managerMapper) {
        this.managerMapper = managerMapper;
    }

    public Manager getByManagerName(String managerName) {
        return managerMapper.selectByManagerName(managerName);
    }

    public Manager getById(Long managerId) {
        return managerMapper.selectById(managerId);
    }
}
