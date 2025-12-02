// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.data.domain.PageRequest

const val BULK_QUERY_KEY = "bulkQuery"

fun constructPageRequest(page: Int?, size: Int?, defaultPageSize: Int): PageRequest? {
  return when {
    page != null && size != null -> PageRequest.of(page, size)
    page != null && size == null -> PageRequest.of(page, defaultPageSize)
    page == null && size != null -> PageRequest.of(0, size)
    else -> null
  }
}

fun <T> findAllPaginated(
    maxResult: Int,
    findAllLambda: (pageRequest: PageRequest) -> MutableList<T>,
): MutableList<T> {
  var pageRequest = PageRequest.ofSize(maxResult)
  var list = mutableListOf<T>()
  do {
    var objectList = findAllLambda(pageRequest)
    pageRequest = pageRequest.next()
    list.addAll(objectList)
  } while (objectList.isNotEmpty())
  return list
}

fun redisGraphKey(graphId: String, version: String): String {
  return "$graphId:$version"
}

fun bulkQueryKey(graphId: String, query: String, version: String?): Pair<ByteArray, String> {
  val redisGraphKey = if (version != null) redisGraphKey(graphId, version) else graphId
  val bulkQueryHash = "$redisGraphKey:$query".shaHash()
  return Pair("$BULK_QUERY_KEY:$bulkQueryHash".toByteArray(StandardCharsets.UTF_8), bulkQueryHash)
}

fun bulkQueryKey(bulkQueryHash: String): ByteArray {
  return "$BULK_QUERY_KEY:$bulkQueryHash".toByteArray(StandardCharsets.UTF_8)
}

fun getLocalDateNow(pattern: String = "yyyy/MM/dd - HH:mm:ss"): String {
  val current = LocalDateTime.now()
  val formatter = DateTimeFormatter.ofPattern(pattern)
  return current.format(formatter)
}
