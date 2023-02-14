package com.cosmotech.dataset.service

import com.cosmotech.api.utils.getCurrentAuthenticatedMail
import com.cosmotech.api.utils.getCurrentAuthenticatedUserName
import com.cosmotech.dataset.api.DatasetApiService
import com.cosmotech.dataset.domain.Dataset
import com.cosmotech.organization.api.OrganizationApiService
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit4.SpringRunner

@ActiveProfiles(profiles = ["dataset-test"])
@ExtendWith(MockKExtension::class)
@ExtendWith(SpringExtension::class)
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasetServiceIntegrationTest {

    private val logger = LoggerFactory.getLogger(DatasetServiceIntegrationTest::class.java)

    @Autowired
    lateinit var datasetApiService: DatasetApiService

    @BeforeEach
    fun setUp() {
        mockkStatic("com.cosmotech.api.utils.SecurityUtilsKt")
        every { getCurrentAuthenticatedMail(any()) } returns "test.user@cosmotech.com"
        every { getCurrentAuthenticatedUserName() } returns "test.user"
    }

    @Test
    fun test_dataset(){

    }
}