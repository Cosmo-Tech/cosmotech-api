package com.cosmotech.solution.azure

import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentProperty
import org.springframework.data.redis.core.PartialUpdate
import org.springframework.data.redis.core.RedisKeyValueAdapter
import org.springframework.data.redis.core.RedisKeyValueTemplate
import org.springframework.data.redis.core.convert.RedisData
import org.springframework.data.redis.core.mapping.RedisMappingContext
import org.springframework.data.redis.core.mapping.RedisPersistentEntity

class CosmoKeyValueTemplate(private val adapter: RedisKeyValueAdapter, mappingContext: RedisMappingContext?) :
    RedisKeyValueTemplate(adapter, mappingContext!!) {


    override fun <T : Any> insert(id: Any, objectToInsert: T): T {
        if (objectToInsert is PartialUpdate<*>) {
            doPartialUpdate(objectToInsert as PartialUpdate<*>)
            return objectToInsert
        }
        if (objectToInsert !is RedisData) {
            val converter = adapter.converter
            val entity: RedisPersistentEntity<*> = converter.mappingContext
                .getRequiredPersistentEntity(objectToInsert.javaClass)
            val idProperty: KeyValuePersistentProperty<*> = entity.requiredIdProperty
            val propertyAccessor = entity.getPropertyAccessor(objectToInsert)
            if (propertyAccessor.getProperty(idProperty) == null) {
                propertyAccessor.setProperty(idProperty, id)
                return super.insert(id, propertyAccessor.bean)
            }
        }
        return insert(id, objectToInsert)
    }

}