package com.uade.robotcierre.contract

import java.lang.reflect.Modifier

/**
 * Codificador JSON mínimo, solo para los tests: recorre por reflexión los campos de las data classes
 * reales de la app (en el orden en que están declarados), así el JSON del contrato sale de los
 * modelos y no de una copia escrita a mano. La app no serializa JSON: esto documenta el contrato.
 */
object ContractJson {

    fun encode(value: Any?, indent: Int = 0): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean, is Int, is Long, is Double -> value.toString()
        is Enum<*> -> quote(value.name)
        is Map<*, *> -> obj(value.entries.map { quote(keyName(it.key)) to it.value }, indent)
        is List<*> -> "[" + value.joinToString(", ") { encode(it, indent) } + "]"
        else -> obj(fieldsOf(value).map { (name, v) -> quote(name) to v }, indent)
    }

    private fun keyName(key: Any?): String = if (key is Enum<*>) key.name else key.toString()

    private fun fieldsOf(value: Any): List<Pair<String, Any?>> =
        value.javaClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { field ->
                field.isAccessible = true
                field.name to field.get(value)
            }

    private fun obj(entries: List<Pair<String, Any?>>, indent: Int): String {
        if (entries.isEmpty()) return "{}"
        val pad = "  ".repeat(indent + 1)
        val body = entries.joinToString(",\n") { (k, v) -> "$pad$k: ${encode(v, indent + 1)}" }
        return "{\n$body\n${"  ".repeat(indent)}}"
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
