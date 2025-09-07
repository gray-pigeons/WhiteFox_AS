package com.flying.whitefox.ui.dashboard;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.music.PlaylistData;
import com.flying.whitefox.utils.CustomPlaylistParser;
import com.flying.whitefox.utils.cache.PlaylistCacheManager;

public class ImportPlaylistActivity extends AppCompatActivity {
    private EditText etPlaylistData;
    private Button btnCancel;
    private Button btnImport;
    private PlaylistCacheManager cacheManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_importplaylist);

        initViews();
        setupListeners();
        cacheManager = new PlaylistCacheManager(this);
    }

    private void initViews() {
        etPlaylistData = findViewById(R.id.et_playlist_data);
        btnCancel = findViewById(R.id.btn_cancel);
        btnImport = findViewById(R.id.btn_import);
        
        // 自动弹出键盘
        etPlaylistData.requestFocus();
        etPlaylistData.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etPlaylistData, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> finish());
        btnImport.setOnClickListener(v -> importPlaylist());
    }

    private void importPlaylist() {
        // 隐藏键盘
        hideKeyboard();
        
        String playlistJson = etPlaylistData.getText().toString().trim();

        if (TextUtils.isEmpty(playlistJson)) {
            Toast.makeText(this, "请输入歌单数据", Toast.LENGTH_SHORT).show();
            return;
        }

        PlaylistData customPlaylist = CustomPlaylistParser.parseCustomPlaylist(playlistJson);

        if (customPlaylist != null && CustomPlaylistParser.isValidPlaylist(customPlaylist)) {
            // 保存到缓存
            cacheManager.savePlaylist(customPlaylist);

            // 通过结果返回给DashboardFragment
            setResult(RESULT_OK);
            
            Toast.makeText(this, "歌单导入成功，共 " + customPlaylist.songs.size() + " 首歌曲", Toast.LENGTH_LONG).show();
            
            // 延迟关闭Activity，让用户看到成功提示
            etPlaylistData.postDelayed(() -> finish(), 1500);
        } else {
            Toast.makeText(this, "歌单数据格式不正确，请检查后重试", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // 获取当前获得焦点的View
            android.view.View v = getCurrentFocus();
            if (v instanceof EditText) {
                // 判断触摸点是否在EditText外部
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                int left = location[0];
                int top = location[1];
                int right = left + v.getWidth();
                int bottom = top + v.getHeight();
                
                // 如果触摸点在EditText外部，则隐藏键盘
                if (ev.getRawX() < left || ev.getRawX() > right || ev.getRawY() < top || ev.getRawY() > bottom) {
                    hideKeyboard();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}

