package com.flying.whitefox.ui.dashboard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.data.model.music.QualityLevel;
import com.flying.whitefox.data.model.music.SongData;
import com.flying.whitefox.service.MusicPlaybackService;
import com.flying.whitefox.data.model.music.PlayMode;
import com.flying.whitefox.service.MusicService;
import com.flying.whitefox.utils.cache.PlaylistCacheManager;
import com.squareup.picasso.Picasso;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DashboardFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private static final String TAG = "DashboardFragment";
    private static final int PlaylistId = 3778678; // 热歌榜ID
    
    // 添加静态变量来保存当前播放的歌曲索引
    private static int currentPlayingSongIndex = -1;

    // UI组件
    private ImageView albumCover;
    private TextView songTitle;
    private TextView songArtist;
    private SeekBar progressBar;
    private TextView currentTime;
    private TextView totalTime;
    private ImageButton btnPrevious;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnPlayMode; // 播放模式按钮
    private Button btnRefresh; // 刷新按钮
    private Button btnImportPlaylist; // 导入歌单按钮
    private ImageButton btnOpenPlaylist; // 播放列表入口按钮

    // 数据
    private PlaylistData playlist;
    private int currentSongIndex = 0;
    private boolean isPlaying = false;
    private PlaylistCacheManager cacheManager; // 缓存管理器
    private boolean isFragmentFirstLoaded = true; // 标记Fragment是否首次加载

    // 添加一个字段来跟踪连续播放失败的次数
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3; // 最大连续失败次数

    // 音乐播放服务
    private MusicPlaybackService musicService;
    private boolean isServiceBound = false;

    // 进度更新
    private final Handler progressHandler = new Handler();
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            progressHandler.postDelayed(this, 1000); // 每秒更新一次
        }
    };

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> importPlaylistLauncher;
    private ActivityResultLauncher<Intent> playlistLauncher;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlaybackService.MusicBinder binder = (MusicPlaybackService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            
            // 设置播放完成监听器
            musicService.setPlaybackListener(new MusicPlaybackService.OnPlaybackListener() {
                @Override
                public void onCompletion() {
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(DashboardFragment.this::playNextSong);
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(() -> {
                            Log.e(TAG, "播放错误: " + error);
                            // 播放错误，自动尝试播放下一首歌曲
                            handleSongPlaybackFailure();
                        });
                    }
                }

                @Override
                public void onPlayModeChanged(PlayMode playMode) {
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(DashboardFragment.this::updatePlayModeButton);
                    }
                }
            });
            
            // 初始化播放模式按钮
            updatePlayModeButton();
            
            // 如果音乐正在播放，同步UI状态
            if (musicService.isPlaying()) {
                syncUIWithPlayingState();
            }
            
            Log.d(TAG, "音乐播放服务已连接");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            Log.d(TAG, "音乐播放服务已断开");
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        initViews(root);
        setupListeners();
        setupActivityResultLaunchers(); // 初始化Activity Result Launchers
        cacheManager = new PlaylistCacheManager(requireContext());

        //排除正在播放的情况下，被刷新了时，重新加载
        if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()
         || musicService == null || !musicService.isPlaying()) {
            loadPlaylist(false); // 首次加载不强制刷新
        }

        // 绑定音乐播放服务
        Intent intent = new Intent(getActivity(), MusicPlaybackService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        getActivity().startService(intent);

        return root;
    }

    private void initViews(View root) {
        albumCover = root.findViewById(R.id.album_cover);
        songTitle = root.findViewById(R.id.song_title);
        songArtist = root.findViewById(R.id.song_artist);
        progressBar = root.findViewById(R.id.progress_bar);
        currentTime = root.findViewById(R.id.current_time);
        totalTime = root.findViewById(R.id.total_time);
        btnPrevious = root.findViewById(R.id.btn_previous);
        btnPlayPause = root.findViewById(R.id.btn_play_pause);
        btnNext = root.findViewById(R.id.btn_next);
        btnPlayMode = root.findViewById(R.id.btn_play_mode); // 播放模式按钮
        btnRefresh = root.findViewById(R.id.btn_refresh); // 刷新按钮
        btnImportPlaylist = root.findViewById(R.id.btn_import_playlist); // 导入歌单按钮
        btnOpenPlaylist = root.findViewById(R.id.btn_open_playlist); // 播放列表入口按钮
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnPrevious.setOnClickListener(v -> playPreviousSong());
        btnNext.setOnClickListener(v -> playNextSong());
        btnPlayMode.setOnClickListener(v -> togglePlayMode()); // 切换播放模式
        btnRefresh.setOnClickListener(v -> refreshPlaylist()); // 点击刷新按钮时刷新歌单
        btnImportPlaylist.setOnClickListener(v -> openImportPlaylistActivity()); // 打开导入歌单Activity
        btnOpenPlaylist.setOnClickListener(v -> openPlaylistActivity()); // 打开播放列表Activity

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateCurrentTimeText(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                progressHandler.removeCallbacks(progressRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
                progressHandler.postDelayed(progressRunnable, 1000);
            }
        });
    }

    private void setupActivityResultLaunchers() {
        // 初始化导入歌单Activity的Launcher
        importPlaylistLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 处理导入歌单Activity的结果
                    // 这里可以根据需要处理返回的结果
                }
        );

        // 初始化播放列表Activity的Launcher
        playlistLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 处理播放列表Activity的结果
                    getActivity();
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        // 获取返回的歌曲数据
                        SongData songData = (SongData) result.getData().getSerializableExtra("song_data");
                        int songIndex = result.getData().getIntExtra("song_index", 0);
                        
                        // 更新当前歌曲索引
                        currentSongIndex = songIndex;
                        currentPlayingSongIndex = songIndex;
                        
                        // 播放选中的歌曲
                        if (songData != null && isServiceBound && musicService != null) {
                            musicService.playSong(songData);
                            isPlaying = true;
                            if (btnPlayPause != null) {
                                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                            }
                            
                            // 更新UI
                            if (playlist != null && playlist.songs != null && 
                                songIndex >= 0 && songIndex < playlist.songs.size()) {
                                updateSongInfo(playlist.songs.get(songIndex));
                            }
                            
                            // 更新进度条
                            new Handler().postDelayed(() -> {
                                if (musicService != null) {
                                    progressBar.setMax(musicService.getDuration());
                                    updateTotalTimeText(musicService.getDuration());
                                    progressBar.setProgress(musicService.getCurrentPosition());
                                    updateCurrentTimeText(musicService.getCurrentPosition());
                                }
                            }, 500);
                            
                            // 开始更新进度
                            progressHandler.removeCallbacks(progressRunnable);
                            progressHandler.postDelayed(() -> {
                                updateProgress();
                                progressHandler.post(progressRunnable);
                            }, 100);
                        }
                    }
                }
        );
    }

    /**
     * 打开播放列表Activity
     */
    private void openPlaylistActivity() {
        if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) {
            Toast.makeText(getContext(), "暂无播放列表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 打开PlaylistActivity
        Intent intent = new Intent(getActivity(), PlaylistActivity.class);
        intent.putExtra(PlaylistActivity.EXTRA_PLAYLIST, playlist);
        // 传递当前播放的歌曲索引，而不是当前选中的歌曲索引
        intent.putExtra(PlaylistActivity.EXTRA_CURRENT_SONG_INDEX, currentPlayingSongIndex >= 0 ? currentPlayingSongIndex : currentSongIndex);
        playlistLauncher.launch(intent); // 使用新的API
    }

    /**
     * 切换播放模式
     */
    private void togglePlayMode() {
        if (musicService != null) {
            PlayMode currentMode = musicService.getPlayMode();
            PlayMode nextMode;
            
            switch (currentMode) {
                case SEQUENTIAL:
                    nextMode = PlayMode.RANDOM;
                    break;
                case RANDOM:
                    nextMode = PlayMode.SINGLE_LOOP;
                    break;
                case SINGLE_LOOP:
                    nextMode = PlayMode.SEQUENTIAL;
                    break;
                default:
                    nextMode = PlayMode.SEQUENTIAL;
            }
            
            musicService.setPlayMode(nextMode);
            updatePlayModeButton();
            Toast.makeText(getContext(), nextMode.getDescription(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 更新播放模式按钮图标
     */
    private void updatePlayModeButton() {
        if (btnPlayMode == null || musicService == null) return;
        
        PlayMode mode = musicService.getPlayMode();
        switch (mode) {
            case SEQUENTIAL:
                btnPlayMode.setImageResource(android.R.drawable.ic_menu_sort_by_size);
                break;
            case RANDOM:
                btnPlayMode.setImageResource(android.R.drawable.ic_menu_rotate);
                break;
            case SINGLE_LOOP:
                btnPlayMode.setImageResource(android.R.drawable.ic_media_previous);
                break;
        }
    }

    /**
     * 打开导入歌单Activity
     */
    private void openImportPlaylistActivity() {
        Intent intent = new Intent(getActivity(), ImportPlaylistActivity.class);
        importPlaylistLauncher.launch(intent); // 使用新的API
    }


    /**
     * 加载默认歌单
     */
    private void loadDefaultPlaylist() {
        Toast.makeText(getContext(), "正在加载默认歌单...", Toast.LENGTH_SHORT).show();
        loadPlaylist(true); // 强制刷新加载默认歌单
    }

    /**
     * 刷新歌单（取消当前请求并立即开始新的请求）
     */
    private void refreshPlaylist() {
        Toast.makeText(getContext(), "正在刷新歌单...", Toast.LENGTH_SHORT).show();
        // 取消当前正在进行的任何请求
        MusicService.cancelCurrentRequest();
        
        // 等待一小段时间确保请求被取消
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(getContext(), "开始获取最新歌单...", Toast.LENGTH_SHORT).show();
            // 立即开始新的请求
            loadPlaylist(true);
        }, 500); // 延迟500毫秒确保请求被取消
    }

    /**
     * 加载歌单
     *
     @param forceRefresh 是否强制刷新（忽略缓存）
     */
    private void loadPlaylist(boolean forceRefresh) {
        // 检查Fragment状态
        if (!isAdded() || isDetached() || getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        
        // 如果不是强制刷新，尝试从缓存加载
        if (!forceRefresh) {
            PlaylistData cachedPlaylist = cacheManager.getPlaylist();
            if (cachedPlaylist != null) {
                Log.d(TAG, "从缓存加载歌单");
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
//                    Toast.makeText(getContext(), "从缓存加载歌单", Toast.LENGTH_SHORT).show();
                    handlePlaylistLoaded(cachedPlaylist);
                }
                return;
            }
        }
        getSongPlayListByServer();
    }

    private void getSongPlayListByServer() {
        // 从网络加载歌单
        new Thread(() -> {
            Future<PlaylistData> future = com.flying.whitefox.service.MusicService.getPlaylist(PlaylistId); // 使用默认歌单ID
            try {
                playlist = future.get();
                if (getActivity() != null && !getActivity().isFinishing() && isAdded() && !isDetached()) {
                    getActivity().runOnUiThread(() -> {
                        if (playlist != null && playlist.songs != null && !playlist.songs.isEmpty()) {
                            // 保存到缓存
                            cacheManager.savePlaylist(playlist);
                            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                                Toast.makeText(getContext(), "歌单加载成功", Toast.LENGTH_SHORT).show();
                                handlePlaylistLoaded(playlist);
                            }
                        } else {
                            Log.e(TAG, "获取到的歌单为空或没有歌曲");
                            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                                Toast.makeText(getContext(), "获取歌单失败，请稍后重试", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "加载歌单失败", e);
                // 如果网络加载失败，尝试从缓存加载（即使在强制刷新模式下）
                if (getActivity() != null && !getActivity().isFinishing() && isAdded() && !isDetached()) {
                    getActivity().runOnUiThread(() -> {
                        PlaylistData cachedPlaylist = cacheManager.getPlaylist();
                        if (cachedPlaylist != null) {
                            Log.d(TAG, "网络加载失败，从缓存加载歌单");
                            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                                Toast.makeText(getContext(), "网络加载失败，从缓存加载歌单", Toast.LENGTH_SHORT).show();
                                handlePlaylistLoaded(cachedPlaylist);
                            }
                        } else {
                            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                                Toast.makeText(getContext(), "加载失败，请检查网络连接", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void handlePlaylistLoaded(PlaylistData playlistData) {
        this.playlist = playlistData;

        // 只有在首次加载且没有音乐正在播放时才设置初始歌曲
        if (isFragmentFirstLoaded && playlistData.songs != null && !playlistData.songs.isEmpty()) {
            // 检查是否有音乐正在播放
            boolean isMusicPlaying = musicService != null && musicService.isPlaying();
            
            // 只有在没有音乐播放时才设置初始歌曲
            if (!isMusicPlaying) {
                Random random = new Random();
                int randomIndex = random.nextInt(playlistData.songs.size());
                // 不立即播放，只是设置当前歌曲索引
                currentSongIndex = randomIndex;
                currentPlayingSongIndex = randomIndex; // 更新静态变量
                // 更新UI显示当前歌曲信息
                updateSongInfo(playlistData.songs.get(currentSongIndex));
            }
            isFragmentFirstLoaded = false;
        } 
        // 如果不是首次加载，检查当前是否有正在播放的歌曲
        else if (!isFragmentFirstLoaded && musicService != null && musicService.isPlaying()) {
            // Fragment恢复时音乐正在播放，同步UI状态
            syncUIWithPlayingState();
        }
        // 如果不是首次加载，且没有音乐正在播放，更新UI显示当前歌曲信息
        else if (!isFragmentFirstLoaded) {
            // 确保currentSongIndex在有效范围内
            if (playlist != null && playlist.songs != null && 
                currentSongIndex >= 0 && currentSongIndex < playlist.songs.size()) {
                updateSongInfo(playlist.songs.get(currentSongIndex));
            }
        }
    }

    /**
     * 同步UI与播放状态
     */
    private void syncUIWithPlayingState() {
        if (musicService != null) {
            // 更新播放按钮状态
            isPlaying = true;
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            
            // 更新进度条
            new Handler().postDelayed(() -> {
                if (musicService != null) {
                    progressBar.setMax(musicService.getDuration());
                    updateTotalTimeText(musicService.getDuration());
                    progressBar.setProgress(musicService.getCurrentPosition());
                    updateCurrentTimeText(musicService.getCurrentPosition());
                }
            }, 100);
            
            // 恢复进度更新
            progressHandler.removeCallbacks(progressRunnable);
            progressHandler.post(progressRunnable);
            
            // 显示当前正在播放的歌曲信息
            com.flying.whitefox.data.model.music.PlaylistData.Song playingSong = musicService.getCurrentPlayingSong();
            if (playingSong != null && playlist != null && playlist.songs != null) {
                // 查找当前播放的歌曲在播放列表中的索引
                for (int i = 0; i < playlist.songs.size(); i++) {
                    com.flying.whitefox.data.model.music.PlaylistData.Song song = playlist.songs.get(i);
                    // 通过歌曲名称和艺术家名称匹配当前播放的歌曲
                    if (song.getName() != null && song.getName().equals(playingSong.getName()) &&
                        song.getAr_name() != null && song.getAr_name().equals(playingSong.getAr_name())) {
                        currentSongIndex = i;
                        currentPlayingSongIndex = i; // 更新静态变量
                        break;
                    }
                }
                updateSongInfo(playingSong);
            }
        }
    }

    private void playSong(int index) {
        if (playlist == null || playlist.songs == null || index >= playlist.songs.size()) {
            return;
        }

        // 更新当前歌曲索引和UI，但不立即播放
        currentSongIndex = index;
        currentPlayingSongIndex = index; // 同步静态变量
        updateSongInfo(playlist.songs.get(index));

        // 只有当用户点击播放按钮时才真正播放歌曲
        // 实际播放由togglePlayPause方法处理
        togglePlayPause();
    }

    void getSongUrlByServer(int index) {
        // 检查索引有效性
        if (playlist == null || playlist.songs == null || index >= playlist.songs.size() || index < 0) {
            Toast.makeText(getContext(), "无效的歌曲索引", Toast.LENGTH_SHORT).show();
            return;
        }

        PlaylistData.Song song = playlist.songs.get(index);
        
        // 检查歌曲ID是否有效
        if (song.getId().isEmpty() || song.getId().equals("0")) {
            Toast.makeText(getContext(), "无效的歌曲ID: " + song.getId(), Toast.LENGTH_SHORT).show();
            // 播放失败，自动尝试下一首歌曲
            handleSongPlaybackFailure();
            return;
        }
        
        Thread thread = new Thread(() -> {
            Future<SongData> future = MusicService.getSongUrl(song.id, QualityLevel.STANDARD);
            try {
                SongData songData = future.get();
                if (getActivity() != null && !getActivity().isFinishing() && isAdded() && !isDetached()) {
                    getActivity().runOnUiThread(() -> {
                        if (songData != null && (songData.status == 200 || (songData.getUrl() != null && !songData.getUrl().isEmpty()))) {
                            // 成功获取歌曲链接，重置失败计数器
                            consecutiveFailures = 0;
                            
                            if (isServiceBound && musicService != null) {
                                musicService.playSong(songData);
                                isPlaying = true;
                                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                                // 首先更新UI显示正在尝试播放的歌曲信息
                                updateSongInfo(song);
                                currentSongIndex = index;
                                // 更新进度条最大值
                                // 注意：这里需要等MediaPlayer准备好才能获取时长
                                new Handler().postDelayed(() -> {
                                    if (musicService != null) {
                                        progressBar.setMax(musicService.getDuration());
                                        updateTotalTimeText(musicService.getDuration());
                                    }
                                }, 500);

                                // 开始更新进度
                                progressHandler.postDelayed(() -> {
                                    updateProgress();
                                    progressHandler.post(progressRunnable);
                                }, 100);
                            }
                        } else {
                            // 减少频繁的错误提示
                            Log.e(TAG, "无法获取歌曲播放链接: " + (songData != null ? songData.getMsg() : "未知错误"));
                            // 播放失败，自动尝试下一首歌曲
                            handleSongPlaybackFailure();
                        }
                    });
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "获取歌曲链接失败", e);
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() -> {
                        // 播放失败，自动尝试下一首歌曲
                        handleSongPlaybackFailure();
                    });
                }
            }
        });

        thread.start();
    }

    private void updateSongInfo(PlaylistData.Song song) {
        // 更新UI信息
        songTitle.setText(song.getName());
        songArtist.setText(song.getAr_name());

        if (song.getPic() != null && !song.getPic().isEmpty()) {
            Picasso.get().load(song.getPic()).into(albumCover);
        }
    }

    private void resetPlayButton() {
        isPlaying = false;
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void togglePlayPause() {
        if (isServiceBound && musicService != null) {
            if (musicService.isPlaying()) {
                musicService.pause();
                isPlaying = false;
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                progressHandler.removeCallbacks(progressRunnable);
            } else {
                // 真正播放当前显示的歌曲
                getSongUrlByServer(currentSongIndex);
            }
        }
    }

    private void playPreviousSong() {
        if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) {
            return;
        }

        if (musicService != null) {
            currentSongIndex = musicService.getPreviousSongIndex(currentSongIndex, playlist.songs.size());
        } else {
            currentSongIndex--;
            if (currentSongIndex < 0) {
                currentSongIndex = playlist.songs.size() - 1;
            }
        }
        playSong(currentSongIndex);
    }

    private void playNextSong() {
        if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) {
            return;
        }

        if (musicService != null) {
            currentSongIndex = musicService.getNextSongIndex(currentSongIndex, playlist.songs.size());
        } else {
            currentSongIndex++;
            if (currentSongIndex >= playlist.songs.size()) {
                currentSongIndex = 0;
            }
        }
        playSong(currentSongIndex);
    }

    private void updateProgress() {
        if (isServiceBound && musicService != null && musicService.isPlaying()) {
            int currentPosition = musicService.getCurrentPosition();
            progressBar.setProgress(currentPosition);
            updateCurrentTimeText(currentPosition);
        }
    }

    private void updateCurrentTimeText(int progress) {
        int minutes = progress / 60000;
        int seconds = (progress / 1000) % 60;
        currentTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void updateTotalTimeText(int duration) {
        int minutes = duration / 60000;
        int seconds = (duration / 1000) % 60;
        totalTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }


    
    /**
     * 处理歌曲播放失败的情况，自动尝试播放下一首歌曲
     */
    private void handleSongPlaybackFailure() {
        if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) {
            resetPlayButton();
            // 只在需要时显示Toast
            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                Toast.makeText(getContext(), "播放列表为空，无法继续播放", Toast.LENGTH_LONG).show();
            }
            return;
        }

        consecutiveFailures++;
        
        // 如果连续失败次数过多，停止尝试并通知用户
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            resetPlayButton();
            consecutiveFailures = 0; // 重置计数器
            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                Toast.makeText(getContext(), "连续播放失败次数过多，请检查网络连接或稍后重试", Toast.LENGTH_LONG).show();
            }
            return;
        }

        // 保存当前尝试的歌曲索引，防止无限循环
        int originalIndex = currentSongIndex;
        boolean songPlayed = false;
        
        // 尝试播放下一首歌曲，最多尝试整个播放列表的长度次
        for (int i = 0; i < playlist.songs.size(); i++) {
            // 获取下一首歌曲的索引
            if (musicService != null) {
                currentSongIndex = musicService.getNextSongIndex(currentSongIndex, playlist.songs.size());
            } else {
                currentSongIndex++;
                if (currentSongIndex >= playlist.songs.size()) {
                    currentSongIndex = 0;
                }
            }
            
            // 如果回到原始歌曲，说明整个列表都尝试过了
            if (currentSongIndex == originalIndex) {
                break;
            }
            
            // 尝试播放当前歌曲
            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                // 减少Toast消息的频率
                if (consecutiveFailures == 1) { // 只在第一次失败时显示
                    Toast.makeText(getContext(), "正在尝试播放下一首歌曲...", Toast.LENGTH_SHORT).show();
                }
            }
            getSongUrlByServer(currentSongIndex);
            songPlayed = true;
            break; // 只尝试播放一首歌曲
        }
        
        // 如果没有成功尝试播放任何歌曲
        if (!songPlayed) {
            resetPlayButton();
            consecutiveFailures = 0; // 重置计数器
            if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                Toast.makeText(getContext(), "无法播放列表中的任何歌曲，请检查网络连接或稍后重试", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Fragment恢复时重新绑定服务
        if (!isServiceBound) {
            Intent intent = new Intent(getActivity(), MusicPlaybackService.class);
            getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
        
        // 如果音乐正在播放则同步UI
        if (musicService != null && musicService.isPlaying()) {
            syncUIWithPlayingState();
        }
        // 如果有播放列表但没有正在播放的音乐，显示当前选中的歌曲信息
        else if (playlist != null && playlist.songs != null && !playlist.songs.isEmpty()) {
            // 确保currentSongIndex在有效范围内
            if (currentSongIndex >= 0 && currentSongIndex < playlist.songs.size()) {
                updateSongInfo(playlist.songs.get(currentSongIndex));
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Fragment暂停时移除进度更新回调
        progressHandler.removeCallbacks(progressRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacks(progressRunnable);
        // 取消任何正在进行的请求
        com.flying.whitefox.service.MusicService.cancelCurrentRequest();
        
        // 解绑服务
        if (isServiceBound) {
            getActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}