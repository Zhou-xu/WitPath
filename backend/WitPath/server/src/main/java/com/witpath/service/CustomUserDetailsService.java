package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.entity.User;
import com.witpath.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 自定义 UserDetailsService 实现，供 Spring Security 加载用户信息
 * <p>
 * JWT 过滤器以 userId 作为 username 调用本方法，因此同时支持按 ID 与按用户名加载。
 * 已被禁用（status=2）或逻辑删除的用户一律视为不存在。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = null;

        // JWT 场景：username 实为 userId
        if (username != null && username.chars().allMatch(Character::isDigit)) {
            user = userMapper.selectById(Long.valueOf(username));
        }
        // 表单登录场景：按用户名查询
        if (user == null) {
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username)
            );
        }

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }

        // status：1 正常 / 2 禁用 / 3 待激活，只有正常状态允许登录
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new UsernameNotFoundException("账号不可用，status=" + user.getStatus());
        }

        // role：1 普通用户 / 2 管理员
        String role = user.getRole() != null && user.getRole() == 2 ? "ADMIN" : "USER";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

        return new org.springframework.security.core.userdetails.User(
                String.valueOf(user.getId()),   // principal 用 userId
                user.getPasswordHash(),          // BCrypt 哈希
                Collections.singletonList(authority)
        );
    }
}
