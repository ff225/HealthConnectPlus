package com.research.healthconnectplus

import com.influxdb.client.InfluxDBClientFactory
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

private const val BASE_URL =
    "http://192.168.1.37:8086/"

private val retrofit = Retrofit.Builder().addConverterFactory(ScalarsConverterFactory.create())
    .baseUrl(BASE_URL)
    .build()

private const val TOKEN =
    "JkQNpogRlMiBndpMcGI0OYB74Lz9TXbfyIRzrmx9SO_0BJ2gAlj-qzJmolhgGkfav-rfCaS8KVxuek38DdznRw=="
private const val ORG = "hcplus"
private const val BUCKET = "hcplus"
private const val PRECISION = "s"

//data class InfluxData(val value: Int)

interface ApiInfluxDB {
    @POST("/api/v2/write")
    @Headers(
        "Content-Type: text/plain; charset=utf-8",
        "Accept: application/json"
    )
    suspend fun writeData(
        @Query("org") org: String = ORG,
        @Query("bucket") bucket: String = BUCKET,
        @Query("precision") precision: String = PRECISION,
        @Header("Authorization") token: CharArray = TOKEN.toCharArray(),
        @Body data: String
    ): String
}

object InfluxApi {
    val retrofitService: ApiInfluxDB by lazy {
        retrofit.create(ApiInfluxDB::class.java)
    }
}

object InfluxDB {
    val client = InfluxDBClientFactory.create(BASE_URL, TOKEN.toCharArray(), ORG, BUCKET)
}