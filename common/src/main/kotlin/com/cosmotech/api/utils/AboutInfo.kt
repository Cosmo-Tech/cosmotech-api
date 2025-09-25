// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.io.BufferedReader
import org.json.JSONException
import org.json.JSONObject

fun getAboutInfo(): JSONObject {
  val aboutJsonInputStream =
      object {}::class.java.getResourceAsStream("/about.json")
          ?: throw IllegalStateException(
              "Unable to read about info data from 'classpath:/about.json'")
  val aboutJsonContent =
      aboutJsonInputStream.use { it.bufferedReader().use(BufferedReader::readText) }

  try {
    return JSONObject(aboutJsonContent)
  } catch (e: JSONException) {
    throw IllegalStateException("Unable to parse about info from 'classpath:/about.json'", e)
  }
}
