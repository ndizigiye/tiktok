package com.ndizigiye.loop.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** JSON configuration shared by Retrofit and local persistence (see docs/api.md). */
@OptIn(ExperimentalSerializationApi::class)
val LoopJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
