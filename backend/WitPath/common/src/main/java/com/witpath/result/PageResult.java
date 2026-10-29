package com.witpath.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回结构
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页码，从 1 开始 */
    private Long page;

    /** 每页条数 */
    private Long size;

    /** 总条数 */
    private Long total;

    /** 总页数 */
    private Long pages;

    private List<T> records;

    /**
     * 构造分页结果，自动计算总页数
     */
    public static <T> PageResult<T> of(Long current, Long size, Long total, List<T> records) {
        long currentVal = current == null || current < 1 ? 1 : current;
        long sizeVal = size == null || size < 1 ? 10 : size;
        long totalVal = total == null || total < 0 ? 0 : total;
        long pages = totalVal == 0 ? 0 : (totalVal + sizeVal - 1) / sizeVal;

        PageResult<T> result = new PageResult<>();
        result.setPage(currentVal);
        result.setSize(sizeVal);
        result.setTotal(totalVal);
        result.setPages(pages);
        result.setRecords(records == null ? Collections.emptyList() : records);
        return result;
    }

    public static <T> PageResult<T> empty(Long page, Long size) {
        return of(page, size, 0L, Collections.emptyList());
    }
}
