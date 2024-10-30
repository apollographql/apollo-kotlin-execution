# Apollo Usage Reporting

Apollo Kotlin Execution supports sending [operation and field reports](https://www.apollographql.com/docs/graphos/platform/insights/sending-operation-metrics) to GraphOS.

> If you have a federated graph, see [ftv1](federation.md#tracing-ftv1) instead for how to send tracing information to the router.
{style=note}

## Sending usage reports

Operation and field reports are sent using a protobuf protocol to `https://usage-reporting.api.apollographql.com/api/ingress/traces`.

Apollo Kotlin collects, encodes and sends those reports using the `apollo-execution-reporting` artifact:

```kotlin
dependencies {
  // Add the Apollo monograph artifact
  implementation("com.apollographql.execution:apollo-execution-reporting:%latest_version%")
}
```

To enable reports, configure your `ExecutableSchema` with a `ApolloReportsInstrumentation` and matching `ApolloReportsContext`:

```kotlin
// Get your API key from the environment or secret manager
val apolloKey = System.getenv("APOLLO_KEY")

// Install the ApolloReportsInstrumentation in the executable schema
val schema = ServiceExecutableSchemaBuilder()
    .addInstrumentation(ApolloReportsInstrumentation(apolloKey))
    .build()

// Create a new ApolloReportsContext() for each operation and use it through execution
val reportsContext = ApolloReportsContext()
val response = schema.execute(request, reportsContext)

// The reports are sent in the background
```
