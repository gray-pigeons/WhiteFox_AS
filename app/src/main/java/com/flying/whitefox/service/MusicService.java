package com.flying.whitefox.service;


import static java.security.AccessController.getContext;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.data.model.music.QualityLevel;
import com.flying.whitefox.data.model.music.SongData;
import com.flying.whitefox.utils.cache.PlaylistCacheManager;
import com.flying.whitefox.utils.config.RequestURLConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MusicService {
    private static final String TAG = "MusicService";
    private static final String PROXY_URL = RequestURLConfig.getVipNeteaseMusic;
    private static final OkHttpClient http_client = new OkHttpClient();
    private static final int MAX_RETRIES = 3; // 进一步减少最大重试次数
    private static final long RETRY_DELAY = 3000; // 减少重试延迟（毫秒）3秒

    // 用于存储当前正在进行的请求，以便可以取消
    private static Call currentCall;
    // 用于跟踪请求ID，防止重复处理
    private static final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private static volatile int currentRequestId = -1;

    private PlaylistCacheManager cacheManager; // 缓存管理器


    public void initializeCacheManager(android.content.Context context) {
        cacheManager = new PlaylistCacheManager(context);
    }


    /**
     * 异步获取歌单信息
     * @param playlistId 歌单ID
     */
    public CompletableFuture<PlaylistData> getPlaylistAsync(int playlistId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlaylistData cachedPlaylist = LoadPlayListByCache();
                if (cachedPlaylist != null) {
                    return cachedPlaylist;
                }
                Future<PlaylistData> future = getPlaylist(playlistId);
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "加载歌单失败", e);
                throw new RuntimeException(e);
            }
        }).thenCompose(playlist -> {
            if (playlist != null && playlist.songs != null && !playlist.songs.isEmpty()) {
                // 保存到缓存
                synchronized (this) {
                    if (cacheManager != null) {
                        cacheManager.savePlaylist(playlist);
                    }
                }
                return CompletableFuture.completedFuture(playlist);
            } else {

                // 尝试从缓存加载
                PlaylistData cachedPlaylist = LoadPlayListByCache();
                if (cachedPlaylist != null) {
                    Log.d(TAG, "网络加载失败，从缓存加载歌单");
                    return CompletableFuture.completedFuture(cachedPlaylist);
                } else {
                    throw new RuntimeException("获取到的歌单为空或没有歌曲");
                }
            }
        });
    }

    /**
     * 从缓存中加载歌单
     *
     * @return 缓存的歌单数据，如果不存在或已过期则返回null
     */
    public PlaylistData LoadPlayListByCache() {
        // 检查cacheManager是否已初始化
        if (cacheManager == null) {
            Log.w(TAG, "CacheManager未初始化，无法从缓存加载歌单");
            return null;
        }

        PlaylistData cachedPlaylist = null;

        try {
            // 判断是否在主线程
            if (isMainThread()) {
                // 主线程直接访问（不上锁）
                cachedPlaylist = cacheManager.getPlaylist();
            } else {
                // 非主线程访问（上锁）
                synchronized (this) {
                    cachedPlaylist = cacheManager.getPlaylist();
                }
            }

            if (cachedPlaylist != null) {
                Log.d(TAG, "成功从缓存加载歌单: " + cachedPlaylist.name);
            } else {
                Log.d(TAG, "缓存中没有找到有效的歌单数据");
            }
        } catch (Exception e) {
            Log.e(TAG, "从缓存加载歌单时发生错误", e);
            return null;
        }

        return cachedPlaylist;
    }

    // 辅助方法：判断是否在主线程
    private boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }


    /**
     * 获取歌单信息
     *
     * @param playlistId 歌单ID
     */
    public Future<PlaylistData> getPlaylist(int playlistId) {
        // 生成新的请求ID
        int requestId = requestIdCounter.incrementAndGet();
        currentRequestId = requestId;
        
        Log.d(TAG, "开始新请求，ID: " + requestId);
        
        // 取消之前的请求（如果有的话）
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            Log.d(TAG, "取消了之前的请求");
        }

        CompletableFuture<PlaylistData> future = new CompletableFuture<>();
        getPlaylistWithRetry(playlistId, 0, future, requestId);
        return future;
    }

    /**
     * 带重试机制的获取歌单信息
     * @param playlistId 歌单ID
     * @param retryCount 当前重试次数
     * @param future     结果Future
     * @param requestId  请求ID
     */
    private static void getPlaylistWithRetry(int playlistId, int retryCount, CompletableFuture<PlaylistData> future, int requestId) {
        // 检查请求是否仍然有效
        if (requestId != currentRequestId) {
            Log.d(TAG, "请求已过期，ID: " + requestId + ", 当前ID: " + currentRequestId);
            future.complete(null);
            return;
        }
        
        // 使用正确的网易云音乐API URL
        String url = "https://music.163.com/api/playlist/detail?id=" + playlistId;
//        String url = "https://whitefox.3yu3.top/wymusiclist.json";
        Log.i(TAG, "getPlaylist url: " + url + " (attempt " + (retryCount + 1) + ")");

        // 构建带有必要请求头的请求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .build();

        currentCall = http_client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 再次检查请求是否仍然有效
                if (requestId != currentRequestId) {
                    Log.d(TAG, "请求已过期(onFailure)，ID: " + requestId + ", 当前ID: " + currentRequestId);
                    future.complete(null);
                    return;
                }
                
                if (call.isCanceled()) {
                    Log.d(TAG, "请求被取消");
                    future.complete(null);
                    return;
                }

                Log.e(TAG, "获取歌单信息失败", e);
                if (retryCount < MAX_RETRIES) {
                    retryGetPlaylist(playlistId, retryCount, future, requestId);
                } else {
                    future.complete(null);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // 检查请求是否仍然有效
                if (requestId != currentRequestId) {
                    Log.d(TAG, "请求已过期(onResponse)，ID: " + requestId + ", 当前ID: " + currentRequestId);
                    future.complete(null);
                    return;
                }
                
                if (call.isCanceled()) {
                    Log.d(TAG, "请求被取消");
                    future.complete(null);
                    return;
                }

                if (!response.isSuccessful()) {
                    Log.e(TAG, "获取歌单信息失败: " + response.code());
                    if (retryCount < MAX_RETRIES) {
                        retryGetPlaylist(playlistId, retryCount, future, requestId);
                    } else {
                        future.complete(null);
                    }
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);

                    // 检查返回的数据结构
                    Log.d(TAG, "Response data: " + responseData.substring(0, Math.min(200, responseData.length())) + "...");

                    // 检查是否服务器繁忙
                    String msg = jsonObject.optString("msg", "");
                    int code = jsonObject.optInt("code", 200);
                    if (code == -447 || (msg.contains("服务器忙碌") || msg.contains("请稍后再试"))) {
                        Log.w(TAG, "服务器忙碌，准备重试...");
                        if (retryCount < MAX_RETRIES) {
                            retryGetPlaylist(playlistId, retryCount, future, requestId);
                        } else {
                            future.complete(null);
                        }
                        return;
                    }

                    PlaylistData playlistData = new PlaylistData();
                    playlistData.id = playlistId;

                    // 解析歌单详情 - 修复嵌套结构访问
                    JSONObject resultObj = jsonObject.optJSONObject("result");
                    if (resultObj != null) {
                        JSONObject playlistObj = resultObj.optJSONObject("playlist");
                        if (playlistObj == null) {
                            // 如果playlist不存在，直接使用result对象
                            playlistObj = resultObj;
                        }

                        // 获取歌单ID和名称
                        playlistData.id = playlistObj.optInt("id", playlistId);
                        playlistData.name = playlistObj.optString("name", "未知歌单");

                        // 解析歌曲列表
                        JSONArray tracksArray = playlistObj.optJSONArray("tracks");
                        if (tracksArray != null && tracksArray.length() > 0) {
                            List<PlaylistData.Song> songs = new ArrayList<>();

                            for (int i = 0; i < tracksArray.length(); i++) {
                                JSONObject trackObj = tracksArray.getJSONObject(i);
                                PlaylistData.Song song = new PlaylistData.Song();
                                song.id = trackObj.optString("id", "0");
                                song.name = trackObj.optString("name", "未知歌曲");

                                // 解析艺术家信息 - 支持多种格式
                                String artistName = "未知歌手";
                                JSONArray artistsArray = trackObj.optJSONArray("ar");
                                if (artistsArray != null && artistsArray.length() > 0) {
                                    JSONObject artistObj = artistsArray.getJSONObject(0);
                                    artistName = artistObj.optString("name", "未知歌手");
                                } else {
                                    artistsArray = trackObj.optJSONArray("artists");
                                    if (artistsArray != null && artistsArray.length() > 0) {
                                        JSONObject artistObj = artistsArray.getJSONObject(0);
                                        artistName = artistObj.optString("name", "未知歌手");
                                    }
                                }
                                song.ar_name = artistName;

                                // 解析专辑信息 - 支持多种格式
                                String albumName = "未知专辑";
                                String albumPic = "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg";

                                JSONObject albumObj = trackObj.optJSONObject("al");
                                if (albumObj == null) {
                                    albumObj = trackObj.optJSONObject("album");
                                }

                                if (albumObj != null) {
                                    albumName = albumObj.optString("name", "未知专辑");
                                    albumPic = albumObj.optString("picUrl", albumPic);
                                }

                                song.al_name = albumName;
                                song.pic = albumPic;

                                songs.add(song);
                            }

                            playlistData.songs = songs;
                            Log.d(TAG, "成功解析歌单: " + playlistData.name + ", 包含 " + songs.size() + " 首歌曲");
                            future.complete(playlistData);
                        } else {
                            Log.e(TAG, "解析歌单信息失败，tracks数组为空或不存在");
                            if (retryCount < MAX_RETRIES) {
                                retryGetPlaylist(playlistId, retryCount, future, requestId);
                            } else {
                                future.complete(null);
                            }
                        }
                    } else {
                        Log.e(TAG, "解析歌单信息失败，result对象为空");
                        if (retryCount < MAX_RETRIES) {
                            retryGetPlaylist(playlistId, retryCount, future, requestId);
                        } else {
                            future.complete(null);
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析歌单信息失败", e);
                    if (retryCount < MAX_RETRIES) {
                        retryGetPlaylist(playlistId, retryCount, future, requestId);
                    } else {
                        future.complete(null);
                    }
                }
            }
        });
    }

    /**
     * 重试获取歌单
     * @param playlistId 歌单ID
     * @param retryCount 当前重试次数
     * @param future     结果Future
     * @param requestId  请求ID
     */
    private static void retryGetPlaylist(int playlistId, int retryCount, CompletableFuture<PlaylistData> future, int requestId) {
        // 检查请求是否仍然有效
        if (requestId != currentRequestId) {
            Log.d(TAG, "请求已过期(retry)，ID: " + requestId + ", 当前ID: " + currentRequestId);
            future.complete(null);
            return;
        }
        
        Log.d(TAG, "将在 " + RETRY_DELAY + "ms 后进行第 " + (retryCount + 1) + " 次重试，请求ID: " + requestId);

        // 使用Handler来处理延迟和重试，确保在主线程中调度
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 再次检查请求是否仍然有效
                if (requestId != currentRequestId) {
                    Log.d(TAG, "请求已过期(延迟后)，ID: " + requestId + ", 当前ID: " + currentRequestId);
                    future.complete(null);
                    return;
                }
                
                // 检查future是否已经被取消
                if (!future.isCancelled() && !future.isDone()) {
                    getPlaylistWithRetry(playlistId, retryCount + 1, future, requestId);
                }
            }
        }, RETRY_DELAY);
    }

    /**
     * 取消当前正在进行的请求
     */
    public static void cancelCurrentRequest() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            Log.d(TAG, "已取消当前请求");
        }
    }

    /**
     * 获取歌曲播放链接
     *
     * @param songId 网易云歌曲 ID
     * @param level  音质级别
     */
    public static CompletableFuture<SongData> getSongUrl(String songId, QualityLevel level) {
        CompletableFuture<SongData> future = new CompletableFuture<>();
        if (songId.isEmpty() || songId.equals("0")) {
            future.complete(null);
            return future;
        }
        // 尝试多个代理URL
        String[] proxyUrls = {
                PROXY_URL + "?ids=" + songId + "&level=" + level.getValue() + "&type=json",
                RequestURLConfig.getMusicAnalysisAggregation + "?id=" + songId + "&media=netease&type=url",
        };

        tryNextProxyUrl(proxyUrls, 0, future, songId, level.getValue());
        return future;
    }

    // 添加一个重载方法，使用默认音质
    public static CompletableFuture<SongData> getSongUrl(String songId) {
        return getSongUrl(songId, QualityLevel.STANDARD);
    }

    private static void tryNextProxyUrl(String[] proxyUrls, int index, CompletableFuture<SongData> future,
                                        String songId, String level) {
        if (index >= proxyUrls.length) {
            if (songId.contains("-")) {
                songId = songId.replace("-", "");
                tryNextProxyUrl(proxyUrls, 0, future, songId, level);
                return;
            }

            Log.e(TAG, "所有代理URL都尝试失败");
            future.complete(null);
            return;
        }

        String url = proxyUrls[index];
        Log.i(TAG, "尝试获取歌曲链接 URL[" + index + "]: " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .addHeader("Referer", "https://music.163.com")
                .build();

        String finalSongId = songId;
        http_client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "获取歌曲链接失败 URL[" + index + "]: " + url, e);
                // 尝试下一个URL
                tryNextProxyUrl(proxyUrls, index + 1, future, finalSongId, level);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "获取歌曲链接失败 URL[" + index + "]: " + url + ", code: " + response.code());
                    // 尝试下一个URL
                    tryNextProxyUrl(proxyUrls, index + 1, future, finalSongId, level);
                    return;
                }

                try {
                    assert response.body() != null;
                    String responseData = response.body().string();
                    Log.d(TAG, "Song URL response data[" + index + "]: " + responseData);

                    // 检查响应是否有效
                    if (responseData.contains("\"status\": 400") || responseData.contains("信息获取不完整")) {
                        Log.e(TAG, "获取歌曲链接失败，信息不完整 URL[" + index + "]");
                        // 尝试下一个URL
                        tryNextProxyUrl(proxyUrls, index + 1, future, finalSongId, level);
                        return;
                    }

                    JSONObject jsonObject = new JSONObject(responseData);
                    SongData songData = new SongData();

                    // 根据不同的响应格式解析数据
                    if (jsonObject.has("data") && jsonObject.get("data") instanceof JSONArray) {
                        // 处理api.bzqll.com格式的响应
                        JSONArray dataArray = jsonObject.getJSONArray("data");
                        if (dataArray.length() > 0) {
                            JSONObject dataObj = dataArray.getJSONObject(0);
                            songData.status = 200;
                            songData.name = dataObj.optString("songname", "");
                            songData.ar_name = dataObj.optString("artistname", "");
                            songData.al_name = dataObj.optString("albumname", "");
                            songData.pic = dataObj.optString("pic", "");
                            songData.url = dataObj.optString("url", "");
                        }
                    } else {
                        // 处理标准格式响应
                        songData.status = jsonObject.optInt("status", 0);
                        songData.msg = jsonObject.optString("msg", "");
                        songData.al_name = jsonObject.optString("al_name", "");
                        songData.ar_name = jsonObject.optString("ar_name", "");
                        songData.level = jsonObject.optString("level", "");
                        songData.lyric = jsonObject.optString("lyric", "");
                        songData.name = jsonObject.optString("name", "");
                        songData.pic = jsonObject.optString("pic", "");
                        songData.size = jsonObject.optString("size", "");
                        songData.tlyric = jsonObject.optString("tlyric", "");
                        songData.url = jsonObject.optString("url", "");
                    }

                    if (songData.status == 200 || (songData.url != null && !songData.url.isEmpty())) {
                        future.complete(songData);
                    } else {
                        Log.e(TAG, "解析歌曲信息失败 URL[" + index + "]");
                        // 尝试下一个URL
                        tryNextProxyUrl(proxyUrls, index + 1, future, finalSongId, level);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析歌曲信息失败 URL[" + index + "]", e);
                    // 尝试下一个URL
                    tryNextProxyUrl(proxyUrls, index + 1, future, finalSongId, level);
                }
            }
        });
    }

    /**
     * 搜索歌曲
     *
     * @param keyword 搜索关键词
     */
    public static Future<List<PlaylistData.Song>> searchSong(String keyword) {
        CompletableFuture<List<PlaylistData.Song>> future = new CompletableFuture<>();

        String url = "https://api.bzqll.com/music/tencent/search?key=" + keyword + "&limit=20&type=song";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();

        http_client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                Log.e(TAG, "搜索歌曲失败", e);
                future.complete(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "搜索歌曲失败: " + response.code());
                    future.complete(null);
                    return;
                }

                try {
                    assert response.body() != null;
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);
                    // 检查返回的数据结构
                    Log.d(TAG, "Search response data: " + responseData);

                    JSONArray songsArray = jsonObject.optJSONArray("data");
                    List<PlaylistData.Song> songs = new ArrayList<>();

                    if (songsArray != null) {
                        for (int i = 0; i < songsArray.length(); i++) {
                            JSONObject songObj = songsArray.getJSONObject(i);
                            PlaylistData.Song song = new PlaylistData.Song();
                            song.id = songObj.optString("songid", "0");
                            song.name = songObj.optString("songname", "未知歌曲");
                            song.ar_name = songObj.optString("artistname", "未知歌手");
                            song.al_name = songObj.optString("albumname", "未知专辑");
                            song.pic = songObj.optString("pic", "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg");
                            songs.add(song);
                        }
                    }

                    future.complete(songs);
                } catch (JSONException e) {
                    Log.e(TAG, "解析搜索结果失败", e);
                    future.complete(null);
                }
            }
        });

        return future;
    }


    /**
     * 将HTTP URL转换为HTTPS URL
     *
     * @param url 原始URL
     * @return 安全的HTTPS URL
     */
    private static String toHttpsUrl(String url) {
        if (url != null && url.startsWith("http://")) {
            return url.replaceFirst("http://", "https://");
        }
        return url;
    }
}

