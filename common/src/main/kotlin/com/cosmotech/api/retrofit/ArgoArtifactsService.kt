// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.retrofit

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface ArgoArtifactsService {
  @GET("artifacts/{namespace}/{workflow}/{node}/{artifact}")
  fun returnArtifact(
      @Path("namespace") namespace: String,
      @Path("workflow") workflow: String,
      @Path("node") node: String,
      @Path("artifact") artifact: String
  ): Call<kotlin.String>
}
