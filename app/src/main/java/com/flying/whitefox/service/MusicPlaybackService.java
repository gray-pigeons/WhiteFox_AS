package com.flying.whitefox.service;


import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.flying.whitefox.data.model.music.PlayMode;
import com.flying.whitefox.data.model.music.SongData;

import java.io.IOException;
import java.util.Random;

public class MusicPlaybackService extends Service {
    private static final String TAG = "MusicPlaybackService";
    private MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();
    private SongData currentSong;
    private boolean isPrepared = false;
    private OnPlaybackListener playbackListener;
    private PlayMode playMode = PlayMode.SEQUENTIAL; // 默认顺序播放
    private Random random = new Random();

    public interface OnPlaybackListener {
        void onCompletion();
        void onError(String error);
        void onPlayModeChanged(PlayMode playMode);
    }

    public class MusicBinder extends Binder {
        public MusicPlaybackService getService() {
            return MusicPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        setupMediaPlayer();
    }

    private void setupMediaPlayer() {
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            mediaPlayer.start();
            Log.d(TAG, "音乐开始播放");
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            Log.d(TAG, "音乐播放完成");
            if (playMode == PlayMode.SINGLE_LOOP) {
                // 单曲循环模式，重新播放当前歌曲
                if (currentSong != null) {
                    playSong(currentSong);
                }
            } else {
                // 顺序播放或随机播放，通知Fragment播放下一首
                if (playbackListener != null) {
                    playbackListener.onCompletion();
                }
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            String error = "音乐播放错误: what=" + what + ", extra=" + extra;
            Log.e(TAG, error);
            if (playbackListener != null) {
                playbackListener.onError(error);
            }
            return true;
        });
    }

    public void setPlaybackListener(OnPlaybackListener listener) {
        this.playbackListener = listener;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void playSong(SongData song) {
        if (song == null || song.getSecureUrl() == null || song.getSecureUrl().isEmpty()) {
            Log.e(TAG, "歌曲URL为空，无法播放");
            if (playbackListener != null) {
                playbackListener.onError("歌曲URL为空，无法播放");
            }
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.reset();
            currentSong = song;
            isPrepared = false;

            String secureUrl = song.getSecureUrl();
            mediaPlayer.setDataSource(secureUrl);
            mediaPlayer.prepareAsync(); // 异步准备，避免阻塞UI线程
            Log.d(TAG, "开始准备播放音乐: " + song.getName() + ", URL: " + secureUrl);
        } catch (IOException e) {
            Log.e(TAG, "播放音乐失败", e);
            if (playbackListener != null) {
                playbackListener.onError("播放音乐失败: " + e.getMessage());
            }
        }
    }

    public void pause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            Log.d(TAG, "音乐已暂停");
        }
    }

    public void resume() {
        if (!mediaPlayer.isPlaying() && isPrepared) {
            mediaPlayer.start();
            Log.d(TAG, "音乐已恢复播放");
        }
    }

    public void stop() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            isPrepared = false;
            Log.d(TAG, "音乐已停止");
        }
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        if (isPrepared) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (isPrepared) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (isPrepared) {
            mediaPlayer.seekTo(position);
        }
    }

    public SongData getCurrentSong() {
        return currentSong;
    }

    public PlayMode getPlayMode() {
        return playMode;
    }

    public void setPlayMode(PlayMode playMode) {
        this.playMode = playMode;
        Log.d(TAG, "播放模式已更改为: " + playMode.getDescription());
        if (playbackListener != null) {
            playbackListener.onPlayModeChanged(playMode);
        }
    }

    public int getNextSongIndex(int currentIndex, int totalSongs) {
        switch (playMode) {
            case SEQUENTIAL:
                return (currentIndex + 1) % totalSongs;
            case RANDOM:
                // 随机播放，但确保不重复播放当前歌曲（除非只有一首歌）
                if (totalSongs <= 1) {
                    return currentIndex;
                }
                int randomIndex = random.nextInt(totalSongs);
                while (randomIndex == currentIndex) {
                    randomIndex = random.nextInt(totalSongs);
                }
                return randomIndex;
            case SINGLE_LOOP:
                return currentIndex; // 单曲循环，保持当前索引
            default:
                return (currentIndex + 1) % totalSongs;
        }
    }

    public int getPreviousSongIndex(int currentIndex, int totalSongs) {
        switch (playMode) {
            case SEQUENTIAL:
                return (currentIndex - 1 + totalSongs) % totalSongs;
            case RANDOM:
                // 随机播放时，上一首也随机选择
                if (totalSongs <= 1) {
                    return currentIndex;
                }
                int randomIndex = random.nextInt(totalSongs);
                while (randomIndex == currentIndex) {
                    randomIndex = random.nextInt(totalSongs);
                }
                return randomIndex;
            case SINGLE_LOOP:
                return currentIndex; // 单曲循环，保持当前索引
            default:
                return (currentIndex - 1 + totalSongs) % totalSongs;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
