// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.scenariorun.workflow.argo

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

internal interface ArgoArtifactsService {
  @GET("artifacts/{namespace}/{workflow}/{node}/{artifact}")
  fun getArtifact(
      @Path("namespace") namespace: String,
      @Path("workflow") workflow: String,
      @Path("node") node: String,
      @Path("artifact") artifact: String
  ): Call<String>
}

internal interface ArgoArtifactsByUidService {
  @GET("artifacts-by-uid/{uid}/{node}/{artifact}")
  fun getArtifactByUid(
      @Path("uid") uid: String,
      @Path("node") node: String,
      @Path("artifact") artifact: String
  ): Call<String>
}
