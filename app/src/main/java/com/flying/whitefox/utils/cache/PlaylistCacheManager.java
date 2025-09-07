package com.flying.whitefox.utils.cache;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.flying.whitefox.data.model.music.PlaylistData;
import com.google.gson.Gson;

public class PlaylistCacheManager {
    private static final String TAG = "PlaylistCacheManager";
    private static final String PREF_NAME = "music_playlist_cache";
    private static final String CACHE_KEY = "playlist_data";
    private static final String CACHE_TIMESTAMP = "cache_timestamp";
    private static final long CACHE_DURATION = 6 * 60 * 60 * 1000; // 6小时

    private SharedPreferences sharedPreferences;
    private Gson gson;

    public PlaylistCacheManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void savePlaylist(PlaylistData playlist) {
        String playlistJson = gson.toJson(playlist);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(CACHE_KEY, playlistJson);
        editor.putLong(CACHE_TIMESTAMP, System.currentTimeMillis());
        editor.apply();
        Log.d(TAG, "Playlist saved to cache");
    }

    public PlaylistData getPlaylist() {
        // 检查缓存是否过期
        long cacheTime = sharedPreferences.getLong(CACHE_TIMESTAMP, 0);
        if (System.currentTimeMillis() - cacheTime > CACHE_DURATION) {
            Log.d(TAG, "Playlist cache expired");
            return null;
        }

        String playlistJson = sharedPreferences.getString(CACHE_KEY, null);
        if (playlistJson == null) {
            Log.d(TAG, "No playlist found in cache");
            return null;
        }

        try {
            PlaylistData playlist = gson.fromJson(playlistJson, PlaylistData.class);
            Log.d(TAG, "Playlist loaded from cache");
            return playlist;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse cached playlist", e);
            return null;
        }
    }

    public boolean isCacheValid() {
        long cacheTime = sharedPreferences.getLong(CACHE_TIMESTAMP, 0);
        return System.currentTimeMillis() - cacheTime <= CACHE_DURATION;
    }

    public void clearCache() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(CACHE_KEY);
        editor.remove(CACHE_TIMESTAMP);
        editor.apply();
        Log.d(TAG, "Playlist cache cleared");
    }
}

