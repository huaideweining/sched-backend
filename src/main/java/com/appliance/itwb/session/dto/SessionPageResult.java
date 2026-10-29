package com.appliance.itwb.session.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果包装。
 *
 * <p>接口：GET /api/auth/sessions</p>
 *
 * <p><b>文件作用</b>：替代 Spring Data 的 {@code Page} 出现在接口层，
 * 避免把 MyBatis-Plus 的分页类型泄漏给前端契约，也便于将来换成 Redis/ES 分页时不用改前端。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：total 来自 Oracle 方言生成的 COUNT 查询，
 * 在大表上 COUNT 走 IDX_SYS_USER_SESSION_USER_STATUS 前缀，代价可接受。</p>
 *
 * <p><b>示例</b>：</p>
 * <pre>
 * {"list": [...], "total": 3, "pageNum": 1, "pageSize": 10, "pages": 1}
 * </pre>
 *
 * @param <T> 列表元素类型
 */
public class SessionPageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据。 */
    private List<T> list = new ArrayList<>();

    /** 符合条件的总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int pageNum = 1;

    /** 每页条数。 */
    private int pageSize = 10;

    /** 总页数。 */
    private int pages;

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list == null ? new ArrayList<>() : list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
