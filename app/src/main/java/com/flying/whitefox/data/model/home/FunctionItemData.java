package com.flying.whitefox.data.model.home;

public class FunctionItemData {
    private String name;
    private int iconResId; // 或者使用URL如果是网络图片

    public FunctionItemData(String name, int iconResId) {
        this.name = name;
        this.iconResId = iconResId;
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getIconResId() { return iconResId; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }
}
