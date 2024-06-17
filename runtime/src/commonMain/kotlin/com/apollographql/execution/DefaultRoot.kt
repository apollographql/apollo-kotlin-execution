package com.apollographql.execution

sealed interface DefaultRoot
data object DefaultQueryRoot : DefaultRoot
data object DefaultMutationRoot : DefaultRoot
data object DefaultSubscriptionRoot : DefaultRoot