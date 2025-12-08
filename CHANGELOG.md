# Version 0.1.2

* Add graphql-ws support by @martinbonnin in https://github.com/apollographql/apollo-kotlin-execution/pull/60
* Fix SPDX license name by @martinbonnin in https://github.com/apollographql/apollo-kotlin-execution/pull/64
* Update dependencies by @martinbonnin in https://github.com/apollographql/apollo-kotlin-execution/pull/67
* Add KDoc for `@GraphQLDefault` by @martinbonnin in https://github.com/apollographql/apollo-kotlin-execution/pull/68
* Switch to gratatouille by @martinbonnin in https://github.com/apollographql/apollo-kotlin-execution/pull/69

# Version 0.1.1

* Add `Ftv1Instrumentation` and `ApolloReportingInstrumentation` for respectively federated and monograph operation and fields insights.
* Publish to GCS
* Add `apolloSubscriptionModule` for subscription support with Ktor.

# Version 0.1.0
_2024-10-23_

* Add `apollo-execution-subgraph` with minimal `@key` support
* Add support for suspending resolvers and parallel execution (#18)
* Update Ktor to version 3

# Version 0.0.3
_2024-08-30_

* Rename `@GraphQLQueryRoot` to `@GraphQLQuery`, `@GraphQLMutationRoot` to `@GraphQLMutation`, `@GraphQLSubscriptionRoot` to `@GraphQLSubscription`,
* Remove `@GraphQLCoercing`
* `@GraphQLScalar` now takes the coercing as a parameter

# Version 0.0.2
_2024-07-08_

* Update to Apollo Kotlin 4.0.0-rc.1
* Remove apollo-compiler dependency
