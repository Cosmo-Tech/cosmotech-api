// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.twingraph.bulk

internal fun Long.to8ByteArrayInLittleEndian(): ByteArray =
    (0 until Long.SIZE_BYTES).map { (this shr (it * Byte.SIZE_BITS)).toByte() }.toByteArray()

internal fun Long.to8ByteArrayinBigEndian(): ByteArray =
    (Long.SIZE_BYTES - 1 downTo 0).map { (this shr (it * Byte.SIZE_BITS)).toByte() }.toByteArray()

internal fun UInt.to4ByteArrayInLittleEndian(): ByteArray =
    (0 until UInt.SIZE_BYTES).map { (this shr (it * Byte.SIZE_BITS)).toByte() }.toByteArray()

internal fun String.toNullTerminatedByteArray(): ByteArray {
  return this.toByteArray() + Char.MIN_VALUE.toString().toByteArray()
}

data class HeaderBinaryBlobFormat(
    private val name: String,
    private val propertyCount: UInt,
    private val propertyNames: List<String>
) {
  public var binary: ByteArray = byteArrayOf()
  public var size: Int = 0

  init {
    binary += name.toNullTerminatedByteArray()
    binary += propertyCount.to4ByteArrayInLittleEndian()
    for (name in propertyNames) binary += name.toNullTerminatedByteArray()

    size = binary.size
  }
}

@Suppress("MagicNumber")
enum class PropertyType(val code: Byte) {
  BI_NULL(0),
  BI_BOOL(1),
  BI_DOUBLE(2),
  BI_STRING(3),
  BI_LONG(4),
  BI_ARRAY(5)
}

data class PropertyBinaryBlobFormat(private val type: PropertyType, private val property: Any) {
  public var binary: ByteArray = byteArrayOf()
  public var size: Int = 0

  init {
    binary += type.code
    when (type) {
      PropertyType.BI_NULL -> {
        // rien
      }
      PropertyType.BI_BOOL -> {
        val p = property as Boolean
        if (p) {
          val one: Byte = 1
          binary += one
        } else {
          val zero: Byte = 0
          binary += zero
        }
      }
      PropertyType.BI_DOUBLE -> {
        val p = property as Double
        binary += p.toBits().to8ByteArrayInLittleEndian()
      }
      PropertyType.BI_STRING -> {
        val p = property as String
        binary += p.toNullTerminatedByteArray()
      }
      PropertyType.BI_LONG -> {
        val p = property as Int
        binary += p.toLong().to8ByteArrayInLittleEndian()
      }
      PropertyType.BI_ARRAY -> {
        // TODO spec not clear
        val p = property as List<String>
        val size = p.size as Long
        binary += size.to8ByteArrayInLittleEndian()
        for (a in p) binary += a.toByteArray()
      }
    }

    size = binary.size
  }
}

data class NodeBinaryBlobFormat(val properties: List<PropertyBinaryBlobFormat>) {
  var binary: ByteArray = byteArrayOf()
  var size: Int = 0

  init {
    for (p in properties) binary += p.binary

    size = binary.size
  }
}

data class EdgeBinaryBlobFormat(
    val source: Long,
    val target: Long,
    val properties: List<PropertyBinaryBlobFormat>
) {
  var binary: ByteArray = byteArrayOf()
  var size: Int = 0

  init {
    binary += source.to8ByteArrayInLittleEndian()
    binary += target.to8ByteArrayInLittleEndian()
    for (p in properties) binary += p.binary

    size = binary.size
  }
}
