import redis.clients.jedis.Jedis
import java.lang.Exception
import java.nio.charset.Charset


class QueryBuffer(val jedis: Jedis, val graphName: String, val max_query_size: Int = 512) {
    private var first: Boolean = true

    val tasks:MutableList<BulkQuery> = mutableListOf()
    // map of redis node creation id to node id
    private var nodesCreationId = mapOf<String, Long>()

    private var currentBulkQueryBuilder: BulkQuery.Builder = BulkQuery.Builder().graphName(graphName).first()
    private var currentType: String? = null


    fun addNodes(type: String, rows: Map<String, Any>) {
        var addedSize = 0
        var typeNodes: TypeNodes? = null

        // create a new TypeNodes (for binary header)
        if (currentType != type) {
            currentType = type
            typeNodes = TypeNodes(type, rows.keys.toList())
            addedSize += typeNodes.size
        }
        // create the node
        val node = Node(rows)
        addedSize += node.size()
        nodesCreationId += node.id to nodesCreationId.size.toLong()

        // check query size limit
        if (currentBulkQueryBuilder.size() + addedSize > max_query_size) {
            tasks.add(currentBulkQueryBuilder.build())
            currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
            typeNodes = TypeNodes(type, rows.keys.toList())
        }

        // add to bulk query builder
        if (typeNodes != null)
             currentBulkQueryBuilder.addTypeNode(type, typeNodes)
        currentBulkQueryBuilder.addNodeToTypeNode(type, node)
    }

    fun addEdges(type: String, rows: Map<String, Any>) {
        var addedSize: Int = 0
        var typeEdges: TypeEdges? = null

        //extact source and target from row
        val sourceKey = rows.keys.elementAt(0)
        val targetKey = rows.keys.elementAt(1)
        val sourceName = rows[sourceKey]
        val targetName = rows[targetKey]

        val newRows = rows.minus(sourceKey).minus(targetKey)

        //create a new edge type (for binary header)
        if (type != currentType) {
            currentType = type
            typeEdges = TypeEdges(type, newRows.keys.toList())
            addedSize += typeEdges.size
        }

        //create edge
        if (sourceName !in nodesCreationId)
            throw Exception("node $sourceName doesn't exist")
        if (targetName !in nodesCreationId)
            throw Exception("node $targetName doesn't exist")
        var edge = Edge(nodesCreationId[sourceName]!!, nodesCreationId[targetName]!!, newRows)
        addedSize += edge.size()

        //check query max size
        if (currentBulkQueryBuilder.size() + addedSize > max_query_size) {
            tasks.add(currentBulkQueryBuilder.build())
            currentBulkQueryBuilder = BulkQuery.Builder().graphName(graphName)
            typeEdges = TypeEdges(type, newRows.keys.toList())
        }

        //add to bulk query builder
        if (typeEdges != null)
            currentBulkQueryBuilder.addTypeEdge(type, typeEdges)
        currentBulkQueryBuilder.addEdgeToTypeEdge(type, edge)
    }


    fun send() {
        tasks.add(currentBulkQueryBuilder.build())

        tasks.forEach { query ->

            println(query.toString())
            val result = jedis.sendCommand(
                { "GRAPH.BULK".toByteArray() },
                *query.generate_query_args()
            )

            println((result as ByteArray).toString(Charset.defaultCharset()))
        }
    }

}