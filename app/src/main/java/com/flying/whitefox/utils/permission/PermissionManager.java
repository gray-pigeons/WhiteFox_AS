package com.flying.whitefox.utils.permission;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.permissionx.guolindev.PermissionX;

import java.util.ArrayList;
import java.util.List;


/**
 * 权限管理类
 * 在其他Activity或Fragment中申请所有权限
 * PermissionManager.requestAllPermissions(this, new PermissionManager.PermissionCallback() {
 *     @Override
 *     public void onAllPermissionsGranted() {
 *         // 所有权限都已授予
 *     }
 *
 *     @Override
 *     public void onPermissionsDenied(List<String> deniedPermissions) {
 *         // 一些权限被拒绝
 *     }
 * });
 *
 * // 或者申请特定权限
 * List<String> permissions = Arrays.asList(
 *     Manifest.permission.CAMERA,
 *     Manifest.permission.READ_EXTERNAL_STORAGE
 * );
 * PermissionManager.requestPermissions(this, permissions, new PermissionManager.PermissionCallback() {
 *     @Override
 *     public void onAllPermissionsGranted() {
 *         // 所有权限都已授予
 *     }
 *
 *     @Override
 *     public void onPermissionsDenied(List<String> deniedPermissions) {
 *         // 一些权限被拒绝
 *     }
 * });
 */
public class PermissionManager {

    /**
     * 申请应用所需的所有权限
     * @param activity Activity上下文
     * @param callback 权限申请结果回调
     */
    @SuppressLint("ObsoleteSdkInt")
    public static void requestAllPermissions(Activity activity, PermissionCallback callback) {
        // 构建权限列表
        List<String> permissions = getList();

        PermissionX.init((FragmentActivity) activity)
                .permissions(permissions)
                .explainReasonBeforeRequest()
                .onExplainRequestReason((scope, deniedList) -> {
                    StringBuilder message = new StringBuilder("应用需要以下权限才能正常运行:\n");
                    for (String permission : deniedList) {
                        message.append(getPermissionDescription(permission)).append("\n");
                    }
                    scope.showRequestReasonDialog(deniedList, message.toString(), "允许", "拒绝");
                })
                .onForwardToSettings((scope, deniedList) -> {
                    StringBuilder message = new StringBuilder("以下权限被拒绝，需要在设置中手动开启:\n");
                    for (String permission : deniedList) {
                        message.append(getPermissionDescription(permission)).append("\n");
                    }
                    scope.showForwardToSettingsDialog(deniedList, message.toString(), "去设置", "取消");
                })
                .request((allGranted, grantedList, deniedList) -> {
                    if (allGranted) {
//                            Toast.makeText(activity, "所有权限已授予，应用可以正常使用", Toast.LENGTH_SHORT).show();
                        if (callback != null) {
                            callback.onAllPermissionsGranted();
                        }
                    } else {
                        StringBuilder deniedPermissions = new StringBuilder();
                        for (String permission : deniedList) {
                            deniedPermissions.append(getPermissionDescription(permission)).append("\n");
                        }
                        Toast.makeText(activity, "以下权限被拒绝，可能会影响应用功能:\n" + deniedPermissions,
                            Toast.LENGTH_LONG).show();
                        if (callback != null) {
                            callback.onPermissionsDenied(deniedList);
                        }
                    }
                });
    }

    @NonNull
    private static List<String> getList() {
        List<String> permissions = new ArrayList<>();

        // 根据Android版本添加相应的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要单独的媒体权限
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            // Android 12及以下版本使用传统的存储权限
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        // 添加其他通用权限
        permissions.add(Manifest.permission.CAMERA);
        return permissions;
    }

    /**
     * 检查并申请特定权限
     * @param activity Activity上下文
     * @param permissions 需要申请的权限列表
     * @param callback 权限申请结果回调
     */
    public static void requestPermissions(Activity activity, List<String> permissions, PermissionCallback callback) {
        PermissionX.init((FragmentActivity) activity)
                .permissions(permissions)
                .explainReasonBeforeRequest()
                .onExplainRequestReason((scope, deniedList) -> {
                    StringBuilder message = new StringBuilder("应用需要以下权限才能正常运行:\n");
                    for (String permission : deniedList) {
                        message.append(getPermissionDescription(permission)).append("\n");
                    }
                    scope.showRequestReasonDialog(deniedList, message.toString(), "允许", "拒绝");
                })
                .onForwardToSettings((scope, deniedList) -> {
                    StringBuilder message = new StringBuilder("以下权限被拒绝，需要在设置中手动开启:\n");
                    for (String permission : deniedList) {
                        message.append(getPermissionDescription(permission)).append("\n");
                    }
                    scope.showForwardToSettingsDialog(deniedList, message.toString(), "去设置", "取消");
                })
                .request((allGranted, grantedList, deniedList) -> {
                    if (allGranted) {
                        if (callback != null) {
                            callback.onAllPermissionsGranted();
                        }
                    } else {
                        if (callback != null) {
                            callback.onPermissionsDenied(deniedList);
                        }
                    }
                });
    }

    /**
     * 获取权限的中文描述
     * @param permission 权限字符串
     * @return 权限的中文描述
     */
    public static String getPermissionDescription(String permission) {
        switch (permission) {
            case Manifest.permission.POST_NOTIFICATIONS:
                return "通知权限 - 用于显示音乐播放通知";
            case Manifest.permission.READ_MEDIA_IMAGES:
                return "图片访问权限 - 用于访问设备上的图片";
            case Manifest.permission.READ_MEDIA_VIDEO:
                return "视频访问权限 - 用于访问设备上的视频";
            case Manifest.permission.READ_MEDIA_AUDIO:
                return "音频访问权限 - 用于访问设备上的音乐";
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                return "存储读取权限 - 用于读取设备存储";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return "存储写入权限 - 用于保存文件到设备存储";
            case Manifest.permission.CAMERA:
                return "相机权限 - 用于扫描二维码等功能";
            default:
                return permission;
        }
    }

    /**
     * 权限申请回调接口
     */
    public interface PermissionCallback {
        /**
         * 当所有权限都被授予时调用
         */
        void onAllPermissionsGranted();

        /**
         * 当有权限被拒绝时调用
         * @param deniedPermissions 被拒绝的权限列表
         */
        void onPermissionsDenied(List<String> deniedPermissions);
    }
}
