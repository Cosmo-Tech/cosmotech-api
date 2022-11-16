package com.cosmotech.scenariorun.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "workflow")
data class ContainerConfig(
    val containers: List<ContainerInfo>
) {
    data class ContainerInfo(
        val name:String,
        val imageRegistry:String = "",
        val imageName: String,
        val imageVersion: String = "latest",
    )
}