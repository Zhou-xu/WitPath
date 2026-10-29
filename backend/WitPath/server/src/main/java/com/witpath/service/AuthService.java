package com.witpath.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.constant.Constants;
import com.witpath.dto.LoginDTO;
import com.witpath.dto.RegisterDTO;
import com.witpath.entity.User;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.UserMapper;
import com.witpath.result.Result;
import com.witpath.utils.JwtUtil;
import com.witpath.utils.SnowflakeIdGenerator;
import com.witpath.vo.CaptchaVO;
import com.witpath.vo.LoginVO;
import com.witpath.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录会话、验证码、登录、注册
 * <p>
 * 登录采用三步式会话：/login/LoginInitId -> /login/image -> /login，
 * 会话标识（LoginInitId）由雪花算法生成并写入 Redis，5 分钟内有效。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RedissonClient redissonClient;
    private final SnowflakeIdGenerator idGenerator;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserGeoLogService userGeoLogService;

    /**
     * 第一步：创建登录会话并返回会话标识
     */
    public long createLoginSession() {
        long loginInitId = idGenerator.nextId();
        RBucket<String> bucket = redissonClient.getBucket(Constants.REDIS_LOGIN_KEY + loginInitId);
        bucket.set("INIT", 10, TimeUnit.MINUTES);
        log.debug("创建登录会话：{}", loginInitId);
        return loginInitId;
    }

    /**
     * 第二步：生成图形验证码，覆盖当前会话的验证码缓存
     */
    public CaptchaVO generateCaptcha(long loginInitId) {
        RBucket<String> bucket = redissonClient.getBucket(Constants.REDIS_LOGIN_KEY + loginInitId);
        if (!bucket.isExists()) {
            throw new BusinessException(400, "登录会话不存在或已过期，请刷新页面重试");
        }

        // 尺寸为前端展示框(116x38)的 2 倍并保持相同宽高比，避免图片被裁切或留白；2x 兼顾高分屏清晰度
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(232, 76, 4, 150);
        String answer = captcha.getCode() == null ? "" : captcha.getCode().toLowerCase();
        bucket.set(answer, 5, TimeUnit.MINUTES);

        String base64 = captcha.getImageBase64();
        String image = (base64 != null && base64.startsWith("data:")) ? base64 : "data:image/png;base64," + base64;
        // 雪花 ID 以字符串返回，避免 JS Number 精度丢失
        return new CaptchaVO(String.valueOf(loginInitId), image);
    }

    /**
     * 第三步：校验验证码 + 账密，签发 JWT
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        Long loginInitId = parseLoginInitId(dto.getLoginInitId());

        RBucket<String> bucket = redissonClient.getBucket(Constants.REDIS_LOGIN_KEY + loginInitId);
        String cached = bucket.get();
        if (cached == null) {
            throw new BusinessException(400, "登录会话已过期，请刷新验证码");
        }
        // 会话仍停留在 INIT 阶段说明尚未获取验证码
        if ("INIT".equals(cached)) {
            throw new BusinessException(400, "请先获取验证码");
        }
        if (!cached.equalsIgnoreCase(dto.getCaptchaAnswer())) {
            throw new BusinessException(400, "验证码错误或已失效");
        }
        // 验证码一次性使用，避免重放
        bucket.delete();

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BusinessException(400, "用户名或密码错误");
        } catch (AuthenticationException e) {
            throw new BusinessException(400, e.getMessage());
        }

        // CustomUserDetailsService 以 userId 作为 UserDetails 的 username，
        // 认证成功后 principal 是 UserDetails 而非 String，这里统一取值避免类型转换异常
        Object principal = authentication.getPrincipal();
        String principalName = principal instanceof UserDetails
                ? ((UserDetails) principal).getUsername()
                : String.valueOf(principal);
        Long userId = Long.parseLong(principalName);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 更新最后登录时间
        User update = new User();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        String token = jwtUtil.createToken(userId);
        log.info("用户登录成功：userId={} username={}", userId, user.getUsername());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(userId);
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname() == null || user.getNickname().isEmpty() ? user.getUsername() : user.getNickname());
        vo.setAvatar(user.getAvatarUrl());
        vo.setRole(user.getRole());
        vo.setExpire(jwtUtil.getExpire());
        return vo;
    }

    /**
     * 用户注册
     *
     * @param clientIp 注册用户 IP（前端公网探测结果，Controller 兜底解析后传入），
     *                 仅用于注册成功后异步记录归属地日志，不参与注册校验
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterDTO dto, String clientIp) {
        Long loginInitId = parseLoginInitId(dto.getLoginInitId());
        RBucket<String> bucket = redissonClient.getBucket(Constants.REDIS_LOGIN_KEY + loginInitId);
        String cached = bucket.get();
        if (cached == null || "INIT".equals(cached)) {
            throw new BusinessException(400, "登录会话已过期，请重新获取验证码");
        }
        if (!cached.equalsIgnoreCase(dto.getCaptchaAnswer())) {
            throw new BusinessException(400, "验证码错误或已失效");
        }

        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (usernameCount != null && usernameCount > 0) {
            throw new BusinessException(400, "用户名已被占用");
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            Long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail()));
            if (emailCount != null && emailCount > 0) {
                throw new BusinessException(400, "邮箱已被注册");
            }
        }

        User user = User.builder()
                .username(dto.getUsername())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname() == null || dto.getNickname().isBlank() ? dto.getUsername() : dto.getNickname())
                .avatarUrl("")
                .email(dto.getEmail() == null || dto.getEmail().isBlank() ? null : dto.getEmail())
                .role(1)
                .status(1)
                .studyStage(dto.getStudyStage())
                .studyGoal(dto.getStudyGoal())
                .preferredTags(dto.getPreferredTags())
                .isDeleted(0)
                .build();
        userMapper.insert(user);

        // 注册成功后立即失效验证码
        bucket.delete();
        log.info("新用户注册成功：username={} userId={}", user.getUsername(), user.getId());
        // 异步记录注册 IP 归属地（userlog），内部线程池执行，不影响注册主流程
        userGeoLogService.recordRegister(user.getId(), user.getUsername(), clientIp);
        return com.witpath.support.VoConverter.toUserVO(userMapper.selectById(user.getId()));
    }

    /**
     * 解析前端传来的登录会话标识
     * <p>
     * 该标识是 19 位雪花 ID，前端受 JS 精度限制只能以字符串承载，这里统一解析，
     * 保证任何非法输入都落到明确的 400，而不是让 Long 转换异常冒泡成 500。
     * </p>
     */
    private Long parseLoginInitId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(400, "缺少登录会话标识");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "登录会话标识非法");
        }
    }
}
