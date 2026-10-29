package com.appliance.itwb.wb.dto;

import java.io.Serializable;

/**
 * 成员 DTO（wire 层）。字段与前端 S.members[] 对应，无枚举转换。
 */
public class MemberDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String role;
    private String color;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
