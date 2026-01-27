// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter

/**
 * This class is here as a suggested workaround for the following issues. Swagger UI does not handle
 * multipart requests correctly even if `encoding` property is set in openapi.yaml Each part of a
 * multipart/form-data is sent with a content-type set to application/octet-stream The following
 * workaround adds application/octet-stream has a supported MediaType for the
 * MappingJackson2HttpMessageConverter (deprecated and replaced by JacksonJsonHttpMessageConverter)
 * https://github.com/swagger-api/swagger-ui/issues/6462
 * https://github.com/swagger-api/swagger-ui/issues/5356
 * https://github.com/swagger-api/swagger-ui/issues/9548
 * https://github.com/swagger-api/swagger-ui/issues/6462
 * https://github.com/swagger-api/swagger-ui/issues/9548
 */
@Configuration
open class SwaggerBeanConfiguration(converter: JacksonJsonHttpMessageConverter) {

  init {
    val supportedMediaTypes = converter.supportedMediaTypes
    supportedMediaTypes.add(MediaType("application", "octet-stream"))
    converter.supportedMediaTypes = supportedMediaTypes
  }
}
