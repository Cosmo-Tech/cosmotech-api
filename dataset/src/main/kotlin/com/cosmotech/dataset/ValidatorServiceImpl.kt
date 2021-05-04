// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.dataset.api.ValidatorApiService
import com.cosmotech.dataset.domain.Validator
import com.cosmotech.dataset.domain.ValidatorRun
import org.springframework.stereotype.Service

@Service
class ValidatorServiceImpl : AbstractPhoenixService(), ValidatorApiService {
  override fun findAllValidators(organizationId: String): List<Validator> {
    TODO("Not yet implemented")
  }

  override fun findValidatorById(organizationId: String, validatorId: String): Validator {
    TODO("Not yet implemented")
  }

  override fun createValidator(organizationId: String, validator: Validator): Validator {
    TODO("Not yet implemented")
  }

  override fun deleteValidator(organizationId: String, validatorId: String) {
    TODO("Not yet implemented")
  }

  override fun runValidator(
      organizationId: String,
      validatorId: String,
      validatorRun: ValidatorRun
  ): ValidatorRun {
    TODO("Not yet implemented")
  }

  override fun findAllValidatorRuns(
      organizationId: String,
      validatorId: String
  ): List<ValidatorRun> {
    TODO("Not yet implemented")
  }

  override fun findValidatorRunById(
      organizationId: String,
      validatorId: String,
      validatorrunId: String
  ): ValidatorRun {
    TODO("Not yet implemented")
  }

  override fun createValidatorRun(
      organizationId: String,
      validatorId: String,
      validatorRun: ValidatorRun
  ): ValidatorRun {
    TODO("Not yet implemented")
  }

  override fun deleteValidatorRun(
      organizationId: String,
      validatorId: String,
      validatorrunId: String
  ) {
    TODO("Not yet implemented")
  }
}
