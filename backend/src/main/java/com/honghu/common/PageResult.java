package com.honghu.common;

import com.github.pagehelper.PageInfo;
import lombok.Data;

import java.util.List;

/**
 * 分页结果。管理端列表接口一律返回本结构，避免一次性把全表拉回内存——
 * 题库上万条时无分页查询会拖垮接口并撑爆前端。
 */
@Data
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;
    private int pages;

    public static <T> PageResult<T> of(PageInfo<T> info) {
        PageResult<T> result = new PageResult<>();
        result.setList(info.getList());
        result.setTotal(info.getTotal());
        result.setPageNum(info.getPageNum());
        result.setPageSize(info.getPageSize());
        result.setPages(info.getPages());
        return result;
    }
}
