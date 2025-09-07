package com.flying.whitefox.data.model.music;

import java.io.Serializable;
import java.util.List;

public class PlaylistData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public int id;
    public String name;
    public List<Song> songs;

    public static class Song implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public String id;
        public String name;
        public String ar_name;  // 艺术家名称
        public String al_name;  // 专辑名称
        public String pic;      // 专辑图片URL

        // Getters and setters
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
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }
}

