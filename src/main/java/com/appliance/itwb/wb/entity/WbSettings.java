package com.appliance.itwb.wb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作台用户设置实体，与表 WB_SETTINGS 一一对应（每用户一行，主键即用户）。
 */
@TableName("WB_SETTINGS")
public class WbSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "USER_ID", type = IdType.INPUT)
    private String userId;

    private Integer warnHours;
    private String webhook;
    private String proxy;
    private Integer digestOn;
    private Integer notifyOn;
    private String digestAt;
    private String lastDigestDate;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getWarnHours() {
        return warnHours;
    }

    public void setWarnHours(Integer warnHours) {
        this.warnHours = warnHours;
    }

    public String getWebhook() {
        return webhook;
    }

    public void setWebhook(String webhook) {
        this.webhook = webhook;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public Integer getDigestOn() {
        return digestOn;
    }

    public void setDigestOn(Integer digestOn) {
        this.digestOn = digestOn;
    }

    public Integer getNotifyOn() {
        return notifyOn;
    }

    public void setNotifyOn(Integer notifyOn) {
        this.notifyOn = notifyOn;
    }

    public String getDigestAt() {
        return digestAt;
    }

    public void setDigestAt(String digestAt) {
        this.digestAt = digestAt;
    }

    public String getLastDigestDate() {
        return lastDigestDate;
    }

    public void setLastDigestDate(String lastDigestDate) {
        this.lastDigestDate = lastDigestDate;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
