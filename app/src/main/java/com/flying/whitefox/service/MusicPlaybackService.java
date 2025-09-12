package com.flying.whitefox.service;


import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.flying.whitefox.MainActivity;
import com.flying.whitefox.data.model.music.PlayMode;
import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.data.model.music.SongData;

import java.io.IOException;
import java.util.Random;

public class MusicPlaybackService extends Service {
    private static final String TAG = "MusicPlaybackService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final String CHANNEL_NAME = "音乐播放";
    private static final String CHANNEL_DESCRIPTION = "音乐播放控制通知";

    // 通知动作
    private static final String ACTION_PAUSE = "com.flying.whitefox.action.PAUSE";
    private static final String ACTION_PLAY = "com.flying.whitefox.action.PLAY";
    private static final String ACTION_NEXT = "com.flying.whitefox.action.NEXT";
    private static final String ACTION_PREVIOUS = "com.flying.whitefox.action.PREVIOUS";
    private static final String ACTION_STOP = "com.flying.whitefox.action.STOP";

    private MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();
    private com.flying.whitefox.data.model.music.PlaylistData.Song currentSong;
    private boolean isPrepared = false;
    private OnPlaybackListener playbackListener;
    private PlayMode playMode = PlayMode.SEQUENTIAL; // 默认顺序播放
    private final Random random = new Random();

    // 添加MusicService实例
    private MusicService musicService;

    // 通知相关
    private NotificationManager notificationManager;
    private Bitmap defaultAlbumArt;

    public PlaylistData.Song getCurrentPlayingSong() {
        return currentSong;
    }

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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        // 初始化MusicService
        musicService = new MusicService();
        musicService.initializeCacheManager(this);
        setupMediaPlayer();

        // 初始化通知管理器
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        // 创建默认专辑图片
        defaultAlbumArt = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_media_play);

        // 注册广播接收器处理通知动作 (修复Android版本兼容性问题)
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_STOP);

        // 根据Android版本选择合适的注册方式
        // Android 8.0及以上版本支持 RECEIVER_NOT_EXPORTED 标志
        registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void setupMediaPlayer() {
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            mediaPlayer.start();
            Log.d(TAG, "音乐开始播放");
            // 使用Handler确保在下一帧更新UI
            new android.os.Handler().post(() -> {
                // 显示通知
                showNotification();
                // 立即通知播放状态改变
                if (playbackListener != null) {
                    playbackListener.onPlayModeChanged(playMode);
                }
            });
        });

        mediaPlayer.setOnCompletionListener(mp ->

        {
            Log.d(TAG, "音乐播放完成");
            if (playMode == PlayMode.SINGLE_LOOP) {
                // 单曲循环模式，重新播放当前歌曲
                if (currentSong != null) {
                    // 注意：这里需要创建一个SongData对象来播放
                    SongData songData = new SongData();
                    // 这里只是示例，实际项目中需要完善数据转换
                    showNotification(); // 更新通知
                }
            } else {
                // 顺序播放或随机播放，通知Fragment播放下一首
                if (playbackListener != null) {
                    playbackListener.onCompletion();
                }
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) ->

        {
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 确保服务在内存不足时不会被轻易杀死
        return START_STICKY;
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
            // 保存当前歌曲信息用于通知显示
            // 注意：这里需要创建一个临时的PlaylistData.Song对象来显示通知
            com.flying.whitefox.data.model.music.PlaylistData.Song notificationSong = new com.flying.whitefox.data.model.music.PlaylistData.Song();
            notificationSong.setName(song.getName());
            notificationSong.setAr_name(song.getAr_name());
            notificationSong.setPic(song.getPic());
            currentSong = notificationSong;

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
            showNotification(); // 更新通知
        }
    }

    public void resume() {
        if (!mediaPlayer.isPlaying() && isPrepared) {
            mediaPlayer.start();
            Log.d(TAG, "音乐已恢复播放");
            showNotification(); // 更新通知
        }
    }

    public void stop() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            isPrepared = false;
            Log.d(TAG, "音乐已停止");
            hideNotification();
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

    // 创建通知渠道 (Android 8.0+)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // 显示通知
    private void showNotification() {
        if (currentSong == null) return;

        // 创建启动应用的意图
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setAction(Intent.ACTION_MAIN);
        openAppIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 创建通知构建器
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentSong != null ? currentSong.getName() : "未知歌曲")
                .setContentText(currentSong != null ? currentSong.getAr_name() : "未知艺术家")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(defaultAlbumArt)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // 添加动作按钮
        if (isPlaying()) {
            // 暂停按钮
            PendingIntent pausePendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", pausePendingIntent);
        } else {
            // 播放按钮
            PendingIntent playPendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_PLAY),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_media_play, "播放", playPendingIntent);
        }

        // 上一首按钮
        PendingIntent previousPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_PREVIOUS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_media_previous, "上一首", previousPendingIntent);

        // 下一首按钮
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_media_next, "下一首", nextPendingIntent);

        // 停止按钮
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_delete, "停止", stopPendingIntent);

        try {
            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "构建或显示通知时发生错误", e);
            // 即使通知显示失败，也不应该影响音乐播放功能
        }

    }

    // 隐藏通知
    private void hideNotification() {
        stopForeground(true);
    }

    // 广播接收器处理通知动作
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_PLAY:
                    resume();
                    break;
                case ACTION_NEXT:
                    if (playbackListener != null) {
                        playbackListener.onCompletion(); // 利用现有的完成回调播放下一首
                    }
                    break;
                case ACTION_PREVIOUS:
                    // 发送广播到DashboardFragment处理上一首逻辑
                    Intent previousIntent = new Intent("com.flying.whitefox.action.PREVIOUS_SONG");
                    sendBroadcast(previousIntent);
                    break;
                case ACTION_STOP:
                    stop();
                    stopSelf(); // 停止服务
                    break;
            }
        }
    };

    // 提供获取MusicService实例的方法
    public MusicService getMusicService() {
        return musicService;
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

        // 取消注册广播接收器
        try {
            unregisterReceiver(notificationReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器未注册，忽略异常
        }

        hideNotification();
    }
}
