// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.runner

/**
 * Constant class that contains for Runner endpoints:
 * - default payload (RequestContent) for API calls
 * - default error messages (Errors) returned by API
 */
object RunnerConstants {

  const val RUNNER_NAME = "my_runner_name"
  const val RUNNER_OWNER_NAME = "John Doe"
  const val RUNNER_RUN_TEMPLATE = "runtemplate1"
  const val NEW_USER_ID = "new.user@cosmotech.com"
  const val NEW_USER_ROLE = "editor"
  const val NEW_RUNNER_NAME = "my_new_runner_name"

  object RequestContent {}

  object Errors {}
}
