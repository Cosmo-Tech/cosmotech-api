// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.iaminfo.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.utils.getAboutInfo
import com.cosmotech.iaminfo.IAMInfoApiServiceInterface
import com.cosmotech.iaminfo.domain.IAMInfo
import com.cosmotech.iaminfo.domain.IAMInfoVersion
import org.springframework.stereotype.Service

@Service
class IAMInfoServiceImpl : CsmPhoenixService(), IAMInfoApiServiceInterface {
  override fun getIAMInfo(): IAMInfo {
    val aboutVersion = getAboutInfo().getJSONObject("version")
    val releaseVersion = aboutVersion.getString("release")
    // Separate the label out on the first '-'
    val numbersAndLabel = releaseVersion.split('-', limit = 2)
    // Split the numbers
    val numbers = numbersAndLabel.first().split('.', limit = 3)

    return IAMInfo(
        version =
            IAMInfoVersion(
                release = releaseVersion,
                full = aboutVersion.getString("full"),
                major = numbers[0].toInt(),
                minor = numbers[1].toInt(),
                patch = numbers[2].toInt(),
                label = numbersAndLabel.getOrElse(1, { "" }),
                build = aboutVersion.getString("build"),
            )
    )
  }
}
