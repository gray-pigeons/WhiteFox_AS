package com.flying.whitefox.ui.dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.data.model.music.SongData;
import com.flying.whitefox.service.MusicPlaybackManager;
import com.flying.whitefox.data.model.music.PlayMode;
import com.squareup.picasso.Picasso;

import java.util.Locale;
import java.util.Random;

public class DashboardFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private static final String TAG = "DashboardFragment";
    private static final int PlaylistId = 3778678; // 热歌榜ID

    // 添加静态变量来保存当前播放的歌曲索引
    private static int currentPlayingSongIndex = -1;

    // 音乐播放管理器
    private MusicPlaybackManager musicPlaybackManager;

    // 跟踪当前播放模式
    private PlayMode currentPlayMode = PlayMode.SEQUENTIAL;

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
    private boolean isFragmentFirstLoaded = true; // 标记Fragment是否首次加载

    // 进度更新
    private final Handler progressHandler = new Handler();
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (musicPlaybackManager != null) {
                musicPlaybackManager.updatePlaybackState();
            }
            // 只有在音乐播放时才继续更新进度
            if (musicPlaybackManager != null && musicPlaybackManager.isPlaying()) {
                progressHandler.postDelayed(this, 1000); // 每秒更新一次
            }
        }
    };

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> importPlaylistLauncher;
    private ActivityResultLauncher<Intent> playlistLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        initViews(root);
        setupListeners();
        setupActivityResultLaunchers(); // 初始化Activity Result Launchers

        // 初始化音乐播放管理器
        musicPlaybackManager = new MusicPlaybackManager(requireActivity());
        musicPlaybackManager.setPlaybackStateListener(new MusicPlaybackManager.PlaybackStateListener() {
            @Override
            public void onPlaybackStateChanged(MusicPlaybackManager.PlaybackState state) {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() -> updateUIWithPlaybackState(state));
                }
            }

            @Override
            public void onPlaylistUpdated(PlaylistData playlist) {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() -> handlePlaylistLoaded(playlist));
                }
            }

            @Override
            public void onSongChanged(int songIndex) {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() -> {
                        currentSongIndex = songIndex;
                        currentPlayingSongIndex = songIndex;
                        if (playlist != null && playlist.songs != null &&
                                songIndex >= 0 && songIndex < playlist.songs.size()) {
                            updateSongInfo(playlist.songs.get(songIndex));
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show());
                }
            }
        });

        //排除正在播放的情况下，被刷新了时，重新加载
        if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) {
            musicPlaybackManager.loadPlaylist(PlaylistId, false); // 首次加载不强制刷新
        }
        initSongInfoToUI();
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

    private void initSongInfoToUI() {
        // 当Fragment重新创建时，获取当前播放状态
        if (musicPlaybackManager != null) {
            // 延迟一小段时间以确保服务连接完成
            new Handler().postDelayed(() -> {
                // 获取当前播放列表
                PlaylistData currentPlaylist = musicPlaybackManager.getCurrentPlaylist();
                if (currentPlaylist != null) {
                    handlePlaylistLoaded(currentPlaylist);

                    // 获取当前播放的歌曲索引
                    int currentSongIndex = musicPlaybackManager.getCurrentSongIndex();
                    if (currentSongIndex >= 0 && currentPlaylist.songs != null &&
                            currentSongIndex < currentPlaylist.songs.size()) {
                        updateSongInfo(currentPlaylist.songs.get(currentSongIndex));
                    }
                } else {
                    // 如果没有播放列表，加载默认播放列表
                    // 排除正在播放的情况下，被刷新了时，重新加载
                    if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) {
                        musicPlaybackManager.loadPlaylist(PlaylistId, false); // 首次加载不强制刷新
                    }
                }

                // 更新播放状态（播放/暂停按钮等）
                musicPlaybackManager.updatePlaybackState();
            }, 500);
        }
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> {
            if (musicPlaybackManager != null) {
                musicPlaybackManager.togglePlayPause();
            }
        });
        btnPrevious.setOnClickListener(v -> {
            if (musicPlaybackManager != null) {
                musicPlaybackManager.playPreviousSong();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (musicPlaybackManager != null) {
                musicPlaybackManager.playNextSong();
            }
        });
        btnPlayMode.setOnClickListener(v -> {
            if (musicPlaybackManager != null) {
                // 切换播放模式
                PlayMode nextMode;

                switch (currentPlayMode) {
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

                currentPlayMode = nextMode;
                musicPlaybackManager.setPlayMode(nextMode);
                updatePlayModeButton(nextMode);
                Toast.makeText(getContext(), nextMode.getDescription(), Toast.LENGTH_SHORT).show();
            }
        });
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
                if (musicPlaybackManager != null) {
                    musicPlaybackManager.seekTo(seekBar.getProgress());
                }
                progressHandler.postDelayed(progressRunnable, 1000);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void updateUIWithPlaybackState(MusicPlaybackManager.PlaybackState state) {
        if (btnPlayPause != null) {
            if (state.isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
        }

        // 只有在进度有效时才更新进度条
        if (state.duration > 0) {
            progressBar.setMax(state.duration);
            progressBar.setProgress(state.currentPosition);
            updateCurrentTimeText(state.currentPosition);
            updateTotalTimeText(state.duration);
        } else {
            // 如果没有有效的持续时间，重置进度条
            progressBar.setProgress(0);
            currentTime.setText("00:00");
        }

        // 如果正在播放，启动进度更新
        if (state.isPlaying) {
            progressHandler.removeCallbacks(progressRunnable);
            progressHandler.postDelayed(progressRunnable, 1000);
        }
    }

    private void setupActivityResultLaunchers() {
        // 初始化导入歌单Activity的Launcher
        importPlaylistLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 处理导入歌单Activity的结果
                    // 这里可以根据需要处理返回的结果
                    Toast.makeText(getContext(), "暂未实现", Toast.LENGTH_SHORT).show();
                }
        );

        // 初始化播放列表Activity的Launcher
        playlistLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 处理播放列表Activity的结果
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        int songIndex = result.getData().getIntExtra("song_index", 0);

                        // 更新当前歌曲索引
                        currentSongIndex = songIndex;
                        currentPlayingSongIndex = songIndex;

                        // 播放选中的歌曲
                        if (musicPlaybackManager != null) {
                            // 使用MusicPlaybackManager播放歌曲
                            musicPlaybackManager.playSong(songIndex);
                            if (btnPlayPause != null) {
                                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                            }

                            // 更新UI
                            if (playlist != null && playlist.songs != null &&
                                    songIndex >= 0 && songIndex < playlist.songs.size()) {
                                updateSongInfo(playlist.songs.get(songIndex));
                            }

                            if (musicPlaybackManager != null) {
                                musicPlaybackManager.updatePlaybackState();
                            }
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
     * 更新播放模式按钮图标
     */
    private void updatePlayModeButton(PlayMode mode) {
        if (btnPlayMode == null) return;

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
     * 刷新歌单（取消当前请求并立即开始新的请求）
     */
    private void refreshPlaylist() {
        // 等待一小段时间确保请求被取消
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(getContext(), "开始获取最新歌单...", Toast.LENGTH_SHORT).show();
            // 立即开始新的请求
            if (musicPlaybackManager != null) {
                musicPlaybackManager.loadPlaylist(PlaylistId, true); // 强制刷新
            }
        }, 500); // 延迟500毫秒确保请求被取消
    }

    private void handlePlaylistLoaded(PlaylistData playlistData) {
        this.playlist = playlistData;

        // 只有在首次加载且没有音乐正在播放时才设置初始歌曲
        if (isFragmentFirstLoaded && playlistData.songs != null && !playlistData.songs.isEmpty()) {
            // 检查是否有音乐正在播放
            boolean isMusicPlaying = musicPlaybackManager != null && musicPlaybackManager.isPlaying();

            // 只有在没有音乐播放时才设置初始歌曲
            if (!isMusicPlaying) {
                if (currentSongIndex < 0 || currentSongIndex >= playlistData.songs.size()) {
                    Random random = new Random();
                    currentSongIndex = random.nextInt(playlistData.songs.size());
                }

                // 不立即播放，只是设置当前歌曲索引
                currentPlayingSongIndex = currentSongIndex; // 更新静态变量
                // 更新UI显示当前歌曲信息
                updateSongInfo(playlistData.songs.get(currentSongIndex));
            }
            isFragmentFirstLoaded = false;
        }
    }

    private void updateSongInfo(PlaylistData.Song song) {
        // 更新UI信息
        songTitle.setText(song.getName());
        songArtist.setText(song.getAr_name());

        if (song.getPic() != null && !song.getPic().isEmpty()) {
            Picasso.get().load(song.getPic()).into(albumCover);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacks(progressRunnable);

        if (musicPlaybackManager != null) {
            musicPlaybackManager.destroy();
        }
    }
}