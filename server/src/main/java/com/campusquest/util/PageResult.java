package com.campusquest.util;

import java.util.List;

public class PageResult<T> {

    public int page;
    public int size;
    public long total;
    public int pages;
    public List<T> list;

    public static <T> PageResult<T> of(int page, int size, long total, List<T> list) {
        PageResult<T> r = new PageResult<>();
        r.page = page;
        r.size = size;
        r.total = total;
        r.pages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        r.list = list;
        return r;
    }
}
