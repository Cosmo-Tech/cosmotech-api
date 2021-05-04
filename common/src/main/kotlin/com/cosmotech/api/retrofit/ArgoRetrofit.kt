// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.retrofit

import com.cosmotech.api.utils.getUnsafeOkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

fun getArgoUnsafeScalarRetrofit(): Retrofit {
  val okHttpClient = getUnsafeOkHttpClient()
  return Retrofit.Builder()
      .addConverterFactory(ScalarsConverterFactory.create())
      .baseUrl("https://argo-server.argo.svc.cluster.local:2746")
      .client(okHttpClient)
      .build()
}

fun getArgoLogArtifact(
    namespace: String,
    workflow: String,
    node: String,
    artifact: String
): String {
  val retrofit = getArgoUnsafeScalarRetrofit()
  val artifactsService = retrofit.create(ArgoArtifactsService::class.java)
  val call = artifactsService.returnArtifact(namespace, workflow, node, artifact)
  val result = call.execute().body()
  return result ?: ""
}

fun getArgoLogArtifactByUid(workflowId: String, node: String, artifact: String): String {
  val retrofit = getArgoUnsafeScalarRetrofit()
  val artifactsService = retrofit.create(ArgoArtifactsByUidService::class.java)
  val call = artifactsService.returnArtifact(workflowId, node, artifact)
  val result = call.execute().body()
  return result ?: ""
}
