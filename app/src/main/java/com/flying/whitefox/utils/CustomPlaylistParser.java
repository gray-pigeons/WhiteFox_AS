package com.flying.whitefox.utils;

import android.util.Log;

import com.flying.whitefox.data.model.music.PlaylistData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

public class CustomPlaylistParser {
    private static final String TAG = "CustomPlaylistParser";

    /**
     * 解析自定义歌单数据
     *
     * @param playlistJson 歌单JSON数据
     * @return 解析后的PlaylistData对象，如果解析失败则返回null
     */
    public static PlaylistData parseCustomPlaylist(String playlistJson) {
        try {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(playlistJson, JsonObject.class);

            PlaylistData playlistData = new PlaylistData();
            
            // 尝试从不同的位置获取歌单信息
            JsonObject resultObject = null;
            if (jsonObject.has("result")) {
                resultObject = jsonObject.getAsJsonObject("result");
            } else if (jsonObject.has("playlist")) {
                resultObject = jsonObject.getAsJsonObject("playlist");
            } else {
                resultObject = jsonObject;
            }

            // 获取歌单ID和名称
            if (resultObject.has("id")) {
                playlistData.id = resultObject.get("id").getAsInt();
            } else {
                playlistData.id = 0;
            }
            
            if (resultObject.has("name")) {
                playlistData.name = resultObject.get("name").getAsString();
            } else {
                playlistData.name = "导入的歌单";
            }

            // 解析歌曲列表
            JsonArray tracksArray = null;
            if (resultObject.has("tracks") && resultObject.get("tracks").isJsonArray()) {
                tracksArray = resultObject.getAsJsonArray("tracks");
            }

            if (tracksArray != null) {
                List<PlaylistData.Song> songs = new ArrayList<>();

                for (JsonElement element : tracksArray) {
                    if (element.isJsonObject()) {
                        JsonObject songObject = element.getAsJsonObject();
                        PlaylistData.Song song = new PlaylistData.Song();

                        // 歌曲ID
                        song.id = songObject.has("id") ? songObject.get("id").getAsInt() : 0;
                        
                        // 歌曲名称
                        song.name = songObject.has("name") ? songObject.get("name").getAsString() : "未知歌曲";
                        
                        // 歌手信息
                        if (songObject.has("artists") && songObject.getAsJsonArray("artists").size() > 0) {
                            JsonObject artistObject = songObject.getAsJsonArray("artists").get(0).getAsJsonObject();
                            song.ar_name = artistObject.has("name") ? artistObject.get("name").getAsString() : "未知歌手";
                        } else if (songObject.has("ar") && songObject.getAsJsonArray("ar").size() > 0) {
                            JsonObject artistObject = songObject.getAsJsonArray("ar").get(0).getAsJsonObject();
                            song.ar_name = artistObject.has("name") ? artistObject.get("name").getAsString() : "未知歌手";
                        } else if (songObject.has("artist")) {
                            song.ar_name = songObject.get("artist").getAsString();
                        } else if (songObject.has("ar_name")) {
                            song.ar_name = songObject.get("ar_name").getAsString();
                        } else {
                            song.ar_name = "未知歌手";
                        }

                        // 专辑信息
                        if (songObject.has("album") && songObject.get("album").isJsonObject()) {
                            JsonObject albumObject = songObject.getAsJsonObject("album");
                            song.al_name = albumObject.has("name") ? albumObject.get("name").getAsString() : "未知专辑";
                            
                            if (albumObject.has("picUrl")) {
                                song.pic = albumObject.get("picUrl").getAsString();
                            } else {
                                song.pic = "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg";
                            }
                        } else if (songObject.has("al") && songObject.get("al").isJsonObject()) {
                            JsonObject albumObject = songObject.getAsJsonObject("al");
                            song.al_name = albumObject.has("name") ? albumObject.get("name").getAsString() : "未知专辑";
                            
                            if (albumObject.has("picUrl")) {
                                song.pic = albumObject.get("picUrl").getAsString();
                            } else {
                                song.pic = "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg";
                            }
                        } else {
                            song.al_name = "未知专辑";
                            song.pic = "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg";
                        }

                        songs.add(song);
                    }
                }

                playlistData.songs = songs;
            }

            Log.d(TAG, "成功解析自定义歌单，包含 " + (playlistData.songs != null ? playlistData.songs.size() : 0) + " 首歌曲");
            return playlistData;
        } catch (JsonParseException e) {
            Log.e(TAG, "解析自定义歌单数据失败", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "解析自定义歌单时发生未知错误", e);
            return null;
        }
    }

    /**
     * 验证歌单数据是否有效
     *
     * @param playlistData 歌单数据
     * @return 是否有效
     */
    public static boolean isValidPlaylist(PlaylistData playlistData) {
        return playlistData != null &&
                playlistData.songs != null &&
                !playlistData.songs.isEmpty();
    }
}

