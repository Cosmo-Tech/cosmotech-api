// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.meta.service

import com.cosmotech.common.CsmPhoenixService
import com.cosmotech.common.utils.getAboutInfo
import com.cosmotech.meta.MetaApiServiceInterface
import com.cosmotech.meta.domain.AboutInfo
import com.cosmotech.meta.domain.AboutInfoVersion
import org.springframework.stereotype.Service

@Service
class MetaServiceImpl : CsmPhoenixService(), MetaApiServiceInterface {
  override fun about(): AboutInfo {
    val aboutVersion = getAboutInfo().getJSONObject("version")
    val releaseVersion = aboutVersion.getString("release")
    // Separate the label out on the first '-'
    val numbersAndLabel = releaseVersion.split('-', limit = 2)
    // Split the numbers
    val numbers = numbersAndLabel.first().split('.', limit = 3)

    return AboutInfo(
        version =
            AboutInfoVersion(
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
