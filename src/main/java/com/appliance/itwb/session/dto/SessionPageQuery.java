package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 在线终端分页查询条件。
 *
 * <p>接口：GET /api/auth/sessions</p>
 *
 * <p><b>文件作用</b>：接收分页与筛选参数。userId 从令牌解析（不信任前端传参），
 * deviceType/keyword 为可选筛选。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：页码从 1 开始。若前端传 0 或负数，Service 会强制收敛到 1，
 * 因为 Oracle 方言的分页 SQL 对 RN &gt; ? 的负下界没有意义。</p>
 *
 * <p><b>示例</b>：/api/auth/sessions?pageNum=1&amp;pageSize=10&amp;deviceType=PC</p>
 */
public class SessionPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始，默认 1。 */
    private Integer pageNum = 1;

    /** 每页条数，默认 10，上限由 MybatisPlusConfig 的 maxLimit 控制。 */
    private Integer pageSize = 10;

    /** 终端类型筛选，为空表示全部。 */
    private String deviceType;

    /** 关键字模糊匹配设备描述或 IP，为空表示不筛选。 */
    private String keyword;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
