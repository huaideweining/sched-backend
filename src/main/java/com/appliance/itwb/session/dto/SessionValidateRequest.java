package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 会话校验请求体。
 *
 * <p>接口：GET /api/auth/session/validate（也支持 POST，见 AuthController 的 validatePost）。</p>
 *
 * <p><b>文件作用</b>：把"只校验"与"校验并续期"两种模式显式化，供网关/前端过滤器决定要不要写库。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>关于 slide 性能取舍</b>：slide=true 时，只有"剩余有效期低于续期阈值"的会话才会真正写库，
 * 其余请求只做一次等值查询，避免每请求一次 UPDATE 造成写放大。</p>
 *
 * <p><b>示例（GET 查询串）</b>：/api/auth/session/validate?deviceId=PC-20240513-XXXX&amp;slide=false</p>
 */
public class SessionValidateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 终端指纹，用于校验令牌是否被复制到别的设备。 */
    private String deviceId;

    /** true=校验并按需续期（滑动过期）；false=只校验不写库。默认 true。 */
    private Boolean slide = Boolean.TRUE;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Boolean getSlide() {
        return slide;
    }

    public void setSlide(Boolean slide) {
        this.slide = slide;
    }
}
