// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.Instant

class InstantTypeAdapter : TypeAdapter<Instant>() {
  override fun write(out: JsonWriter, value: Instant?) {
    if (value == null) {
      out.nullValue()
    } else {
      out.value(value.toString())
    }
  }

  override fun read(`in`: JsonReader): Instant? {
    return if (`in`.peek() == JsonToken.NULL) {
      `in`.nextNull()
      null
    } else {
      Instant.parse(`in`.nextString())
    }
  }
}
