// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import jakarta.xml.bind.annotation.adapters.HexBinaryAdapter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val KUBERNETES_RESOURCE_NAME_MAX_LENGTH = 63

/**
 * Sanitize the given String for use as a Kubernetes resource name.
 *
 * By convention, the names of Kubernetes resources should be up to maximum length of 253 characters
 * and consist of lower case alphanumeric characters, -, and ., but certain resources have more
 * specific restrictions.
 *
 * See https://kubernetes.io/docs/concepts/overview/working-with-objects/names/
 *
 * @throws IllegalArgumentException if [maxLength] is negative.
 */
fun String.sanitizeForKubernetes(maxLength: Int = KUBERNETES_RESOURCE_NAME_MAX_LENGTH) =
    this.replace("/", "-")
        .replace(":", "-")
        .replace("_", "-")
        .replace(".", "-")
        .lowercase()
        .takeLast(maxLength)

fun String.sanitizeForRedis() =
    this.replace("@", "\\\\@").replace(".", "\\\\.").replace("-", "\\\\-")

fun String.toSecurityConstraintQuery() =
    "((-@security_default:{none})|(@security_accessControlList_id:{${this.sanitizeForRedis()}}" +
        " -@security_accessControlList_role:{none}))"

fun String.shaHash(): String {
  val messageDigest = MessageDigest.getInstance("SHA-256")
  messageDigest.update(this.toByteArray(StandardCharsets.UTF_8))
  return (HexBinaryAdapter()).marshal(messageDigest.digest())
}

fun String.toRedisMetaDataKey() = "${this}MetaData"

fun String.formatQuery(map: Map<String, String>): String {
  var newValue = this
  map.forEach { (key, value) ->
    val sanitizedValue =
        if (value.isNullOrBlank()) {
          "null"
        } else {
          "\"${value.replace("\"","\\\"")}\""
        }
    newValue = newValue.replace("$${key.trim()}", sanitizedValue)
  }
  return newValue
}

/**
 * Extract the file name from a path for example: /path/to/file.txt -> file
 *
 * @return the file name without the extension
 */
fun String.extractFileNameFromPath(): String {
  val prefixLess = this.split("/").last()
  return prefixLess.split(".")[0]
}
