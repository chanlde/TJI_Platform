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

    @Test
    fun malformedTypedDeviceRowsDoNotDiscardValidRows() {
        val response = Gson().fromJson(
            """
            {
              "id": "user-1",
              "token": "token-value",
              "cleansns": [
                { "sn1": { "unexpected": true }, "id": "bad" },
                true,
                {
                  "sn1": "SOLAR-001",
                  "id": "not-an-int",
                  "productId": "3",
                  "productName": ["wrong-shape"]
                }
              ]
            }
            """.trimIndent(),
            LoginResponse::class.java
        )

        val devices = response.cleanDevicesResolved()

        assertEquals(1, devices.size)
        assertEquals("SOLAR-001", devices.single().sn1)
        assertEquals(null, devices.single().id)
        assertEquals(3, devices.single().productId)
        assertEquals(null, devices.single().productName)
        assertEquals("SolarClean", devices.single().productType)
    }
}
