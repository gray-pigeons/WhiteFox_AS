package com.flying.whitefox.ui.dashboard;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.music.PlaylistData;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
    private List<PlaylistData.Song> songs;
    private OnSongClickListener listener;
    private static final String TAG = "SongAdapter";

    public interface OnSongClickListener {
        void onSongClick(PlaylistData.Song song, int position);
        void onPlayButtonClick(PlaylistData.Song song, int position);
    }

    public SongAdapter(List<PlaylistData.Song> songs, OnSongClickListener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        PlaylistData.Song song = songs.get(position);
        holder.title.setText(song.name);
        holder.artist.setText(song.ar_name);
        
        if (song.pic != null && !song.pic.isEmpty()) {
            loadAlbumCover(song.pic, holder.albumCover);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSongClick(song, position);
            }
        });

        holder.playButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayButtonClick(song, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView albumCover;
        TextView title;
        TextView artist;
        ImageButton playButton;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            albumCover = itemView.findViewById(R.id.item_album_cover);
            title = itemView.findViewById(R.id.item_song_title);
            artist = itemView.findViewById(R.id.item_song_artist);
            playButton = itemView.findViewById(R.id.item_btn_play);
        }
    }

    public void updateSongs(List<PlaylistData.Song> newSongs) {
        this.songs = newSongs;
        notifyDataSetChanged();
    }

    // 使用HttpURLConnection加载专辑封面
    private void loadAlbumCover(String imageUrl, ImageView imageView) {
        new Thread(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                
                // 在主线程中更新UI
                imageView.post(() -> imageView.setImageBitmap(bitmap));
            } catch (IOException e) {
                Log.e(TAG, "加载专辑封面失败", e);
            }
        }).start();
    }
}

