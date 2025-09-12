package com.flying.whitefox.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.data.model.music.PlayMode;

import java.util.concurrent.atomic.AtomicInteger;

public class MusicPlaybackManager {
    private static final String TAG = "MusicPlaybackManager";

    private final Context context;
    private MusicPlaybackService musicPlaybackService;
    private MusicService musicService;
    private boolean isServiceBound = false;
    private PlaybackStateListener playbackStateListener;
    private PlaylistData currentPlaylist;
    private int currentSongIndex = -1;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public boolean isPlaying() {
        return musicPlaybackService != null && musicPlaybackService.isPlaying();
    }

    public interface PlaybackStateListener {
        void onPlaybackStateChanged(PlaybackState state);
        void onPlaylistUpdated(PlaylistData playlist);
        void onSongChanged(int songIndex);
        void onError(String error);
    }

    public static class PlaybackState {
        public boolean isPlaying;
        public int currentPosition;
        public int duration;
        public PlayMode playMode;

        public PlaybackState(boolean isPlaying, int currentPosition, int duration, PlayMode playMode) {
            this.isPlaying = isPlaying;
            this.currentPosition = currentPosition;
            this.duration = duration;
            this.playMode = playMode;
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlaybackService.MusicBinder binder = (MusicPlaybackService.MusicBinder) service;
            musicPlaybackService = binder.getService();
            isServiceBound = true;

            // 设置播放完成监听器
            musicPlaybackService.setPlaybackListener(new MusicPlaybackService.OnPlaybackListener() {
                @Override
                public void onCompletion() {
                    playNextSong();
                    // 确保在播放完成后更新UI状态
                    updatePlaybackState();
                }

                @Override
                public void onError(String error) {
                    if (playbackStateListener != null) {
                        playbackStateListener.onError(error);
                    }
                    // 发生错误时也更新UI状态
                    updatePlaybackState();
                }

                @Override
                public void onPlayModeChanged(PlayMode playMode) {
                    if (playbackStateListener != null) {
                        updatePlaybackState();
                    }
                }
            });

            // 初始化MusicService
            musicService = musicPlaybackService.getMusicService();

            Log.d(TAG, "音乐播放服务已连接");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            Log.d(TAG, "音乐播放服务已断开");
        }
    };

    public MusicPlaybackManager(Context context) {
        this.context = context;
        bindService();
    }

    private void bindService() {
        Intent intent = new Intent(context, MusicPlaybackService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        context.startService(intent);
    }

    public void setPlaybackStateListener(PlaybackStateListener listener) {
        this.playbackStateListener = listener;
    }

    public void loadPlaylist(int playlistId, boolean forceRefresh) {
        if (musicService == null) return;
        musicService.cancelCurrentRequest();
        musicService.getPlaylistAsync(playlistId)
                .thenAccept(playlist -> {
                    currentPlaylist = playlist;
                    if (playbackStateListener != null) {
                        playbackStateListener.onPlaylistUpdated(playlist);
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "加载歌单失败", throwable);
                    if (playbackStateListener != null) {
                        playbackStateListener.onError("加载歌单失败: " + throwable.getMessage());
                    }
                    return null;
                });
    }

    public void playSong(int songIndex) {
        if (currentPlaylist == null ||
                currentPlaylist.songs == null ||
                songIndex < 0 ||
                songIndex >= currentPlaylist.songs.size() ||
                !isServiceBound) {
            return;
        }

        currentSongIndex = songIndex;

        if (playbackStateListener != null) {
            playbackStateListener.onSongChanged(songIndex);
        }

        PlaylistData.Song song = currentPlaylist.songs.get(songIndex);
        musicService.getSongUrl(song.id)
            .thenAccept(songData -> {
                if (songData != null && (songData.status == 200 || 
                    (songData.getUrl() != null && !songData.getUrl().isEmpty()))) {
                    // 成功获取歌曲链接，重置失败计数器
                    consecutiveFailures.set(0);
                    
                    if (musicPlaybackService != null) {
                        musicPlaybackService.playSong(songData);
                        updatePlaybackState();
                    } else {
                        Log.e(TAG, "MusicPlaybackService为空");
                        if (playbackStateListener != null) {
                            playbackStateListener.onError("播放服务未初始化");
                        }
                    }
                } else {
                    Log.e(TAG, "无法获取歌曲播放链接: " + (songData != null ? songData.getMsg() : "未知错误"));
                    if (playbackStateListener != null) {
                        playbackStateListener.onError("无法获取歌曲播放链接");
                    }
                    handleSongPlaybackFailure();
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "获取歌曲链接失败", throwable);
                if (playbackStateListener != null) {
                    playbackStateListener.onError("获取歌曲链接失败: " + throwable.getMessage());
                }
                handleSongPlaybackFailure();
                return null;
            });
    }

    public void togglePlayPause() {
        if (!isServiceBound) return;

        if (musicPlaybackService.isPlaying()) {
            musicPlaybackService.pause();
        } else {
            if (currentSongIndex >= 0) {
                // 如果当前有歌曲，直接恢复播放
                musicPlaybackService.resume();
            } else if (currentPlaylist != null && currentPlaylist.songs != null && !currentPlaylist.songs.isEmpty()) {
                // 如果没有当前歌曲但有播放列表，播放第一首歌
                playSong(0);
            }
        }
        updatePlaybackState();
    }

    public void playNextSong() {
        if (currentPlaylist == null ||
                currentPlaylist.songs == null ||
                currentPlaylist.songs.isEmpty() ||
                !isServiceBound) {
            return;
        }

        int nextIndex;
        if (musicPlaybackService != null) {
            nextIndex = musicPlaybackService.getNextSongIndex(currentSongIndex, currentPlaylist.songs.size());
        } else {
            nextIndex = (currentSongIndex + 1) % currentPlaylist.songs.size();
        }

        playSong(nextIndex);
    }

    public void playPreviousSong() {
        if (currentPlaylist == null ||
                currentPlaylist.songs == null ||
                currentPlaylist.songs.isEmpty() ||
                !isServiceBound) {
            return;
        }

        int previousIndex;
        if (musicPlaybackService != null) {
            previousIndex = musicPlaybackService.getPreviousSongIndex(currentSongIndex, currentPlaylist.songs.size());
        } else {
            previousIndex = (currentSongIndex - 1 + currentPlaylist.songs.size()) % currentPlaylist.songs.size();
        }

        playSong(previousIndex);
    }

    public void setPlayMode(PlayMode playMode) {
        if (isServiceBound) {
            musicPlaybackService.setPlayMode(playMode);
            updatePlaybackState();
        }
    }

    public void seekTo(int position) {
        if (isServiceBound) {
            musicPlaybackService.seekTo(position);
            updatePlaybackState();
        }
    }

    public void updatePlaybackState() {
        if (isServiceBound && playbackStateListener != null) {
            boolean isPlaying = musicPlaybackService.isPlaying();
            int currentPosition = isPlaying ? musicPlaybackService.getCurrentPosition() : 0;
            int duration = isPlaying ? musicPlaybackService.getDuration() : 0;
            PlayMode playMode = musicPlaybackService.getPlayMode();

            PlaybackState state = new PlaybackState(isPlaying, currentPosition, duration, playMode);
            playbackStateListener.onPlaybackStateChanged(state);
            Log.d(TAG, "更新播放状态: " + state.isPlaying);
        }
    }

    public PlaylistData getCurrentPlaylist() {
        return currentPlaylist;
    }

    public int getCurrentSongIndex() {
        return currentSongIndex;
    }

    public void destroy() {
        if (isServiceBound) {
            context.unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void handleSongPlaybackFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= 3) {
            Log.e(TAG, "连续三次播放失败，停止尝试");
            if (playbackStateListener != null) {
                playbackStateListener.onError("连续三次播放失败，停止尝试");
            }
        } else {
            Log.e(TAG, "播放失败，尝试重新播放");
            playSong(currentSongIndex);
        }
    }
}
