// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home.dataset

/**
 * Constant class that contains for Dataset endpoints:
 * - default payload (RequestContent) for API calls
 * - default error messages (Errors) returned by API
 */
object DatasetConstants {

  const val DATASET_NAME = "my_dataset_name"
  const val DATASET_DESCRIPTION = "this_is_a_description"
  const val DATASET_PART_NAME = "my_dataset_part_name"
  const val DATASET_PART_DESCRIPTION = "this_is_a_description_for_my_dataset_part"
  const val TEST_FILE_NAME = "test.txt"
  const val NEW_USER_ID = "new.user@cosmotech.com"
  const val NEW_USER_ROLE = "editor"
  const val NEW_DATASET_NAME = "my_new_dataset_name"

  object RequestContent {}

  object Errors {}
}
