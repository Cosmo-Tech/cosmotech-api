fun inferPropType(prop: Any): PropertyType {
    when (prop) {
        is Int -> return PropertyType.BI_LONG
        is Double -> return PropertyType.BI_DOUBLE
    }

    (prop as String).also {
        if (it == "")
            return PropertyType.BI_NULL
        if (it.lowercase() == "true" || it.lowercase() == "false")
            return PropertyType.BI_BOOL
        if (it.startsWith('[') and it.endsWith(']'))
            return PropertyType.BI_ARRAY
    }
    return PropertyType.BI_STRING
}


class TypeNodes(val type: String, val header: List<String>) {
    val headerBinaryBlobFormat: HeaderBinaryBlobFormat = HeaderBinaryBlobFormat(type, header.size.toUInt(), header)
    var size: Int = headerBinaryBlobFormat.size
    var nodesBinaryBlobFormat: List<NodeBinaryBlobFormat> = mutableListOf()

    fun addNodes(vararg nodesArg: Node) {
        nodesArg.forEach {
            if (it.properties.keys.any { s -> s !in header })
                //TODO create specific exception
                throw Exception("header mismatch, one or more properties are not in $header")
            nodesBinaryBlobFormat += it.toBinaryFormat()
            size += it.toBinaryFormat().size
        }
    }

    fun toBinary(): ByteArray {
        var ba = byteArrayOf()

        ba += headerBinaryBlobFormat.binary
        for (n in nodesBinaryBlobFormat)
            ba += n.binary

        return ba
    }
}

class TypeEdges(val type: String, val header: List<String>) {
    val headerBinaryBlobFormat = HeaderBinaryBlobFormat(type, header.size.toUInt(), header)
    var size: Int = headerBinaryBlobFormat.size
    var edgesBinaryBlobFormat: List<EdgeBinaryBlobFormat> = mutableListOf()

    fun addEdges(vararg edgeArg: Edge) {
        edgeArg.forEach {
            if (it.properties.keys.any { s -> s !in header })
                //TODO create specific exception
                throw Exception("header mismatch, one or more properties are not in $header")
            edgesBinaryBlobFormat += it.toBinaryFormat()
            size += it.toBinaryFormat().size
        }
    }

    fun toBinary(): ByteArray {
        var ba = byteArrayOf()

        ba += headerBinaryBlobFormat.binary
        for (e in edgesBinaryBlobFormat)
            ba += e.binary

        return ba
    }
}

class Node(val properties: Map<String, Any>) {
    val id: String = (properties["id"] ?: properties.entries.iterator().next().value) as String

    private fun getPropertiesBinary(): List<PropertyBinaryBlobFormat> {
        return properties.values.map {
            PropertyBinaryBlobFormat(inferPropType(it), it)
        }
    }

    fun toBinaryFormat(): NodeBinaryBlobFormat {
        return NodeBinaryBlobFormat(getPropertiesBinary())
    }

    fun size(): Int {
        return toBinaryFormat().size
    }
}

class Edge(
    private val source: Long,
    private val target: Long,
    val properties: Map<String, Any>
) {
    private fun getPropertiesBinary(): List<PropertyBinaryBlobFormat> {
        return properties.values.map {
            PropertyBinaryBlobFormat(inferPropType(it), it)
        }
    }

    fun toBinaryFormat(): EdgeBinaryBlobFormat {
        return EdgeBinaryBlobFormat(source, target, getPropertiesBinary())
    }

    fun size(): Int {
        return toBinaryFormat().size
    }
}