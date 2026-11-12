package com.example.Vlocker.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface VlockerApiService {

    // 1. 카카오 로컬 목적지 검색
    @GET("api/places/search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("size") size: Int = 5
    ): APIResponse<PlacesSearchData>

    // 2. 인근 전기차 충전소 검색 (30분 전 [사용 연장]용)
    @GET("api/chargers/nearby")
    suspend fun searchNearbyChargers(
        @Query("lat") lat: Double = 37.6242,
        @Query("lng") lng: Double = 128.7420,
        @Query("radius_m") radiusM: Int = 20000
    ): APIResponse<ChargersNearbyData>

    // 2-1. 즐겨찾기 목적지('우리 집' 등) 조회 / 등록 / 수정 / 삭제
    @GET("api/favorites")
    suspend fun listFavorites(): APIResponse<FavoritesListData>

    @POST("api/favorites")
    suspend fun upsertFavorite(
        @Body request: FavoriteCreateRequest
    ): APIResponse<FavoriteItem>

    @PUT("api/favorites/{favoriteId}")
    suspend fun updateFavorite(
        @Path("favoriteId") favoriteId: Int,
        @Body request: FavoriteUpdateRequest
    ): APIResponse<FavoriteItem>

    @DELETE("api/favorites/{favoriteId}")
    suspend fun deleteFavorite(
        @Path("favoriteId") favoriteId: Int
    ): APIResponse<Map<String, Int>>

    // 3. 차량 스펙 및 운전자 평소 전비 조회
    @GET("api/user")
    suspend fun getUserVehicle(): APIResponse<UserVehicleData>

    // 4. 차량 스펙 및 전비 수정 저장
    @PUT("api/user")
    suspend fun updateUserVehicle(
        @Body request: UserVehicleUpdateRequest
    ): APIResponse<UserVehicleData>

    // 5. AI 귀가 배터리 소모량 예측 (3단계 마진 산출)
    @POST("api/predict")
    suspend fun predictConsumption(
        @Body request: PredictRequest
    ): APIResponse<PredictData>

    // 6. V2L 보호 모니터링 세션 시작
    @POST("api/sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): APIResponse<SessionStatusData>

    // 7. 실시간 세션 상태 조회
    @GET("api/sessions/{sessionId}")
    suspend fun getSessionStatus(
        @Path("sessionId") sessionId: Int
    ): APIResponse<SessionStatusData>

    // 7-1. 실시간 EvBattery SoC 절대값 동기화
    @PATCH("api/sessions/{sessionId}/soc")
    suspend fun syncSessionSoc(
        @Path("sessionId") sessionId: Int,
        @Body request: SocSyncRequest
    ): APIResponse<SessionStatusData>

    // 7-2. 세션 감사 이벤트 로그 조회
    @GET("api/sessions/{sessionId}/events")
    suspend fun getSessionEvents(
        @Path("sessionId") sessionId: Int
    ): APIResponse<SessionEventsData>

    // 8. 시연용 배터리 차감 시뮬레이션 Tick
    @POST("api/sessions/{sessionId}/tick")
    suspend fun tickSession(
        @Path("sessionId") sessionId: Int,
        @Body request: TickRequest = TickRequest()
    ): APIResponse<SessionStatusData>

    // 9. 30분 전 알림 [사용 연장] (인근 충전소 기준 재계산)
    @POST("api/sessions/{sessionId}/extend")
    suspend fun extendSession(
        @Path("sessionId") sessionId: Int,
        @Body request: ExtendRequest = ExtendRequest()
    ): APIResponse<ExtendSessionData>

    // 10. V2L 즉시 차단 & 무시동 히터 21℃ 가동
    @POST("api/sessions/{sessionId}/lockout")
    suspend fun lockoutSession(
        @Path("sessionId") sessionId: Int
    ): APIResponse<LockoutData>
}