package com.flying.whitefox.data.model.home;

public class GridItemData {
    private int id;
    private String text;
    private String icon;
    private String url;
    private String color;
    private boolean isShow;

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isShow() { return isShow; }
    public void setShow(boolean show) { isShow = show; }
}
