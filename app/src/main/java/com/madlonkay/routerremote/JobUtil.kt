package com.madlonkay.routerremote

import android.support.v4.widget.SwipeRefreshLayout
import android.view.View
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.NonCancellable
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor

// From https://github.com/Kotlin/kotlinx.coroutines/blob/master/ui/coroutines-guide-ui.md#android

interface JobHolder {
    val job: Job
}

val View.contextJob: Job
    get() = (context as? JobHolder)?.job ?: NonCancellable

fun View.onClick(action: suspend (View) -> Unit) {
    // launch one actor as a parent of the context job
    val eventActor = actor<Unit>(contextJob + UI, capacity = Channel.CONFLATED) {
        for (event in channel) action(this@onClick)
    }
    // install a listener to activate this actor
    setOnClickListener {
        eventActor.offer(Unit)
    }
}

fun SwipeRefreshLayout.onRefresh(action: suspend (SwipeRefreshLayout) -> Unit) {
    // launch one actor as a parent of the context job
    val eventActor = actor<Unit>(contextJob + UI, capacity = Channel.CONFLATED) {
        for (event in channel) action(this@onRefresh)
    }
    // install a listener to activate this actor
    setOnRefreshListener {
        eventActor.offer(Unit)
    }
}