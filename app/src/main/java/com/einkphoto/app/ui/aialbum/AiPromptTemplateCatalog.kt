package com.einkphoto.app.ui.aialbum

import android.content.Context

/**
 * App-bundled creative prompts. The source stays as Markdown so it remains
 * easy to curate by hand; parsing occurs locally and never reaches the model
 * until the user edits and explicitly requests generation.
 */
internal enum class AiPromptTemplateCategory(val title: String, private val heading: String) {
    Landscape("风景", "风景模板"),
    People("人物", "人物模板"),
    Animals("动物", "动物模板"),
    Artwork("绘画作品", "绘画作品模板");

    companion object {
        fun fromHeading(value: String): AiPromptTemplateCategory? = entries.firstOrNull { it.heading == value }
    }
}

internal data class AiPromptTemplate(
    val id: String,
    val category: AiPromptTemplateCategory,
    val text: String,
)

internal object AiPromptTemplateCatalog {
    private const val assetName = "ai_prompt_templates.md"

    fun load(context: Context): List<AiPromptTemplate> = runCatching {
        context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
            val items = mutableListOf<AiPromptTemplate>()
            var category: AiPromptTemplateCategory? = null
            var id: String? = null
            val lines = mutableListOf<String>()

            fun flush() {
                val activeCategory = category
                val activeId = id
                if (activeCategory != null && !activeId.isNullOrBlank() && lines.isNotEmpty()) {
                    items += AiPromptTemplate(activeId, activeCategory, lines.joinToString("\n"))
                }
                id = null
                lines.clear()
            }

            reader.forEachLine { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("## ") -> {
                        flush()
                        category = AiPromptTemplateCategory.fromHeading(line.removePrefix("## ").trim())
                    }
                    line.startsWith("### ") -> {
                        flush()
                        id = line.removePrefix("### ").trim()
                    }
                    id != null && line.isNotBlank() -> lines += line
                }
            }
            flush()
            items
        }
    }.getOrDefault(emptyList())

    fun choose(
        templates: List<AiPromptTemplate>,
        category: AiPromptTemplateCategory,
        previousId: String?,
    ): AiPromptTemplate? {
        val candidates = templates.filter { it.category == category }
        val alternatives = candidates.filterNot { it.id == previousId }
        return (alternatives.ifEmpty { candidates }).randomOrNull()
    }
}
