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

internal fun getHeaderBinaryBlobFormat(name: String, propertyNames: List<String>): ByteArray {
  var binary: ByteArray = byteArrayOf()
  val propertyCount = propertyNames.size.toUInt()
  binary += name.toNullTerminatedByteArray()
  binary += propertyCount.to4ByteArrayInLittleEndian()
  for (propertyName in propertyNames) {
    binary += propertyName.toNullTerminatedByteArray()
  }
  return binary
}

internal fun getPropertyBinaryBlobFormat(type: PropertyType, property: Any?): ByteArray {
  var binary: ByteArray = byteArrayOf()
  binary += type.code
  when (type) {
    PropertyType.BI_NULL -> {
      // rien
    }
    PropertyType.BI_BOOL -> {
      val p = property.toString().toBoolean()
      if (p) {
        val one: Byte = 1
        binary += one
      } else {
        val zero: Byte = 0
        binary += zero
      }
    }
    PropertyType.BI_DOUBLE -> {
      val p = property.toString().toDouble()
      binary += p.toBits().to8ByteArrayInLittleEndian()
    }
    PropertyType.BI_STRING -> {
      val p = property.toString()
      binary += p.toNullTerminatedByteArray()
    }
    PropertyType.BI_LONG -> {
      val p = property.toString().toLong()
      binary += p.to8ByteArrayInLittleEndian()
    }
    PropertyType.BI_ARRAY -> {
      // TODO spec not clear
      val p = property as List<String>
      val size = p.size as Long
      binary += size.to8ByteArrayInLittleEndian()
      for (a in p) binary += a.toByteArray()
    }
  }
  return binary
}

internal fun getNodeBinaryBlobFormat(properties: List<ByteArray>): ByteArray {
  var binary: ByteArray = byteArrayOf()
  properties.forEach { binary += it }
  return binary
}

internal fun getEdgeBinaryBlobFormat(
    source: Long,
    target: Long,
    properties: List<ByteArray>
): ByteArray {
  var binary: ByteArray = byteArrayOf()
  binary += source.to8ByteArrayInLittleEndian()
  binary += target.to8ByteArrayInLittleEndian()
  properties.forEach { binary += it }
  return binary
}
