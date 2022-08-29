//package com.redis.om.spring.repository.support
//
//import com.redis.om.spring.metamodel.MetamodelField
//import com.redis.om.spring.ops.RedisModulesOperations
//import com.redis.om.spring.repository.RedisDocumentRepository
//import com.redislabs.modules.rejson.Path
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.data.domain.Page
//import org.springframework.data.domain.PageImpl
//import org.springframework.data.domain.Pageable
//import org.springframework.data.keyvalue.core.KeyValueAdapter
//import org.springframework.data.keyvalue.core.KeyValueCallback
//import org.springframework.data.keyvalue.core.KeyValueOperations
//import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentEntity
//import org.springframework.data.keyvalue.core.mapping.KeyValuePersistentProperty
//import org.springframework.data.keyvalue.repository.support.SimpleKeyValueRepository
//import org.springframework.data.redis.core.PartialUpdate
//import org.springframework.data.redis.core.RedisKeyValueAdapter
//import org.springframework.data.redis.core.RedisKeyValueTemplate.RedisKeyValueCallback
//import org.springframework.data.redis.core.RedisTemplate
//import org.springframework.data.redis.core.convert.RedisConverter
//import org.springframework.data.redis.core.convert.RedisData
//import org.springframework.data.redis.core.mapping.RedisMappingContext
//import org.springframework.data.redis.core.mapping.RedisPersistentEntity
//import org.springframework.data.repository.core.EntityInformation
//import org.springframework.util.Assert
//import org.springframework.util.ClassUtils
//import java.util.function.Function
//import java.util.stream.Collectors
//import java.util.stream.StreamSupport
//
//class SimpleRedisDocumentRepository<T, ID>(
//    metadata: EntityInformation<T, ID>,
//    operations: KeyValueOperations,
//    @Qualifier("redisModulesOperations") rmo: RedisModulesOperations<*>
//) :
//    SimpleKeyValueRepository<T, ID>(metadata, operations), RedisDocumentRepository<T, ID> {
//    protected var modulesOperations: RedisModulesOperations<String>
//    protected var metadata: EntityInformation<T, ID>
//    protected var operations: KeyValueOperations
//
//    override fun <S : T?> save(entity: S): S {
//        Assert.notNull(entity, "Entity must not be null!")
//        return if (entityInformation.isNew(entity)) {
//            operations.insert(entity)
//        } else operations.update(entityInformation.getRequiredId(entity), entity)
//    }
//
//    public fun <S : T> save(entity: S) {
//
//        Assert.notNull(entity, "Entity must not be null!");
//
//        if (entityInformation.isNew(entity)) {
//            return operations.insert(entity);
//        }
//
//        return operations.update(entityInformation.getRequiredId(entity), entity);
//    }
//
//
//    init {
//        modulesOperations = rmo as RedisModulesOperations<String>
//        this.metadata = metadata
//        this.operations = operations
//    }
//
//    override fun getIds(): Iterable<ID> {
//        val template = modulesOperations.template as RedisTemplate<String, ID>
//        val setOps = template.opsForSet()
//        return ArrayList(setOps.members(metadata.javaType.name))
//    }
//
//    override fun getIds(pageable: Pageable): Page<ID> {
//        val template = modulesOperations.template as RedisTemplate<String, ID>
//        val setOps = template.opsForSet()
//        val ids: List<ID> = ArrayList(setOps.members(metadata.javaType.name))
//        val fromIndex = java.lang.Long.valueOf(pageable.offset).toInt()
//        val toIndex = fromIndex + pageable.pageSize
//        return PageImpl(ids.subList(fromIndex, toIndex), pageable, ids.size.toLong())
//    }
//
//    override fun deleteById(id: ID, path: Path) {
//        val deletedCount = modulesOperations.opsForJSON().del(metadata.javaType.name + ":" + id.toString(), path)
//        if (deletedCount > 0 && path == Path.ROOT_PATH) {
//            val template = modulesOperations.template as RedisTemplate<String, ID>
//            val setOps = template.opsForSet()
//            setOps.remove(metadata.javaType.name, id)
//        }
//    }
//
//    override fun updateField(entity: T, field: MetamodelField<T, *>, value: Any) {
//        modulesOperations.opsForJSON()[metadata.javaType.name + ":" + metadata.getId(entity).toString(), value] =
//            Path.of("$." + field.field.name)
//    }
//
//    override fun <F> getFieldsByIds(ids: Iterable<ID>, field: MetamodelField<T, F>): Iterable<F> {
//        val keys = StreamSupport.stream(ids.spliterator(), false).map { id: ID ->
//            metadata.javaType.name + ":" + id
//        }.toArray<String> { _Dummy_.__Array__() }
//        return modulesOperations.opsForJSON().mget<List<*>>(
//            Path.of("$." + field.field.name),
//            MutableList::class.java, *keys
//        ).stream().flatMap<Any>(Function { obj: List<*> -> obj.stream() }).collect(
//            Collectors.toList<Any>()
//        )
//    }
//
//}