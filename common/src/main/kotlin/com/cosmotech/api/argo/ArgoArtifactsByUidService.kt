// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

internal interface ArgoArtifactsByUidService {
  @GET("artifacts-by-uid/{uid}/{node}/{artifact}")
  fun returnArtifact(
      @Path("uid") uid: String,
      @Path("node") node: String,
      @Path("artifact") artifact: String
  ): Call<String>
}
