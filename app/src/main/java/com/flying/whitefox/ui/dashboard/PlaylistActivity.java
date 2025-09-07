package com.flying.whitefox.ui.dashboard;


import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.data.model.music.SongData;
import com.flying.whitefox.service.MusicService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PlaylistActivity extends AppCompatActivity {
    private static final String TAG = "PlaylistActivity";

    public static final String EXTRA_PLAYLIST = "extra_playlist";
    public static final String EXTRA_CURRENT_SONG_INDEX = "extra_current_song_index";

    private RecyclerView recyclerView;
    private ImageButton btnClose;
    private PlaylistAdapter adapter;
    private PlaylistData playlist;
    private int currentSongIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_musicplaylist);

        initViews();
        initData();
        setupListeners();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_playlist);
        btnClose = findViewById(R.id.btn_close);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void initData() {
        playlist = (PlaylistData) getIntent().getSerializableExtra(EXTRA_PLAYLIST);
        currentSongIndex = getIntent().getIntExtra(EXTRA_CURRENT_SONG_INDEX, 0);

        if (playlist != null && playlist.songs != null) {
            adapter = new PlaylistAdapter(playlist.songs, currentSongIndex, new PlaylistAdapter.OnSongClickListener() {
                @Override
                public void onSongClick(PlaylistData.Song song, int position) {
                    playSong(position);
                }
            });
            recyclerView.setAdapter(adapter);
        }
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());
    }

    private void playSong(int index) {
        if (playlist == null || playlist.songs == null || index >= playlist.songs.size()) {
            return;
        }

        PlaylistData.Song song = playlist.songs.get(index);
        Toast.makeText(this, "正在获取歌曲播放链接: " + song.name, Toast.LENGTH_SHORT).show();

        // 在后台线程获取歌曲播放链接
        new Thread(() -> {
            Future<SongData> future = MusicService.getSongUrl(song.id, "standard");
            try {
                SongData songData = future.get();
                if (songData != null && (songData.status == 200 || (songData.getUrl() != null && !songData.getUrl().isEmpty()))) {
                    // 返回结果给DashboardFragment
                    setResult(RESULT_OK, getIntent()
                            .putExtra("song_data", songData)
                            .putExtra("song_index", index));
                    runOnUiThread(() -> {
                        Toast.makeText(PlaylistActivity.this, "开始播放: " + song.name, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(PlaylistActivity.this, "无法获取歌曲播放链接: " + song.name, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "获取歌曲链接失败", e);
                runOnUiThread(() -> {
                    Toast.makeText(PlaylistActivity.this, "获取歌曲链接失败: " + song.name, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}

