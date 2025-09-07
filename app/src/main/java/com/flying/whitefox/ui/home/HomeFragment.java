package com.flying.whitefox.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.flying.whitefox.R;
import com.flying.whitefox.data.model.home.GridItemData;
import com.flying.whitefox.data.model.home.NavDataResponse;
import com.flying.whitefox.utils.network.NetworkManager;
import com.flying.whitefox.databinding.FragmentHomeBinding;
import com.flying.whitefox.ui.webview.WebViewActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private List<GridItemData> functionItems = new ArrayList<>();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false);

        // 从网络获取数据
        loadFunctionItemsFromNetwork();

        return binding.getRoot();
    }

  private void loadFunctionItemsFromNetwork() {
    try {
        // 发起网络请求
        NetworkManager.getInstance().getApiService().getNavData().enqueue(new Callback<NavDataResponse>() {
            @Override
            public void onResponse(Call<NavDataResponse> call, Response<NavDataResponse> response) {
                if (getActivity() == null) return;
                Log.i("HomeFragment", "Response: " + response);
                try {
                    // 处理成功响应
                    if (response.isSuccessful() && response.body() != null) {
                        NavDataResponse navDataResponse = response.body();
                        if (navDataResponse.getData() != null &&
                            navDataResponse.getData().getFuncGridData() != null) {

                            functionItems.clear();
                            // 只添加显示的项
                            for (GridItemData item : navDataResponse.getData().getFuncGridData()) {
                                if (item.isShow()) {
                                    functionItems.add(item);
                                }
                            }

                            // 在主线程更新UI
                            getActivity().runOnUiThread(() -> {
                                populateGridLayout();
                            });
                        } else {
                            handleError("数据格式错误");
                        }
                    } else {
                        handleError("服务器响应错误: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("HomeFragment", "Error processing response", e);
                    handleError("处理响应时出错: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<NavDataResponse> call, Throwable t) {
                if (getActivity() == null) return;

                try {
                    // 处理网络错误
                    String errorMessage = t.getMessage();
                    if (t instanceof java.net.UnknownHostException) {
                        errorMessage = "无法连接到服务器，请检查网络连接";
                    } else if (t instanceof java.net.SocketTimeoutException) {
                        errorMessage = "连接超时，请稍后重试";
                    } else if (t instanceof javax.net.ssl.SSLHandshakeException) {
                        errorMessage = "SSL证书验证失败";
                    }

                    handleError("网络请求失败: " + errorMessage);

                    // 使用静态数据作为备选方案
                    loadStaticFunctionItems();
                    getActivity().runOnUiThread(() -> {
                        populateGridLayout();
                    });
                } catch (Exception e) {
                    Log.e("HomeFragment", "Error handling failure", e);
                }
            }
        });
    } catch (Exception e) {
        Log.e("HomeFragment", "Error initiating network request", e);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                handleError("初始化网络请求失败: " + e.getMessage());
                loadStaticFunctionItems();
                populateGridLayout();
            });
        }
    }
}

    private void handleError(String errorMessage) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getActivity(), "加载失败: " + errorMessage, Toast.LENGTH_LONG).show();
            });
        }
        Log.e("HomeFragment", "Network error: " + errorMessage);
    }

    private void loadStaticFunctionItems() {
        functionItems.clear();

        GridItemData item1 = new GridItemData();
        item1.setText("热点资讯");
        item1.setIcon("ic_dashboard_black_24dp");
        item1.setUrl("https://newsnow.busiyi.world");
        item1.setShow(true);
        functionItems.add(item1);

    }

    /**
     * 填充网格布局中的功能项
     *
     * 该方法负责将functionItems列表中的每个功能项渲染到网格布局中，
     * 为每个功能项创建对应的视图，并设置图标、名称和点击事件。
     *
     * 此方法不接受参数，无返回值。
     */
    private void populateGridLayout() {
        if (binding == null) return; // Fragment已销毁

        GridLayout gridLayout = binding.gridLayout;
        // 清除网格布局中的所有现有视图
        gridLayout.removeAllViews();

        // 遍历所有功能项，为每个项创建并添加视图
        for (GridItemData item : functionItems) {
            View itemView = LayoutInflater.from(getContext()).inflate(R.layout.function_item, gridLayout, false);
            ImageView iconView = itemView.findViewById(R.id.functionIcon);
            TextView nameView = itemView.findViewById(R.id.functionName);

            // 设置名称
            nameView.setText(item.getText());

            // 加载图标 (简化处理，实际可能需要根据icon字段加载网络图片或本地资源)
            iconView.setImageResource(R.drawable.ic_dashboard_black_24dp);

            // 设置点击事件
            itemView.setOnClickListener(v -> {
                // 打开新页面的逻辑
                openFunctionPage(item);
            });

            gridLayout.addView(itemView);
        }
    }

    private void openFunctionPage(GridItemData item) {
        // 根据功能项打开相应的页面
        if (getActivity() != null) {
            Toast toast = Toast.makeText(getActivity(), "点击了功能项：" + item.getText() +
                "，跳转URL：" + item.getUrl(), Toast.LENGTH_SHORT);
            toast.show();
            if (item.getUrl().startsWith("http")) {
                // 跳转到网页
                WebViewActivity.start(this.getContext(), item.getUrl());
            } else {
                // 跳转到本地页面
            }

        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
