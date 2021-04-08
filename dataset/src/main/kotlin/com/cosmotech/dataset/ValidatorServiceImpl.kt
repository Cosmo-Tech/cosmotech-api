// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.dataset

import com.cosmotech.api.AbstractPhoenixService
import com.cosmotech.dataset.api.ValidatorApiService
import com.cosmotech.dataset.domain.Validator
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

  override fun deleteValidator(organizationId: String, validatorId: String): Validator {
    TODO("Not yet implemented")
  }
}
