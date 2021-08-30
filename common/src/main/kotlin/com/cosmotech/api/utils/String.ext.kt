// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

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
