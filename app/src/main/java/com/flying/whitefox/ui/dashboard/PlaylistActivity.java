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
import com.flying.whitefox.data.model.music.QualityLevel;
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
    private PlaylistData playlist;

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
        int currentSongIndex = getIntent().getIntExtra(EXTRA_CURRENT_SONG_INDEX, 0);

        if (playlist != null && playlist.songs != null) {
            PlaylistAdapter adapter = new PlaylistAdapter(playlist.songs, currentSongIndex, new PlaylistAdapter.OnSongClickListener() {
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
        // 返回结果给DashboardFragment
        setResult(RESULT_OK, getIntent()
                .putExtra("song_index", index));
        finish();
    }
}

