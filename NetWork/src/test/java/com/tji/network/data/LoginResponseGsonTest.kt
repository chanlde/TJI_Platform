package com.tji.network.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginResponseGsonTest {

    @Test
    fun parsesLoginApiResponseWithAnnotatedFieldNames() {
        val type = object : TypeToken<ApiResponse<LoginResponse>>() {}.type
        val response: ApiResponse<LoginResponse> = Gson().fromJson(
            """
            {
              "code": 200,
              "message": "成功",
              "data": {
                "id": "user-1",
                "token": "token-value",
                "boundDevices": [
                  {
                    "id": 188,
                    "sn1": "SN001",
                    "productName": "光伏清洗 01",
                    "productId": 3,
                    "productType": "SolarClean",
                    "productCode": "SolarClean"
                  }
                ]
              }
            }
            """.trimIndent(),
            type
        )

        assertEquals(200, response.code)
        assertEquals("成功", response.message)
        assertEquals("user-1", response.data?.id)
        assertEquals("token-value", response.data?.token)
        assertEquals("SN001", response.data?.boundDevices?.single()?.sn1)
        assertEquals("光伏清洗 01", response.data?.boundDevices?.single()?.productName)
    }
}
