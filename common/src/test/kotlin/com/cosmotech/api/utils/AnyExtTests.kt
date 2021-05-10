// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnyExtTests {

  @Test
  fun `change when comparing null to non-null`() {
    val existingObject: TestDataClass? = null
    val newObject = TestDataClass(id = "my-id", name = "my-name")
    assertTrue { newObject.changed(existingObject) { id } }
    assertTrue { newObject.changed(existingObject) { name } }
  }

  @Test
  fun `change when comparing non-null to null`() {
    val existingObject = TestDataClass(id = "my-id", name = "my-name")
    val newObject: TestDataClass? = null
    assertTrue(existingObject.changed(newObject) { id })
    assertTrue(existingObject.changed(newObject) { name })
  }

  @Test
  fun `change when compared to self`() {
    val existingObject = TestDataClass(id = "my-id", name = "my-name")
    assertFalse { existingObject.changed(existingObject) { id } }
    assertFalse { existingObject.changed(existingObject) { name } }
  }

  @Test
  fun `detect change when comparing fields`() {
    val existingObject = TestDataClass(id = "my-id", name = "my-name")

    assertTrue {
      TestDataClass(id = "my-id-changed", name = "my-name").changed(existingObject) { id }
    }
    assertFalse {
      TestDataClass(id = "my-id-changed", name = "my-name").changed(existingObject) { name }
    }

    assertFalse {
      TestDataClass(id = "my-id", name = "my-name-changed").changed(existingObject) { id }
    }
    assertTrue {
      TestDataClass(id = "my-id", name = "my-name-changed").changed(existingObject) { name }
    }

    assertTrue {
      TestDataClass(id = "my-id-changed", name = "my-name-changed").changed(existingObject) { id }
    }
    assertTrue {
      TestDataClass(id = "my-id-changed", name = "my-name-changed").changed(existingObject) { name }
    }
  }

  @Test
  fun `detect change when comparing fields in a more-complex structure`() {
    val list = listOf(TestDataClass(id = "my-id", name = "my-name"))
    val map = mapOf("my-id-1" to TestDataClass(id = "my-id-1", name = "my-name-1"))
    val existingObject = TestDataClassWithCollections(dataList = list, dataMap = map)

    assertTrue { TestDataClassWithCollections(dataMap = map).changed(existingObject) { dataList } }
    assertFalse { TestDataClassWithCollections(list, map).changed(existingObject) { dataMap } }

    assertFalse {
      TestDataClassWithCollections(dataList = list).changed(existingObject) { dataList }
    }
    assertTrue { TestDataClassWithCollections(dataList = list).changed(existingObject) { dataMap } }

    assertTrue { TestDataClassWithCollections().changed(existingObject) { dataList } }
    assertTrue { TestDataClassWithCollections().changed(existingObject) { dataMap } }
  }
}

private data class TestDataClass(val id: String, val name: String)

private data class TestDataClassWithCollections(
    val dataList: List<TestDataClass> = listOf(),
    val dataMap: Map<String, TestDataClass> = mapOf()
)
