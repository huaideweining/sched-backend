package com.appliance.itwb.wb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作台事项每日完成明细实体（周/月复盘 LONG_TERM 推进记录），与表 WB_ITEM_LOG 一一对应。
 *
 * <p><b>文件作用</b>：承载每日进度、阻塞点与情绪落库字段。时间列用 TIMESTAMP(6) ↔ LocalDateTime。</p>
 * <p><b>主键</b>：{@code IdType.ASSIGN_UUID}（由 {@code ItwbIdentifierGenerator} 在应用层生成），
 * 与现役 Wb* 实体保持一致，绝不可回退为归档旧版的 {@code IdType.INPUT}
 * ——后者在主键为 null 时会显式写 NULL，触发 Oracle ORA-01400。</p>
 * <p><b>审计列</b>：表 WB_ITEM_LOG 实际只有 CREATE_TIME / UPDATE_TIME，没有 CREATE_BY / UPDATE_BY，
 * 故这两个属性标记 {@code @TableField(exist = false)}：保留 Java 侧读写 API，
 * 但不参与 MyBatis-Plus 列映射，避免生成非法的 INSERT/SELECT 列（ORA-00904）。</p>
 */
@TableName("WB_ITEM_LOG")
public class WbItemLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String itemId;
    private LocalDateTime logDate;
    private Double progressPct;
    private String content;
    private String blocker;
    private String mood;

    /** 表 WB_ITEM_LOG 无 CREATE_BY 列：不参与 SQL 列映射（仅保留 Java 侧 API）。 */
    @TableField(exist = false)
    private String createBy;
    private LocalDateTime createTime;
    /** 表 WB_ITEM_LOG 无 UPDATE_BY 列：不参与 SQL 列映射（仅保留 Java 侧 API）。 */
    @TableField(exist = false)
    private String updateBy;
    private LocalDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public LocalDateTime getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDateTime logDate) {
        this.logDate = logDate;
    }

    public Double getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(Double progressPct) {
        this.progressPct = progressPct;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBlocker() {
        return blocker;
    }

    public void setBlocker(String blocker) {
        this.blocker = blocker;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
