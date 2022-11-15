// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.api

import com.cosmotech.twingraph.domain.TwinGraphImport
import com.cosmotech.twingraph.domain.TwinGraphImportInfo
import com.cosmotech.twingraph.domain.TwinGraphQuery
import org.springframework.stereotype.Service

@Service
class TwingraphServiceImpl : TwingraphApiService {

  override fun query(organizationId: String, twinGraphQuery: TwinGraphQuery): String {
    TODO("Not yet implemented")
  }

  override fun importGraph(
      organizationId: String,
      twinGraphImport: TwinGraphImport
  ): TwinGraphImportInfo {
    TODO("Not yet implemented")
  }

  override fun delete() {
    TODO("Not yet implemented")
  }
}
