package com.flying.whitefox.data.model.music;


public enum PlayMode {
    SEQUENTIAL(0, "顺序播放"),
    RANDOM(1, "随机播放"),
    SINGLE_LOOP(2, "单曲循环");

    private final int mode;
    private final String description;

    PlayMode(int mode, String description) {
        this.mode = mode;
        this.description = description;
    }

    public int getMode() {
        return mode;
    }

    public String getDescription() {
        return description;
    }

    public static PlayMode fromMode(int mode) {
        for (PlayMode playMode : values()) {
            if (playMode.mode == mode) {
                return playMode;
            }
        }
        return SEQUENTIAL; // 默认为顺序播放
    }
}
