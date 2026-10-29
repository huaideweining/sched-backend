package com.workbuddy.sched.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbuddy.sched.auth.RegisterRequest;
import com.workbuddy.sched.common.BizException;
import com.workbuddy.sched.common.ErrorCode;
import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.dto.RegisterResp;
import com.workbuddy.sched.entity.SchedUser;
import com.workbuddy.sched.mapper.SchedUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户注册服务实现（R4）。
 *
 * <p>约定：{@code USER_ID = USERNAME}（注册即初始化，业务主键与登录名一致）；
 * 密码以 BCrypt 哈希存储（与既有 SCHED_USER 手插 BCrypt 哈希保持同构）；
 * 新用户默认启用 {@code STATUS='Y'}。用户名唯一由 UK_SCHED_USER_NAME 兜底，
 * 业务层先查后插，给出可读的 {@code USER_DUP=1004} 错误而非裸 ORA-00001。</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private final SchedUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegisterResp register(RegisterRequest req) {
        String username = req.getUsername().trim();

        // 先查后插：给出可读的"用户名已存在"错误，规避裸唯一约束异常（ORA-00001）
        Long exist = userMapper.selectCount(
                new LambdaQueryWrapper<SchedUser>().eq(SchedUser::getUsername, username));
        if (exist != null && exist > 0) {
            throw new BizException(ErrorCode.USER_DUP, "username already exists");
        }

        LocalDateTime now = LocalDateTime.now(TimeZoneUtil.ZONE);
        SchedUser user = new SchedUser();
        user.setUserId(username);                          // 约定：USER_ID = USERNAME
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.getPassword())); // BCrypt 哈希
        user.setStatus("Y");                               // 默认启用
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发同名注册越过"先查后插"的 TOCTOU 窗口，命中 UK_SCHED_USER_NAME；
            // 统一转成 USER_DUP，保证"用户名已存在"无论时序都返回 1004 而非裸 1001/500。
            throw new BizException(ErrorCode.USER_DUP, "username already exists");
        }

        return RegisterResp.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .status(user.getStatus())
                .build();
    }
}
