package com.mingliu.inventoryapp

import android.content.Context
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ========================================================
// 1. API route definitions (all relative to RetrofitClient.BASE_URL)
// ========================================================
interface ApiService {

    /** Health check used to keep a free-tier host warm. */
    @GET("/api/ping")
    fun pingServer(): Call<ResponseBody>

    /** Lists all operators. Admin only (enforced server-side). */
    @GET("/api/operators")
    fun getAllOperators(): Call<List<OperatorResponse>>

    @FormUrlEncoded
    @POST("/api/login")
    fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse>

    @GET("/api/stock/valuation")
    fun getStockValuation(): Call<StockValuationResponse>

    /** Transaction history for a single item, newest first. */
    @GET("/api/transactions/item/{item_id}")
    fun getItemHistory(
        @Path("item_id") itemId: Int
    ): Call<List<TransactionHistoryItem>>

    @POST("/api/transactions")
    fun createTransaction(
        @Body request: TransactionRequest
    ): Call<TransactionResponse>

    @POST("/api/items")
    fun createItem(
        @Body request: ItemCreateRequest
    ): Call<ItemCreateResponse>

    @POST("/api/operators")
    fun createOperator(
        @Body request: OperatorCreateRequest
    ): Call<ResponseBody>

    @DELETE("/api/operators/{operator_id}")
    fun deleteOperator(
        @Path("operator_id") operatorId: Int
    ): Call<ResponseBody>

    @POST("api/operators/{operator_id}/password")
    fun changePassword(
        @Path("operator_id") operatorId: Int,
        @Body request: PasswordChangeRequest
    ): Call<ResponseBody>

    /** Full stock transaction log across all items, newest first. */
    @GET("api/stock/global-history")
    fun getGlobalHistory(): Call<List<GlobalLogResponse>>
}

// ========================================================
// 2. Data models (mirror the backend's Pydantic models/response shapes)
// ========================================================

data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val is_admin: Boolean,
    val operator_id: Int
)

data class StockValuationResponse(
    val details: List<Product>,
    val total_twd_amount: Double
)

/** A stock item as returned by /api/stock/valuation. */
data class Product(
    @SerializedName("item_id", alternate = ["id", "id_item"])
    val item_id: Int,
    val item_name: String,
    val category: String,
    val current_qty: Int,
    val usd_price: Any,
    val twd_amount: Any,
    val last_update_date: String?,
    val last_update_time: String?
)

data class TransactionRequest(
    val item_id: Int,
    val io_type: String, // "IN" or "OUT"
    val transaction_qty: Int,
    val remark: String
)

data class TransactionResponse(
    val message: String,
    val current_stock: Int
)

data class ItemCreateRequest(
    val item_name: String,
    val category: String, // "Main" or "Accessories"
    val usd_price: Double,
    val exchange_rate: Double,
    val tax_coefficient: Double
)

data class ItemCreateResponse(
    val message: String,
    val item_id: Int
)

data class TransactionHistoryItem(
    val transaction_date: String,
    val transaction_time: String,
    val io_type: String, // "IN" or "OUT"
    val qty: Int,        // sign already applied server-side (+ for IN, - for OUT)
    val post_balance_qty: Int,
    val remark: String?,
    val operator_name: String
)

data class OperatorCreateRequest(
    val operator_name: String,
    val password: String,
    val is_admin: Boolean = false
)

data class OperatorResponse(
    val operator_id: Int,
    val operator_name: String,
    val is_admin: Boolean
)

data class GlobalLogResponse(
    val tran_id: Int,
    val item_id: Int,
    val item_name: String,
    val io_type: String,
    val transaction_qty: Int,
    val transaction_date: String,
    val transaction_time: String,
    val remark: String?,
    val post_balance_qty: Int,
    val operator_name: String?
)

data class PasswordChangeRequest(
    val old_password: String, // required when a non-admin changes their own password
    val new_password: String
)

// ========================================================
// 3. Retrofit client (single source of truth for BASE_URL)
// ========================================================
object RetrofitClient {

    //private const val BASE_URL = "https://inventoryapp-s3w2.onrender.com"
    private const val BASE_URL = "https://inventoryapp-c6f7.onrender.com"
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor(appContext))
            .addInterceptor(AuthExpiredInterceptor(appContext))
            .build()
    }

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
