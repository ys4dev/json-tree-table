package controller

import javafx.beans.property.ReadOnlyStringWrapper
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeTableColumn
import javafx.scene.control.TreeTableView
import javafx.util.Callback
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.QueryBuilders
import java.net.InetAddress
import java.net.URL
import java.util.*

/**
 *
 */
class MainController : Initializable {

    @FXML
    private lateinit var table: TreeTableView<ESItem>

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        val client = TransportClient.builder().build()
                .addTransportAddress(InetSocketTransportAddress(InetAddress.getByName("192.168.99.100"), 9300))

        val indices = indices(client)
        println(indices)

        val columns = buildColumns(indices[0].types[0].fields)
//        val size = columnSize(columns)

        columns.forEachIndexed { index, map ->
            val column = TreeTableColumn<ESItem, String>(map[""])
            column.cellValueFactory = Callback {
                val path = it.value.value?.path
                val name = map[path]
                val value = it.value.value?.values?.get(name)?.toString()
                if (path?.length ?:0 > 0) {
                    println()
                }
                ReadOnlyStringWrapper(value)
            }
            table.columns.add(column)
        }

        val response = client.prepareSearch(indices[0].name)
                .setQuery(QueryBuilders.matchAllQuery())
                .get()
        val items = response.hits.hits.map { hit ->
            val treeItem = TreeItem(ESItem("", hit.source))
            indices[0].types[0].fields.filter { it is NestedField }.forEach { field ->
                val nested = hit.source[field.name] as List<*>
                nested.forEach { f ->
                    val friend = f as Map<String, *>
                    treeItem.children.add(TreeItem(ESItem(field.name, friend)))
                }
            }
            treeItem
        }

        val root = TreeItem<ESItem>()
        root.children.addAll(items)
        table.root = root

        table.selectionModel.selectedItemProperty().addListener { observable, oldValue, newValue ->
            newValue.value.path
            table.columns.forEachIndexed { index, tableColumn ->
                val name = columns[index][newValue.value.path]
                tableColumn.text = name ?: ""
            }
        }
    }

    fun indices(client: TransportClient): List<Index> {
        val indices = client.admin().cluster().prepareState().get().state.metaData.indices
        val idxs = indices.map {
            val types = it.value.mappings.map {
                val source = it.value.sourceAsMap
                val properties = source["properties"] as Map<*, *>
                val fields = parseProperty(properties)
                Type(it.key, fields)
            }
            Index(it.key, types)
        }
        return idxs
    }

    fun parseProperty(properties: Map<*, *>): List<FieldLike> {
        val fields = properties.map { (name_, v) ->
            val name = name_ as String
            val property = v as Map<*, *>
            val type = property["type"] as String
            println("$name -> $type")
            if (type == "nested") {
                val nestedProperties = v["properties"] as Map<*, *>
                val nested = parseProperty(nestedProperties)
                NestedField(name, nested)
            } else {
                Field(name, type)
            }
        }
        return fields
    }

    fun buildColumns(fields: List<FieldLike>): List<Map<String, String>> {
        fun run(fields: List<FieldLike>, path: List<String>): Map<String, List<String>> {
            val result = mutableMapOf<String, List<String>>()
            val columns = mutableListOf<String>()
            fields.forEach { field ->
                when (field) {
                    is Field -> columns.add(field.name)
                    is NestedField -> result.putAll(run(field.properties, path + field.name))
                }
            }
            result.put(path.joinToString("."), columns)
            return result
        }

        val map = run(fields, listOf())
        val size = columnSize(map)
        val result = mutableListOf<MutableMap<String, String>>()
        (1..size).forEach {
            result.add(mutableMapOf())
        }
        map.forEach { k, v ->
            v.forEachIndexed { index, s ->
                val m = result[index]
                m.put(k, s)
            }
        }
        return result
    }

    fun columnSize(columns: Map<String, List<String>>): Int {
        return columns.values.map { it.size }.max() ?: 0
    }
}




sealed class FieldLike {
    abstract val name: String
}

data class Field(override val name: String, val type: String) : FieldLike()

data class NestedField(override val name: String, val properties: List<FieldLike>) : FieldLike()

data class Index(val name: String, val types: List<Type>)

data class Type(val name: String, val fields: List<FieldLike>)

data class ESItem(val path: String, val values: Map<String, *>)
