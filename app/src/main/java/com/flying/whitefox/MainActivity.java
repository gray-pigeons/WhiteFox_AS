package com.flying.whitefox;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import com.flying.whitefox.service.MusicService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.flying.whitefox.databinding.ActivityMainBinding;
import com.flying.whitefox.utils.permission.PermissionManager;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置窗口模式以更好地处理软键盘
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
        
        // 请求所有必要权限
        requestAllPermissions();

    }
    
    private void requestAllPermissions() {
            PermissionManager.requestAllPermissions(this, new PermissionManager.PermissionCallback() {
                @Override
                public void onAllPermissionsGranted() {
                    // 所有权限都已授予，可以执行需要权限的操作
                }

                @Override
                public void onPermissionsDenied(List<String> deniedPermissions) {
                    // 一些权限被拒绝，可以根据需要处理
                }
            });
    }
}