package com.honghu.init;

import com.honghu.mapper.ManagerMapper;
import com.honghu.pojo.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 首次启动时创建超级管理员，密码随机生成并落盘提示。
 * 相较原论文的硬编码固定密码，避免了所有部署共用同一凭据的问题。
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    /** 去掉易混淆字符（0/O/1/l/I）的密码字符集 */
    private static final String PASSWORD_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%^&*";

    private static final String ADMIN_NAME = "admin";
    private static final Path CREDENTIALS_FILE = Path.of("ADMIN_CREDENTIALS.txt");

    private final ManagerMapper managerMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(ManagerMapper managerMapper, PasswordEncoder passwordEncoder) {
        this.managerMapper = managerMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Manager existing = managerMapper.selectByManagerName(ADMIN_NAME);
        if (existing != null) {
            log.info("超级管理员已存在（managerId={}），跳过初始化", existing.getManagerId());
            return;
        }

        String password = generateRandomPassword(16);
        String hash = passwordEncoder.encode(password);
        managerMapper.insertAdmin(ADMIN_NAME, hash, "SUPER_ADMIN", "系统初始化生成的超级管理员");

        log.info("{}", "=".repeat(78));
        log.info("超级管理员账户已创建");
        log.info("  用户名: {}", ADMIN_NAME);
        log.info("  密码  : {}", password);
        log.info("  凭据已写入: {}", CREDENTIALS_FILE.toAbsolutePath());
        log.info("请立即登录并修改密码，随后删除该凭据文件。");
        log.info("{}", "=".repeat(78));

        writeCredentialsFile(password);
    }

    private void writeCredentialsFile(String password) {
        String content = """
                波波公考 — 超级管理员初始凭据
                生成时间: %s

                用户名: %s
                密码  : %s

                安全提示：首次登录后请立即通过「管理端 → 修改密码」更换密码，并删除本文件。
                """.formatted(LocalDateTime.now(), ADMIN_NAME, password);
        try {
            Files.writeString(CREDENTIALS_FILE, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("凭据文件写入失败，请从上方日志中抄录密码: {}", e.getMessage());
        }
    }

    private String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
