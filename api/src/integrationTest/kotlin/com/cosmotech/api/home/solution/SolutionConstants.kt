// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.solution

/**
 * Constant class that contains for Solution endpoints:
 * - default payload (RequestContent) for API calls
 * - default error messages (Errors) returned by API
 */
object SolutionConstants {

  const val SOLUTION_NAME = "my_solution_name"
  const val SOLUTION_KEY = "my_solution_key"
  const val SOLUTION_REPOSITORY = "solution_repository"
  const val SOLUTION_VERSION = "1.0.0"
  const val SOLUTION_SDK_VERSION = "11.3.0-45678.abcdef12"
  const val NEW_USER_ID = "new.user@cosmotech.com"
  const val NEW_USER_ROLE = "editor"
  const val NEW_SOLUTION_NAME = "my_new_solution_name"

  object RequestContent {}

  object Errors {}
}
