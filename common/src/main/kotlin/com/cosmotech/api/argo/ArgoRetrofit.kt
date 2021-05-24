// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.argo

import com.cosmotech.api.utils.getUnsafeOkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

@Component
class ArgoRetrofit(@Value("\${csm.platform.argo.base-uri:}") val baseUrl: String) {
  fun getUnsafeScalarRetrofit(): Retrofit {
    val okHttpClient = getUnsafeOkHttpClient()
    return Retrofit.Builder()
        .addConverterFactory(ScalarsConverterFactory.create())
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .build()
  }

  fun getLogArtifact(namespace: String, workflow: String, node: String, artifact: String): String {
    val retrofit = getUnsafeScalarRetrofit()
    val artifactsService = retrofit.create(ArgoArtifactsService::class.java)
    val call = artifactsService.returnArtifact(namespace, workflow, node, artifact)
    val result = call.execute().body()
    return result ?: ""
  }

  fun getLogArtifactByUid(workflowId: String, node: String, artifact: String): String {
    val retrofit = getUnsafeScalarRetrofit()
    val artifactsService = retrofit.create(ArgoArtifactsByUidService::class.java)
    val call = artifactsService.returnArtifact(workflowId, node, artifact)
    val result = call.execute().body()
    return result ?: ""
  }
}
