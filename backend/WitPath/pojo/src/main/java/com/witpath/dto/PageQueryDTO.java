package com.witpath.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页查询入参
 */
@Data
public class PageQueryDTO implements Serializable {

    /** 页码，从 1 开始 */
    private Long page = 1L;

    /** 每页条数 */
    private Long size = 10L;

    /** 关键字搜索 */
    private String keyword;

    public Long safePage() {
        return page == null || page < 1 ? 1L : page;
    }

    public Long safeSize() {
        if (size == null || size < 1) {
            return 10L;
        }
        return Math.min(size, 100L);
    }
}
