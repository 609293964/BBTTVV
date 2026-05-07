package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class IpLocationResponse(
    val code: Int = 0,
    val message: String = "",
    val data: IpLocationData? = null
)

@Serializable
data class IpLocationData(
    val addr: String = "",
    val country: String = "",
    val province: String = "",
    val city: String = ""
)
