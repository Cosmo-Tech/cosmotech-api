// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.utils

import com.cosmotech.common.config.CsmPlatformProperties
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.data.domain.PageRequest

fun constructPageRequest(page: Int?, size: Int?, defaultPageSize: Int): PageRequest? =
    when {
      page != null && size != null -> PageRequest.of(page, size)
      page != null && size == null -> PageRequest.of(page, defaultPageSize)
      page == null && size != null -> PageRequest.of(0, size)
      else -> null
    }

fun <T> findAllPaginated(
    maxResult: Int,
    findAllLambda: (pageRequest: PageRequest) -> List<T>,
): MutableList<T> {
  var pageRequest = PageRequest.ofSize(maxResult)
  val list = mutableListOf<T>()
  do {
    val objectList = findAllLambda(pageRequest)
    pageRequest = pageRequest.next()
    list.addAll(objectList)
  } while (objectList.isNotEmpty())
  return list
}

fun getLocalDateNow(pattern: String = "yyyy/MM/dd - HH:mm:ss"): String {
  val current = LocalDateTime.now()
  val formatter = DateTimeFormatter.ofPattern(pattern)
  return current.format(formatter)
}

fun getCurrentUserSecurityConstraints(configuration: CsmPlatformProperties): String {
  val identifiers = getCurrentAccountGroups(configuration).toMutableList()
  val currentUser = getCurrentAccountIdentifier(configuration)
  identifiers.add(currentUser)
  val userFilters =
      identifiers.joinToString(separator = "|") {
        "@security_accessControlList_id:{${it.sanitizeForRedis()}}"
      }
  return "((-@security_default:{none})|($userFilters -@security_accessControlList_role:{none}))"
}
