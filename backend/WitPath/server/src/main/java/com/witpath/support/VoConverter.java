package com.witpath.support;

import com.witpath.entity.Reminder;
import com.witpath.entity.StudyMaterial;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import com.witpath.entity.User;
import com.witpath.enumeration.GoalStatus;
import com.witpath.enumeration.ProcessStatus;
import com.witpath.enumeration.ReminderStatus;
import com.witpath.enumeration.TaskStatus;
import com.witpath.enumeration.UserRole;
import com.witpath.vo.GoalVO;
import com.witpath.vo.MaterialVO;
import com.witpath.vo.RecordVO;
import com.witpath.vo.ReminderVO;
import com.witpath.vo.TaskVO;
import com.witpath.vo.UserVO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 实体 -> VO 转换器（集中放置，避免各 Service 重复实现）
 */
@Slf4j
public final class VoConverter {

    private VoConverter() {
    }

    public static UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setRoleName(UserRole.of(user.getRole()).getDesc());
        vo.setStatus(user.getStatus());
        vo.setStudyStage(user.getStudyStage());
        vo.setStudyGoal(user.getStudyGoal());
        vo.setPreferredTags(user.getPreferredTags());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    public static GoalVO toGoalVO(com.witpath.entity.StudyGoal goal) {
        if (goal == null) {
            return null;
        }
        GoalVO vo = new GoalVO();
        vo.setId(goal.getId());
        vo.setTitle(goal.getTitle());
        vo.setDescription(goal.getDescription());
        vo.setSubject(goal.getSubject());
        vo.setPriority(goal.getPriority());
        vo.setStatus(goal.getStatus());
        vo.setStatusText(GoalStatus.desc(goal.getStatus()));
        vo.setStartDate(goal.getStartDate());
        vo.setEndDate(goal.getEndDate());
        vo.setTargetHours(goal.getTargetHours());
        vo.setActualHours(goal.getActualHours());
        vo.setTaskTotal(goal.getTaskTotal());
        vo.setTaskDone(goal.getTaskDone());
        vo.setProgress(goal.getProgress());
        vo.setCreatedAt(goal.getCreatedAt());

        LocalDate today = LocalDate.now();
        if (goal.getEndDate() != null) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, goal.getEndDate());
            vo.setDaysLeft(daysLeft);
            boolean finished = goal.getStatus() != null && goal.getStatus().equals(GoalStatus.COMPLETED.getCode());
            vo.setOverdue(daysLeft < 0 && !finished);
        } else {
            vo.setDaysLeft(null);
            vo.setOverdue(false);
        }
        return vo;
    }

    public static TaskVO toTaskVO(StudyTask task) {
        return toTaskVO(task, null);
    }

    public static TaskVO toTaskVO(StudyTask task, String goalTitle) {
        if (task == null) {
            return null;
        }
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setGoalId(task.getGoalId());
        vo.setGoalTitle(goalTitle);
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setStatus(task.getStatus());
        vo.setStatusText(TaskStatus.desc(task.getStatus()));
        vo.setPriority(task.getPriority());
        vo.setPlanDate(task.getPlanDate());
        vo.setDueDate(task.getDueDate());
        vo.setEstimatedMinutes(task.getEstimatedMinutes());
        vo.setActualMinutes(task.getActualMinutes());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setSortOrder(task.getSortOrder());
        vo.setCreatedAt(task.getCreatedAt());

        LocalDate today = LocalDate.now();
        boolean done = task.getStatus() != null && task.getStatus().equals(TaskStatus.DONE.getCode());
        if (task.getDueDate() != null) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, task.getDueDate());
            vo.setDaysLeft(daysLeft);
            vo.setOverdue(daysLeft < 0 && !done);
        } else {
            vo.setDaysLeft(null);
            vo.setOverdue(false);
        }
        return vo;
    }

    public static RecordVO toRecordVO(StudyRecord record) {
        return toRecordVO(record, null, null);
    }

    public static RecordVO toRecordVO(StudyRecord record, String goalTitle, String taskTitle) {
        if (record == null) {
            return null;
        }
        RecordVO vo = new RecordVO();
        vo.setId(record.getId());
        vo.setGoalId(record.getGoalId());
        vo.setGoalTitle(goalTitle);
        vo.setTaskId(record.getTaskId());
        vo.setTaskTitle(taskTitle);
        vo.setTitle(record.getTitle());
        vo.setContent(record.getContent());
        vo.setSubject(record.getSubject());
        vo.setStudyDate(record.getStudyDate());
        vo.setStartAt(record.getStartAt());
        vo.setEndAt(record.getEndAt());
        vo.setDurationMinutes(record.getDurationMinutes());
        vo.setDurationText(formatDuration(record.getDurationMinutes()));
        vo.setFocusScore(record.getFocusScore());
        vo.setOutcome(record.getOutcome());
        vo.setTags(record.getTags());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    public static MaterialVO toMaterialVO(StudyMaterial material) {
        return toMaterialVO(material, null);
    }

    public static MaterialVO toMaterialVO(StudyMaterial material, String url) {
        if (material == null) {
            return null;
        }
        MaterialVO vo = new MaterialVO();
        vo.setId(material.getId());
        vo.setName(material.getName());
        vo.setOriginalName(material.getOriginalName());
        vo.setFileType(material.getFileType());
        vo.setFileSize(material.getFileSize());
        vo.setFileSizeText(formatFileSize(material.getFileSize()));
        vo.setStorageType(material.getStorageType());
        vo.setStorageTypeText(material.getStorageType() != null && material.getStorageType() == 2 ? "阿里云OSS" : "本地存储");
        vo.setSubject(material.getSubject());
        vo.setDescription(material.getDescription());
        vo.setParseStatus(material.getParseStatus());
        vo.setParseStatusText(ProcessStatus.desc(material.getParseStatus()));
        vo.setVectorStatus(material.getVectorStatus());
        vo.setVectorStatusText(ProcessStatus.desc(material.getVectorStatus()));
        vo.setChunkCount(material.getChunkCount());
        vo.setErrorMessage(material.getErrorMessage());
        vo.setUrl(url);
        vo.setCreatedAt(material.getCreatedAt());
        return vo;
    }

    public static ReminderVO toReminderVO(Reminder reminder) {
        if (reminder == null) {
            return null;
        }
        ReminderVO vo = new ReminderVO();
        vo.setId(reminder.getId());
        vo.setGoalId(reminder.getGoalId());
        vo.setTaskId(reminder.getTaskId());
        vo.setType(reminder.getType());
        vo.setLevel(reminder.getLevel());
        vo.setTitle(reminder.getTitle());
        vo.setContent(reminder.getContent());
        vo.setSource(reminder.getSource());
        vo.setStatus(reminder.getStatus());
        vo.setStatusText(ReminderStatus.desc(reminder.getStatus()));
        vo.setTriggerTime(reminder.getTriggerTime());
        vo.setReadAt(reminder.getReadAt());
        vo.setCreatedAt(reminder.getCreatedAt());
        return vo;
    }

    /**
     * 时长格式化：90 -> 1 小时 30 分钟
     */
    public static String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "0 分钟";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return m + " 分钟";
        }
        if (m == 0) {
            return h + " 小时";
        }
        return h + " 小时 " + m + " 分钟";
    }

    public static String formatFileSize(Long size) {
        if (size == null || size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = size;
        int idx = 0;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024;
            idx++;
        }
        return BigDecimal.valueOf(value).setScale(idx == 0 ? 0 : 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                + " " + units[idx];
    }

    /**
     * 将逗号分隔的标签字符串拆成标签列表
     */
    public static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
