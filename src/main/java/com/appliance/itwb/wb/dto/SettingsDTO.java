package com.appliance.itwb.wb.dto;

import java.io.Serializable;

/**
 * 用户设置 DTO（wire 层）。digestOn/notifyOn 为布尔；其余原样。
 */
public class SettingsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer warnHours;
    private String webhook;
    private String proxy;
    private Boolean digestOn;
    private Boolean notifyOn;
    private String digestAt;
    private String lastDigestDate;

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

    public Boolean getDigestOn() {
        return digestOn;
    }

    public void setDigestOn(Boolean digestOn) {
        this.digestOn = digestOn;
    }

    public Boolean getNotifyOn() {
        return notifyOn;
    }

    public void setNotifyOn(Boolean notifyOn) {
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
}
