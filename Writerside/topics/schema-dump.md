# Monitoring the generated schema

You can dump the generated schema with `./gradlew apolloDumpSchema`.

It is recommended to check this file in source control so that changes to the Kotlin source can be reviewed and no unintentional change is published.

By default, GraphQL schema is dumped in `graphql/schema.graphqls`.

The Apollo Kotlin Execution Gradle plugin also adds a `apolloCheckSchema` task to the `check` lifecycle task. Any time there is a change in the schema, `apolloCheckSchema` fails and you can pull the latest changes with an explicit `./gradlew apolloDumpSchema`.