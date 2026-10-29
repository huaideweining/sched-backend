package com.appliance.itwb.wb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 复盘保存/查询 DTO（wire 层，R4）。
 *
 * <p><b>字段约定</b>：reviewType=WEEKLY|MONTHLY；periodKey 可选（留空由服务端按当期计算）；
 * keep/problem/try 为周复盘 KPT；goal/result/analysis/insight 为月复盘 GRAI；
 * savedAt/windowStart/windowEnd 仅响应返回。JSON 键 {@code try} 通过 @JsonProperty 映射（Java 关键字）。</p>
 */
public class ReviewSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String reviewType;
    private String periodKey;
    private String keep;
    private String problem;
    @JsonProperty("try")
    private String tryField;
    private String goal;
    private String result;
    private String analysis;
    private String insight;
    private String savedAt;
    private String windowStart;
    private String windowEnd;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(String savedAt) {
        this.savedAt = savedAt;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }
}
