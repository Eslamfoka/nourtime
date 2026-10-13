package com.nourtime.app.remote.parent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Something the parent's phone reads from the server: still loading, couldn't be read, or there. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

fun <T> Load<T>.valueOrNull(): T? = (this as? Load.Ready)?.value

/**
 * Follows [source] as a [Load]: loading until the first value, failed on an error (instead of looking
 * like an empty result), and started again each time [retries] emits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> loadWithRetry(retries: Flow<*>, source: () -> Flow<T>): Flow<Load<T>> = retries.flatMapLatest {
    source()
        .map<T, Load<T>> { Load.Ready(it) }
        .onStart { emit(Load.Loading) }
        .catch { emit(Load.Failed) }
}
