package com.scbrbackend.common.Result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> from(IPage<T> pageInfo) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(pageInfo.getTotal());
        result.setPage(pageInfo.getCurrent());
        result.setSize(pageInfo.getSize());
        result.setRecords(pageInfo.getRecords());
        return result;
    }
}
