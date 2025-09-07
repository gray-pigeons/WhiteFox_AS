package com.flying.whitefox.data.model.music;

import java.io.Serializable;

public class SongData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public int status;
    public String msg;
    public String name;
    public String ar_name;
    public String al_name;
    public String pic;
    public String url;
    public String lyric;
    public String tlyric;
    public String size;
    public String level;

    // Getters and Setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAr_name() {
        return ar_name;
    }

    public void setAr_name(String ar_name) {
        this.ar_name = ar_name;
    }

    public String getAl_name() {
        return al_name;
    }

    public void setAl_name(String al_name) {
        this.al_name = al_name;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getTlyric() {
        return tlyric;
    }

    public void setTlyric(String tlyric) {
        this.tlyric = tlyric;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * 获取安全的HTTPS URL
     * @return 安全的URL
     */
    public String getSecureUrl() {
        if (url != null && url.startsWith("http://")) {
            return url.replaceFirst("http://", "https://");
        }
        return url;
    }
}

