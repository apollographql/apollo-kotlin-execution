package com.apollographql.execution

import com.apollographql.apollo.ast.GQLDocument
import com.apollographql.apollo.ast.Issue

class PersistedDocument(
    val document: GQLDocument?,
    val issues: List<Issue>
)

interface PersistedDocumentCache {
    fun get(id: String): PersistedDocument?
    fun put(id: String, persistedDocument: PersistedDocument)
}