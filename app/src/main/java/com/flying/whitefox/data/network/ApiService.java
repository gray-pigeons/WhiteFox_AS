package com.flying.whitefox.data.network;

import com.flying.whitefox.data.model.home.NavDataResponse;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("/api/indexNavigation") // 替换为实际的API路径
    Call<NavDataResponse> getNavData();
}
