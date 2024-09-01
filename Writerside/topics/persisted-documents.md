# Persisted documents 

## Automatic persisted documents  

Apollo Kotlin Execution supports [Automatic Persisted Queries](https://www.apollographql.com/docs/apollo-server/performance/apq/).

To enable them, call `persistedDocumentCache()`:

```kotlin
val executableSchema = ServiceExecutableSchemaBuilder()
            .persistedDocumentCache(InMemoryPersistedDocumentCache())
            .build()
```