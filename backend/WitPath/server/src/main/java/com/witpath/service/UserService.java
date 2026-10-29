package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.dto.PasswordDTO;
import com.witpath.entity.User;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.UserMapper;
import com.witpath.support.VoConverter;
import com.witpath.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户资料服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserVO profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return VoConverter.toUserVO(user);
    }

    /**
     * 修改个人资料（学习阶段、目标、偏好标签等）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UserVO update) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 邮箱唯一性校验
        if (update.getEmail() != null && !update.getEmail().isBlank()
                && !Objects.equals(update.getEmail(), user.getEmail())) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, update.getEmail()));
            if (count != null && count > 0) {
                throw new BusinessException(400, "邮箱已被其他账号使用");
            }
        }

        User entity = new User();
        entity.setId(userId);
        entity.setNickname(update.getNickname());
        entity.setAvatarUrl(update.getAvatarUrl());
        entity.setEmail(update.getEmail());
        entity.setPhone(update.getPhone());
        entity.setStudyStage(update.getStudyStage());
        entity.setStudyGoal(update.getStudyGoal());
        entity.setPreferredTags(update.getPreferredTags());
        userMapper.updateById(entity);

        return VoConverter.toUserVO(userMapper.selectById(userId));
    }

    /**
     * 更新头像地址
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateAvatar(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        User update = new User();
        update.setId(userId);
        update.setAvatarUrl(avatarUrl);
        userMapper.updateById(update);
        return VoConverter.toUserVO(userMapper.selectById(userId));
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "原密码不正确");
        }
        if (Objects.equals(dto.getOldPassword(), dto.getNewPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        User update = new User();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);
        log.info("用户修改密码成功：userId={}", userId);
    }
}
