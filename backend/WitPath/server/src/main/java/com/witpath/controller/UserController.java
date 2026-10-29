package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.AvatarPresetDTO;
import com.witpath.dto.PasswordDTO;
import com.witpath.result.Result;
import com.witpath.service.AvatarService;
import com.witpath.service.ReminderService;
import com.witpath.service.UserService;
import com.witpath.vo.AvatarPresetVO;
import com.witpath.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * 个人资料控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ReminderService reminderService;
    private final AvatarService avatarService;

    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.success(userService.profile(UserContext.requireUserId()));
    }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestBody UserVO update) {
        return Result.success(userService.updateProfile(UserContext.requireUserId(), update));
    }

    @PostMapping("/password")
    public Result<Boolean> changePassword(@RequestBody PasswordDTO dto) {
        userService.changePassword(UserContext.requireUserId(), dto);
        return Result.success(true);
    }

    /** 内置在线头像列表（含 SVG 源码，供前端直接预览） */
    @GetMapping("/avatar/presets")
    public Result<List<AvatarPresetVO>> avatarPresets() {
        return Result.success(avatarService.presets());
    }

    /** 上传本地图片作为头像，直接存入阿里云 OSS（公共读直链） */
    @PostMapping("/avatar")
    public Result<UserVO> uploadAvatar(@RequestPart("file") MultipartFile file) {
        Long userId = UserContext.requireUserId();
        String url = avatarService.upload(userId, file);
        return Result.success(applyAvatar(userId, url));
    }

    /** 选用内置在线头像：由后端用同一份 SVG 上传 OSS，保证与预览一致 */
    @PostMapping("/avatar/preset")
    public Result<UserVO> applyAvatarPreset(@Valid @RequestBody AvatarPresetDTO dto) {
        Long userId = UserContext.requireUserId();
        String url = avatarService.applyPreset(userId, dto.getPresetId());
        return Result.success(applyAvatar(userId, url));
    }

    /**
     * 头像内容代理：bucket 为公共读时前端直接用 OSS 直链，不会走到这里；
     * bucket 仍为私有时由本接口中转输出（免鉴权，仅允许 avatars/ 前缀）。
     */
    @GetMapping("/avatar/raw")
    public ResponseEntity<byte[]> rawAvatar(@RequestParam("key") String key) {
        AvatarService.AvatarObject obj = avatarService.read(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                // SVG 由后端生成（不含脚本），额外加 CSP 兜底，防止直链被当作页面打开
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .body(obj.bytes());
    }

    /**
     * 手动触发一次提醒生成（便于演示，无需等待定时任务）
     */
    @PostMapping("/reminders/generate")
    public Result<Integer> generateReminders() {
        return Result.success(reminderService.generateForUser(UserContext.requireUserId()));
    }

    /** 落库新头像并清理旧头像对象（旧图非本服务生成时自动跳过） */
    private UserVO applyAvatar(Long userId, String url) {
        String previous = userService.profile(userId).getAvatarUrl();
        UserVO updated = userService.updateAvatar(userId, url);
        avatarService.deleteQuietly(previous);
        return updated;
    }
}
