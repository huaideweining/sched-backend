package com.appliance.itwb.wb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作台复盘落库实体（R4：KPT/GRAI 持久化）。与表 WB_REVIEW 一一对应。
 *
 * <p><b>文件作用</b>：每期（周/月）复盘一条记录，user_id 隔离；
 * KEEP/PROBLEM/TRY_C 为周复盘 KPT 模板；GOAL/RESULT/ANALYSIS/INSIGHT 为月复盘 GRAI 模板；
 * DATA_JSON 归档当期的计算快照（VARCHAR2(4000)，超出截断）。</p>
 * <p><b>主键</b>：{@code IdType.ASSIGN_UUID}（由 {@code ItwbIdentifierGenerator} 在应用层生成），
 * 与现役 Wb* 实体保持一致，绝不可回退为归档旧版的 {@code IdType.INPUT}
 * ——后者在主键为 null 时会显式写 NULL，触发 Oracle ORA-01400。</p>
 * <p><b>审计列</b>：表 WB_REVIEW 实际只有 CREATE_TIME / UPDATE_TIME，没有 CREATE_BY / UPDATE_BY，
 * 故这两个属性标记 {@code @TableField(exist = false)}：保留 Java 侧读写 API，
 * 但不参与 MyBatis-Plus 列映射，避免生成非法的 INSERT/SELECT 列（ORA-00904）。</p>
 */
@TableName("WB_REVIEW")
public class WbReview implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String reviewType;   // WEEKLY / MONTHLY
    private String periodKey;    // yyyy-Www / yyyy-MM
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String keep;
    private String problem;
    @TableField("TRY_C")
    private String tryField;
    private String goal;
    private String result;
    private String analysis;
    private String insight;
    private String dataJson;     // 归档当期计算快照（VARCHAR2(4000)，超出截断）

    /** 表 WB_REVIEW 无 CREATE_BY 列：不参与 SQL 列映射（仅保留 Java 侧 API）。 */
    @TableField(exist = false)
    private String createBy;
    private LocalDateTime createTime;
    /** 表 WB_REVIEW 无 UPDATE_BY 列：不参与 SQL 列映射（仅保留 Java 侧 API）。 */
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

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getKeep() {
        return keep;
    }

    public void setKeep(String keep) {
        this.keep = keep;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getTryField() {
        return tryField;
    }

    public void setTryField(String tryField) {
        this.tryField = tryField;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getInsight() {
        return insight;
    }

    public void setInsight(String insight) {
        this.insight = insight;
    }

    public String getDataJson() {
        return dataJson;
    }

    public void setDataJson(String dataJson) {
        this.dataJson = dataJson;
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
