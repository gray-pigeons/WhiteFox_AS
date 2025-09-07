package com.flying.whitefox.data.model.music;


/**
 * 音乐音质级别枚举
 */
public enum QualityLevel {
    /**
     * 标准音质
     */
    STANDARD("standard", "标准音质"),

    /**
     * 极高音质
     */
    EXHIGH("exhigh", "极高音质"),

    /**
     * 无损音质
     */
    LOSSLESS("lossless", "无损音质"),

    /**
     * Hi-Res音质
     */
    HIRES("hires", "Hi-Res音质"),

    /**
     * 高清环绕声
     */
    JYEFFECT("jyeffect", "高清环绕声"),

    /**
     * 沉浸环绕声
     */
    SKY("sky", "沉浸环绕声"),

    /**
     * 超清母带
     */
    JYMASTER("jymaster", "超清母带");

    private final String value;
    private final String description;

    QualityLevel(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return value;
    }
}

