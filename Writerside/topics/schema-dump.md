# Monitoring the generated schema

When working with Kotlin code, it's easy to introduce unwanted changes to your GraphQL schema. Not every Kotlin construct needs to be part of your exported GraphQL API.

For this reason, Apollo Kotlin Execution comes with built-in monitoring of your GraphQL schema. The Gradle plugin adds an `apolloCheckSchema` task that is run automatically whenever you run `./gradlew check` (and therefore `./gradlew build`).

For an example, if you were to add a `exposedByMistake()` function, running `./gradlew apolloCheckSchema` would fail with the below:

```
$ ./gradlew apolloCheckSchema
> Task :apolloCheckSchema FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':apolloCheckSchema'.
> Apollo schema check failed.
  --- /Users/mbonnin/git/apollo-kotlin-execution/sample-ktor/graphql/schema.graphqls
  +++ /Users/mbonnin/git/apollo-kotlin-execution/sample-ktor/build/generated/ksp/main/resources/serviceSchema.graphqls
  @@ -7,5 +7,7 @@
      Greeting for name
     """
     hello(name: String!): String!
  +
  +  exposedByMistake: String!
   }
   
  
  Run 'apolloDumpSchema' to overwrite the schema.
```

If the change is unwanted, then you can fix your code to hide it.

If the change is in fact desired, you can run `./gradlew apolloDumpSchema` and commit the resulted file to make the check pass:

```
$ ./gradlew apolloDumpSchema        
BUILD SUCCESSFUL in 522ms
$ git diff graphql/
diff --git a/sample-ktor/graphql/schema.graphqls b/sample-ktor/graphql/schema.graphqls
index 6f4c510..b7f7311 100644
--- a/sample-ktor/graphql/schema.graphqls
+++ b/sample-ktor/graphql/schema.graphqls
@@ -7,4 +7,6 @@ type Query {
    Greeting for name
   """
   hello(name: String!): String!
+
+  newField: String!
 }
$ git commit -a -m 'Update GraphQL schema'
$ ./gradlew apolloCheckSchema        
BUILD SUCCESSFUL in 324ms
```

