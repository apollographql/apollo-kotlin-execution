import com.apollographql.apollo.api.ExecutionContext

internal class CurrentSubscription(val id: String) : ExecutionContext.Element {

  override val key: ExecutionContext.Key<CurrentSubscription> = Key

  companion object Key : ExecutionContext.Key<CurrentSubscription>
}

internal fun ExecutionContext.subscriptionId(): String = get(CurrentSubscription)?.id ?: error("Apollo: not executing a subscription")
