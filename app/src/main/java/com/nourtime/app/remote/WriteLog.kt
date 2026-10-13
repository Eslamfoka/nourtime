package com.nourtime.app.remote

import android.util.Log
import com.google.android.gms.tasks.Task

/**
 * For writes that aren't awaited (offline they wait in Firestore's queue): a refusal from the server
 * (rules, a missing document) is logged instead of disappearing, so stale data on the parent's phone
 * can be traced. Returns the same task.
 */
fun <T> Task<T>.logIfFailed(tag: String, what: String): Task<T> =
    addOnFailureListener { Log.w(tag, "$what: write failed", it) }
