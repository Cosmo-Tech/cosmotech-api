// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.common.redis

import com.google.gson.Gson
import com.redis.om.spring.convert.RedisOMCustomConversions
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * Configuration to fix "Couldn't find PersistentEntity for type class java.util.LinkedHashMap"
 * error caused by spring-data-commons 4.0.x no longer creating PersistentEntity for JDK Map types.
 *
 * Redis OM Spring's MappingRedisOMConverter.writeMap() calls writeInternal() for map entry values,
 * which then calls getRequiredPersistentEntity(LinkedHashMap.class) and fails.
 *
 * This fix patches the static converter list in RedisOMCustomConversions via reflection so that
 * customConversions.hasCustomWriteTarget(LinkedHashMap.class) returns true, causing writeToBucket()
 * to be called instead of writeInternal().
 *
 * BeanFactoryPostProcessor runs BEFORE any repository beans are instantiated, ensuring the
 * converters are registered before SimpleRedisDocumentRepository creates its internal
 * MappingRedisOMConverter. See the issue created:
 * https://github.com/redis/redis-om-spring/issues/755
 */
@Configuration
open class RedisOMMapConverterConfig {

  @Bean
  open fun redisOMMapConverterRegistrar(): BeanFactoryPostProcessor {
    return BeanFactoryPostProcessor { _: ConfigurableListableBeanFactory ->
      registerMapConverters()
    }
  }

  companion object {
    private val logger = org.slf4j.LoggerFactory.getLogger(RedisOMMapConverterConfig::class.java)

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
    private fun registerMapConverters() {
      try {
        val field = RedisOMCustomConversions::class.java.getDeclaredField("omConverters")
        field.isAccessible = true
        val converters = field.get(null) as MutableList<Any>
        converters.add(MapToBytesConverter())
        converters.add(CollectionToBytesConverter())
        logger.info("Registered Map/Collection -> byte[] converters in RedisOMCustomConversions")
      } catch (e: Exception) {
        logger.error(
            "Failed to register custom converters in RedisOMCustomConversions: ${e.message}",
            e,
        )
      }
    }
  }
}

@WritingConverter
class MapToBytesConverter : Converter<Map<*, *>, ByteArray> {
  private val gson = Gson()

  override fun convert(source: Map<*, *>): ByteArray {
    return gson.toJson(source).toByteArray(Charsets.UTF_8)
  }
}

@WritingConverter
class CollectionToBytesConverter : Converter<Collection<*>, ByteArray> {
  private val gson = Gson()

  override fun convert(source: Collection<*>): ByteArray {
    return gson.toJson(source).toByteArray(Charsets.UTF_8)
  }
}
