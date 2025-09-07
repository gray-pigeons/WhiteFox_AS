package com.flying.whitefox.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.music.PlaylistData;

import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.SongViewHolder> {
    private List<PlaylistData.Song> songs;
    private int currentSongIndex;
    private OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(PlaylistData.Song song, int position);
    }

    public PlaylistAdapter(List<PlaylistData.Song> songs, int currentSongIndex, OnSongClickListener listener) {
        this.songs = songs;
        this.currentSongIndex = currentSongIndex;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_songplaylist, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        PlaylistData.Song song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSongName;
        private TextView tvSongArtist;
        private View indicator;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSongName = itemView.findViewById(R.id.tv_song_name);
            tvSongArtist = itemView.findViewById(R.id.tv_song_artist);
            indicator = itemView.findViewById(R.id.indicator);
        }

        public void bind(PlaylistData.Song song, int position) {
            tvSongName.setText(song.name);
            tvSongArtist.setText(song.ar_name);

            // 显示当前播放歌曲的指示器
            if (position == currentSongIndex) {
                indicator.setVisibility(View.VISIBLE);
            } else {
                indicator.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song, position);
                }
            });
        }
    }
}
