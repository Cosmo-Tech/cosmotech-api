//package com.cosmotech.solution.azure
//
//import com.redis.om.spring.metamodel.MetamodelField
//import com.redis.om.spring.ops.RedisModulesOperations
//import com.redis.om.spring.repository.RedisDocumentRepository
//import com.redislabs.modules.rejson.Path
//import io.redisearch.Schema
//import io.redisearch.client.Client
//import io.redisearch.client.IndexDefinition
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.data.domain.Page
//import org.springframework.data.domain.PageImpl
//import org.springframework.data.domain.Pageable
//import org.springframework.data.keyvalue.core.KeyValueOperations
//import org.springframework.data.keyvalue.repository.support.SimpleKeyValueRepository
//import org.springframework.data.redis.core.RedisTemplate
//import org.springframework.data.repository.core.EntityInformation
//import org.springframework.util.Assert
//import java.util.function.Function
//import java.util.stream.Collectors
//import java.util.stream.StreamSupport
//
//abstract class CosmoRedisDocumentRepository<T, ID>(
//    metadata: EntityInformation<T, ID>,
//    operations: KeyValueOperations,
//    @Qualifier("redisModulesOperations") rmo: RedisModulesOperations<String>
//) : SimpleKeyValueRepository<T, ID>(metadata, operations), RedisDocumentRepository<T, ID> {
//
//    private var modulesOperations: RedisModulesOperations<String>
//    private var metadata: EntityInformation<T, ID>
//    private var operations: KeyValueOperations
//    private val entityInformation: EntityInformation<T, ID>? = null
//
//    init{
//        modulesOperations = rmo
//        this.metadata = metadata
//        this.operations = operations
//    }
//
//    // -------------------------------------------------------------------------
//    // Methods from CrudRepository
//    // -------------------------------------------------------------------------
//
//
//    private val MASTER_NAME = "mymaster"
//    private var sentinels: Set<String>? = HashSet()
//
//    var client: Client = Client("testung", MASTER_NAME, sentinels)
//
//    override fun <S : T> save(entity: S): S{
//
//        Assert.notNull(entity, "Entity must not be null!")
//
//        client.createIndex(Schema().addTextField("title",5.0)
//            .addTextField("body", 1.0)
//            .addNumericField("price"),
//            Client.IndexOptions
//                .defaultOptions()
//                .setDefinition(IndexDefinition()
//                    .setPrefixes(String[].{"item:", "product:"})
//                    .setFilter("@price>100")))
//
//
//        if(entityInformation!!.isNew(entity)){
//            return operations.insert(entity)
//        }
//
//        return operations.update(entityInformation.getRequiredId(entity), entity)
//    }
//
//    override fun getIds() : Iterable<ID>{
//      val template = RedisTemplate<String, ID>()
//      val setOps = template.opsForSet()
//      return ArrayList<ID>(setOps.members(metadata.javaType.name)!!)
//    }
//
//    override fun getIds(pageable: Pageable) : Page<ID> {
//        val template = RedisTemplate<String, ID>()
//        val setOps = template.opsForSet()
//        val ids : List<ID> = ArrayList<ID>(setOps.members(metadata.javaType.name)!!)
//
//        val fromIndex : Int = pageable.offset.toInt()
//        val toIndex : Int = pageable.pageSize
//
//        return PageImpl<ID>(ids.subList(fromIndex, toIndex) , pageable, ids.size.toLong())
//    }
//
//    override fun deleteById(id: ID, path: Path?) {
//        val deletedCount = modulesOperations.opsForJSON().del(metadata.javaType.name + ":" + id.toString(), path)
//
//        if((deletedCount > 0) && path!! == Path.ROOT_PATH){
//            val template = modulesOperations.template as RedisTemplate<String, ID>
//            val setOps = template.opsForSet()
//            setOps.remove(metadata.javaType.name, id)
//        }
//    }
//
//    override fun updateField(entity: T, field: MetamodelField<T, *>?, value: Any?) {
//        modulesOperations.opsForJSON().set(metadata.javaType.name + ":" + metadata.getId(entity).toString(), value, Path.of(("$." + field!!.field.name)))
//    }
//
//    override fun <F> getFieldsByIds(ids: Iterable<ID>, field: MetamodelField<T, F>): Iterable<F> {
//        val keys = StreamSupport.stream(ids.spliterator(), false).map { id: ID ->
//            metadata.javaType.name + ":" + id
//        }.toArray()
//        return modulesOperations.opsForJSON().mget(Path.of("$." + field.field.name),
//        MutableList::class.java, keys.toString()).stream().flatMap<Any>(Function { obj: List<*> -> obj.stream() }).collect(
//            Collectors.toList<Any>()) as Iterable<F>
//    }
//}
