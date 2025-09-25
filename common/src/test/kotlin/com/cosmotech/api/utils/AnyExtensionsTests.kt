// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.lang.IllegalArgumentException
import java.lang.UnsupportedOperationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

class AnyExtensionsTests {

  @Test
  fun `changed - change when comparing null to non-null`() {
    val existingObject: TestDataClass? = null
    val newObject = TestDataClass(id = "my-id", name = "my-name")
    assertTrue { newObject.changed(existingObject) { id } }
    assertTrue { newObject.changed(existingObject) { name } }
  }

  @Test
  fun `changed - change when comparing non-null to null`() {
    val existingObject = TestDataClass(id = "my-id", name = "my-name")
    val newObject: TestDataClass? = null
    assertTrue(existingObject.changed(newObject) { id })
    assertTrue(existingObject.changed(newObject) { name })
  }

  @Test
  fun `changed - change when compared to self`() {
    val existingObject = TestDataClass(id = "my-id", name = "my-name")
    assertFalse { existingObject.changed(existingObject) { id } }
    assertFalse { existingObject.changed(existingObject) { name } }
  }

  @Test
  fun `changed - detect change when comparing fields`() {
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

  @Test
  fun `compareTo supports data classes only`() {
    class NonDataClass(val attr: String, val anotherAttr: Boolean)
    val nonDataClassObj = NonDataClass("old_attr", true)

    assertThrows<UnsupportedOperationException> {
      nonDataClassObj.compareToAndMutateIfNeeded(NonDataClass("new_attr", false))
    }
    assertEquals("old_attr", nonDataClassObj.attr)
    assertTrue { nonDataClassObj.anotherAttr }
  }

  @Test
  fun `compareToAndMutateIfNeeded - should throw when trying to mutate non-mutable members`() {
    data class MyDataClass(val attr: String, val anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("old_attr", true)

    assertThrows<IllegalArgumentException> {
      myDataClassObj.compareToAndMutateIfNeeded(
          MyDataClass("new_attr", false), mutateIfChanged = true)
    }
    assertEquals("old_attr", myDataClassObj.attr)
    assertTrue { myDataClassObj.anotherAttr }
  }

  @Test
  fun `compareToAndMutateIfNeeded - no mutation if not told so`() {
    data class MyDataClass(var attr: String, val anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("old_attr", true)

    val changes =
        myDataClassObj.compareToAndMutateIfNeeded(
            MyDataClass("new_attr", false), mutateIfChanged = false)
    assertEquals(2, changes.size)
    assertTrue { "attr" in changes }
    assertTrue { "anotherAttr" in changes }

    assertEquals("old_attr", myDataClassObj.attr)
    assertTrue { myDataClassObj.anotherAttr }
  }

  @Test
  fun `compareToAndMutateIfNeeded - no change when compared to self`() {
    data class MyDataClass(val attr: String, val anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("attr1", true)

    assertTrue { myDataClassObj.compareToAndMutateIfNeeded(myDataClassObj).isEmpty() }
    assertEquals("attr1", myDataClassObj.attr)
    assertTrue { myDataClassObj.anotherAttr }
  }

  @Test
  fun `compareToAndMutateIfNeeded - id field is de facto excluded`() {
    data class MyDataClass(var id: String, var attr: String, var anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("id1", "attr1", true)

    val changes = myDataClassObj.compareToAndMutateIfNeeded(MyDataClass("id2", "attr2", true))

    assertEquals(1, changes.size)
    assertEquals("attr", changes.first())

    assertEquals("id1", myDataClassObj.id)
    assertEquals("attr2", myDataClassObj.attr)
    assertTrue { myDataClassObj.anotherAttr }
  }

  @Test
  fun `compareToAndMutateIfNeeded - no change if all fields excluded`() {
    data class MyDataClass(var id: String, var attr: String, var anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("id1", "attr1", true)

    val changes =
        myDataClassObj.compareToAndMutateIfNeeded(
            MyDataClass("id2", "attr2", false), excludedFields = arrayOf("attr", "anotherAttr"))

    assertTrue(changes.isEmpty())

    assertEquals("id1", myDataClassObj.id)
    assertTrue { myDataClassObj.anotherAttr }
    assertEquals("attr1", myDataClassObj.attr)
  }

  @Test
  fun `compareToAndMutateIfNeeded - handles collections besides other attributes`() {
    data class MyDataClass(var id: String, var listAttr: List<String>, var anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("id1", listOf("attr1", "attr2"), true)

    val changes =
        myDataClassObj.compareToAndMutateIfNeeded(
            MyDataClass("id2", listOf("attr2", "attr3", "attr4"), false))

    assertEquals(2, changes.size)
    assertTrue("listAttr" in changes)
    assertTrue("anotherAttr" in changes)
    assertFalse("id" in changes)

    assertEquals("id1", myDataClassObj.id)
    assertFalse { myDataClassObj.anotherAttr }
    assertEquals(listOf("attr2", "attr3", "attr4"), myDataClassObj.listAttr)
  }

  @Test
  fun `compareToAndMutateIfNeeded - handles collections regardless of order`() {
    data class MyDataClass(var id: String, var listAttr: List<String>, var anotherAttr: Boolean)
    val myDataClassObj = MyDataClass("id1", listOf("attr1", "attr2"), true)

    val changes =
        myDataClassObj.compareToAndMutateIfNeeded(
            MyDataClass("id2", listOf("attr2", "attr1"), false))

    assertEquals(1, changes.size)
    assertTrue("anotherAttr" in changes)

    assertEquals("id1", myDataClassObj.id)
    assertFalse { myDataClassObj.anotherAttr }
    assertEquals(listOf("attr1", "attr2"), myDataClassObj.listAttr)
  }
}

private data class TestDataClass(val id: String, val name: String)

private data class TestDataClassWithCollections(
    val dataList: List<TestDataClass> = listOf(),
    val dataMap: Map<String, TestDataClass> = mapOf()
)
