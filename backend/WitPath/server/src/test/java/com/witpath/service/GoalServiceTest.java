package com.witpath.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.witpath.dto.GoalSaveDTO;
import com.witpath.entity.StudyGoal;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import com.witpath.enumeration.GoalStatus;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.StudyGoalMapper;
import com.witpath.mapper.StudyRecordMapper;
import com.witpath.mapper.StudyTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 目标服务单元测试：聚焦校验规则与进度重算口径（不依赖数据库与中间件）。
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private StudyGoalMapper goalMapper;
    @Mock
    private StudyTaskMapper taskMapper;
    @Mock
    private StudyRecordMapper recordMapper;

    @InjectMocks
    private GoalService goalService;

    /**
     * 纯单元测试没有 MyBatis 容器，需要手动初始化实体元数据，
     * 否则 LambdaQueryWrapper 的字段解析会报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, StudyGoal.class);
        TableInfoHelper.initTableInfo(assistant, StudyTask.class);
        TableInfoHelper.initTableInfo(assistant, StudyRecord.class);
    }

    // ------------------------------------------------------------------ 校验

    @Test
    @DisplayName("标题为空时拒绝保存")
    void saveRejectsBlankTitle() {
        GoalSaveDTO dto = new GoalSaveDTO();
        dto.setTitle("   ");

        BusinessException ex = assertThrows(BusinessException.class, () -> goalService.save(1L, dto));
        assertEquals(400, ex.getCode());
        verify(goalMapper, never()).insert(any(StudyGoal.class));
    }

    @Test
    @DisplayName("截止日期早于开始日期时拒绝保存")
    void saveRejectsInvalidDateRange() {
        GoalSaveDTO dto = new GoalSaveDTO();
        dto.setTitle("期末冲刺");
        dto.setStartDate(LocalDate.of(2026, 10, 20));
        dto.setEndDate(LocalDate.of(2026, 10, 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> goalService.save(1L, dto));
        assertEquals(400, ex.getCode());
        verify(goalMapper, never()).insert(any(StudyGoal.class));
    }

    @Test
    @DisplayName("访问他人目标返回 404")
    void getOwnedGoalRejectsOtherUser() {
        StudyGoal goal = new StudyGoal();
        goal.setId(1L);
        goal.setUserId(999L);
        when(goalMapper.selectById(1L)).thenReturn(goal);

        BusinessException ex = assertThrows(BusinessException.class, () -> goalService.getOwnedGoal(1L, 1L));
        assertEquals(404, ex.getCode());
    }

    // -------------------------------------------------------------- 进度重算

    @Test
    @DisplayName("存在任务时按任务完成率计算进度")
    void recalculateByTaskCompletion() {
        StudyGoal goal = goal(1L, 1L, new BigDecimal("30"), GoalStatus.IN_PROGRESS.getCode());
        when(goalMapper.selectOne(any())).thenReturn(goal);
        // 第一次查询任务总数，第二次查询已完成任务数
        when(taskMapper.selectCount(any())).thenReturn(4L, 2L);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        goalService.recalculate(1L, 1L);

        StudyGoal update = capturedUpdate();
        assertEquals(4, update.getTaskTotal());
        assertEquals(2, update.getTaskDone());
        assertEquals(0, new BigDecimal("50.00").compareTo(update.getProgress()));
        assertNull(update.getStatus(), "任务未全部完成时不应改状态");
    }

    @Test
    @DisplayName("无任务时按 实际小时 / 计划小时 计算进度")
    void recalculateByHours() {
        StudyGoal goal = goal(2L, 1L, new BigDecimal("10"), GoalStatus.IN_PROGRESS.getCode());
        when(goalMapper.selectOne(any())).thenReturn(goal);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(recordMapper.selectList(any())).thenReturn(List.of(record(120), record(180)));

        goalService.recalculate(1L, 2L);

        StudyGoal update = capturedUpdate();
        assertEquals(0, new BigDecimal("50.00").compareTo(update.getProgress()));
        assertEquals(0, new BigDecimal("5.00").compareTo(update.getActualHours()));
    }

    @Test
    @DisplayName("进度上限为 100%")
    void recalculateCapsAt100() {
        StudyGoal goal = goal(3L, 1L, new BigDecimal("2"), GoalStatus.IN_PROGRESS.getCode());
        when(goalMapper.selectOne(any())).thenReturn(goal);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(recordMapper.selectList(any())).thenReturn(List.of(record(600)));

        goalService.recalculate(1L, 3L);

        assertEquals(0, new BigDecimal("100.00").compareTo(capturedUpdate().getProgress()));
    }

    @Test
    @DisplayName("任务全部完成时目标自动置为已完成")
    void recalculateAutoCompletesGoal() {
        StudyGoal goal = goal(4L, 1L, new BigDecimal("30"), GoalStatus.IN_PROGRESS.getCode());
        when(goalMapper.selectOne(any())).thenReturn(goal);
        when(taskMapper.selectCount(any())).thenReturn(3L, 3L);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        goalService.recalculate(1L, 4L);

        StudyGoal update = capturedUpdate();
        assertEquals(GoalStatus.COMPLETED.getCode(), update.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(update.getProgress()));
    }

    @Test
    @DisplayName("目标不存在时重算直接返回")
    void recalculateSkipsMissingGoal() {
        when(goalMapper.selectOne(any())).thenReturn(null);

        goalService.recalculate(1L, 999L);

        verify(goalMapper, never()).updateById(any(StudyGoal.class));
    }

    // ------------------------------------------------------------------ 辅助

    private StudyGoal goal(Long id, Long userId, BigDecimal targetHours, Integer status) {
        StudyGoal goal = new StudyGoal();
        goal.setId(id);
        goal.setUserId(userId);
        goal.setTargetHours(targetHours);
        goal.setStatus(status);
        return goal;
    }

    private StudyRecord record(int minutes) {
        StudyRecord record = new StudyRecord();
        record.setDurationMinutes(minutes);
        return record;
    }

    private StudyGoal capturedUpdate() {
        ArgumentCaptor<StudyGoal> captor = ArgumentCaptor.forClass(StudyGoal.class);
        verify(goalMapper, atLeastOnce()).updateById(captor.capture());
        return captor.getValue();
    }
}
