package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.PageQueryDTO;
import com.witpath.dto.RecordSaveDTO;
import com.witpath.result.PageResult;
import com.witpath.result.Result;
import com.witpath.service.RecordService;
import com.witpath.vo.RecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习记录控制器
 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @GetMapping("/page")
    public Result<PageResult<RecordVO>> page(
            PageQueryDTO query,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "subject", required = false) String subject) {
        return Result.success(recordService.page(UserContext.requireUserId(), query, from, to, subject));
    }

    @GetMapping("/recent")
    public Result<List<RecordVO>> recent(@RequestParam(value = "limit", defaultValue = "8") Integer limit) {
        return Result.success(recordService.listRecent(UserContext.requireUserId(), limit));
    }

    @PostMapping
    public Result<RecordVO> save(@RequestBody RecordSaveDTO dto) {
        return Result.success(recordService.save(UserContext.requireUserId(), dto));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> remove(@PathVariable Long id) {
        recordService.remove(UserContext.requireUserId(), id);
        return Result.success(true);
    }
}
