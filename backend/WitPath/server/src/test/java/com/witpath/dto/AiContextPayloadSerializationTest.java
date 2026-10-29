package com.witpath.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.witpath.enumeration.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归用例：AI 服务使用 pydantic strict 校验，Spring 侧必须把 LocalDate 序列化成
 * "yyyy-MM-dd" 字符串（默认会被 Jackson 写成 [2026,3,1] 数组，导致 AI 400）。
 */
class AiContextPayloadSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());


    @Test
    @DisplayName("载荷中的日期一律序列化为 yyyy-MM-dd 字符串")
    void datesAreSerializedAsString() throws Exception {
        AiContextPayload payload = samplePayload();

        String json = mapper.writeValueAsString(payload);

        assertTrue(json.contains("\"from\":\"2026-10-01\""), json);
        assertTrue(json.contains("\"to\":\"2026-10-15\""), json);
        assertTrue(json.contains("\"startDate\":\"2026-10-01\""), json);
        assertTrue(json.contains("\"dueDate\":\"2026-10-20\""), json);
        assertTrue(json.contains("\"studyDate\":\"2026-10-05\""), json);
        assertFalse(json.contains("[2026,10,1"), "日期不能序列化为数组：" + json);
    }

    @Test
    @DisplayName("字段命名保持驼峰，AI 侧可直接按 camelCase 解析")
    void fieldsKeepCamelCase() throws Exception {
        String json = mapper.writeValueAsString(samplePayload());

        assertTrue(json.contains("\"userId\":10086"), json);
        assertTrue(json.contains("\"taskNo\":\"AI20261018001\""), json);
        assertTrue(json.contains("\"callbackUrl\":\"http://127.0.0.1:8080/api/ai/callback\""), json);
        assertTrue(json.contains("\"materialSnippets\""), json);
    }

    @Test
    @DisplayName("序列化结果可被反序列化回等价对象")
    void roundTripKeepsDates() throws Exception {
        AiContextPayload payload = samplePayload();
        String json = mapper.writeValueAsString(payload);

        AiContextPayload parsed = mapper.readValue(json, AiContextPayload.class);

        assertEquals(LocalDate.of(2026, 10, 1), parsed.getSummary().getFrom());
        assertEquals(LocalDate.of(2026, 10, 15), parsed.getSummary().getTo());
        assertEquals(LocalDate.of(2026, 10, 20), parsed.getTasks().get(0).getDueDate());
        assertEquals(LocalDate.of(2026, 10, 5), parsed.getRecords().get(0).getStudyDate());
    }

    private AiContextPayload samplePayload() {
        AiContextPayload payload = new AiContextPayload();
        payload.setUserId(10086L);
        payload.setTaskNo("AI20261018001");
        payload.setQuestion("帮我看看最近两周的学习情况");
        payload.setCallbackUrl("http://127.0.0.1:8080/api/ai/callback");
        payload.setCallbackToken("witpath-internal-token");
        payload.setMaterialSnippets(List.of("三角函数和角公式：sin(a+b)=..."));

        AiContextPayload.Summary summary = new AiContextPayload.Summary();
        summary.setRangeDays(14);
        summary.setTotalMinutes(720);
        summary.setRecordCount(12);
        summary.setStreakDays(5);
        summary.setAvgFocus(new BigDecimal("4.20"));
        summary.setFrom(LocalDate.of(2026, 10, 1));
        summary.setTo(LocalDate.of(2026, 10, 15));
        payload.setSummary(summary);

        AiContextPayload.GoalItem goal = new AiContextPayload.GoalItem();
        goal.setId(1L);
        goal.setTitle("期末数学冲刺");
        goal.setSubject("数学");
        goal.setTargetHours(new BigDecimal("30"));
        goal.setActualHours(new BigDecimal("12"));
        goal.setProgress(new BigDecimal("40.00"));
        goal.setStartDate(LocalDate.of(2026, 10, 1));
        goal.setEndDate(LocalDate.of(2026, 10, 31));
        payload.setGoals(List.of(goal));

        AiContextPayload.TaskItem task = new AiContextPayload.TaskItem();
        task.setId(11L);
        task.setGoalId(1L);
        task.setTitle("完成三角函数专题");
        task.setStatus(TaskStatus.DONE.getCode());
        task.setPriority(1);
        task.setDueDate(LocalDate.of(2026, 10, 20));
        task.setEstimatedMinutes(120);
        task.setOverdue(false);
        payload.setTasks(List.of(task));

        AiContextPayload.RecordItem record = new AiContextPayload.RecordItem();
        record.setStudyDate(LocalDate.of(2026, 10, 5));
        record.setTitle("三角函数练习");
        record.setSubject("数学");
        record.setDurationMinutes(90);
        record.setFocusScore(4);
        record.setOutcome("掌握了和角公式");
        payload.setRecords(List.of(record));

        return payload;
    }
}
