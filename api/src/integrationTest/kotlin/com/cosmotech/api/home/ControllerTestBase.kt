// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.home

import com.cosmotech.api.home.Constants.ORGANIZATION_USER_EMAIL
import com.cosmotech.api.home.Constants.PLATFORM_ADMIN_EMAIL
import com.cosmotech.common.tests.CsmTestBase
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.dataset.domain.DatasetPart
import com.cosmotech.organization.domain.Organization
import com.cosmotech.run.domain.Run
import com.cosmotech.runner.domain.Runner
import com.cosmotech.solution.domain.Solution
import com.cosmotech.workspace.domain.Workspace
import com.redis.om.spring.indexing.RediSearchIndexer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@EnableWebSecurity
@ExtendWith(RestDocumentationExtension::class)
@AutoConfigureRestDocs
abstract class ControllerTestBase : CsmTestBase() {

  @Autowired private lateinit var context: WebApplicationContext

  @Autowired lateinit var rediSearchIndexer: RediSearchIndexer

  lateinit var mvc: MockMvc

  private val logger = LoggerFactory.getLogger(ControllerTestBase::class.java)

  @BeforeEach
  fun beforeEach(restDocumentationContextProvider: RestDocumentationContextProvider) {

    rediSearchIndexer.createIndexFor(Organization::class.java)
    rediSearchIndexer.createIndexFor(Workspace::class.java)
    rediSearchIndexer.createIndexFor(Dataset::class.java)
    rediSearchIndexer.createIndexFor(DatasetPart::class.java)
    rediSearchIndexer.createIndexFor(Solution::class.java)
    rediSearchIndexer.createIndexFor(Runner::class.java)
    rediSearchIndexer.createIndexFor(Run::class.java)

    this.mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .alwaysDo<DefaultMockMvcBuilder> { result ->
              if (logger.isTraceEnabled) {
                val response = result.response
                logger.trace(
                    """
                 <<< Response : 
                 [${response.status}]
                 ${response.headerNames.associateWith { response.getHeaderValues(it) }.entries.joinToString("\n")}}
                    
                  ${response.contentAsString}
                """
                        .trimIndent()
                )
              }
            }
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .apply<DefaultMockMvcBuilder>(
                documentationConfiguration(restDocumentationContextProvider)
                    .operationPreprocessors()
                    .withRequestDefaults(
                        modifyHeaders().remove(HttpHeaders.CONTENT_LENGTH),
                        modifyHeaders().remove(PLATFORM_ADMIN_EMAIL),
                        modifyHeaders().remove(ORGANIZATION_USER_EMAIL),
                        prettyPrint(),
                    )
                    .withResponseDefaults(
                        modifyHeaders()
                            .remove("X-Content-Type-Options")
                            .remove("X-XSS-Protection")
                            .remove("X-Frame-Options")
                            .remove(HttpHeaders.CONTENT_LENGTH)
                            .remove(HttpHeaders.CACHE_CONTROL),
                        prettyPrint(),
                    )
            )
            .build()
  }
}
