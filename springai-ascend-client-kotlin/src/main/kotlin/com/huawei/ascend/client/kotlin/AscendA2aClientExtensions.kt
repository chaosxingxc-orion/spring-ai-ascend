package com.huawei.ascend.client.kotlin

import com.huawei.ascend.client.A2aResponse
import com.huawei.ascend.client.AscendA2aClient
import com.huawei.ascend.client.SendSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.a2aproject.sdk.spec.StreamingEventKind

/**
 * [AscendA2aClient.sendText] as a suspending call: the blocking exchange runs
 * on [Dispatchers.IO] via [runInterruptible], so cancelling the coroutine
 * interrupts the worker thread and the call ends with a
 * [kotlinx.coroutines.CancellationException] instead of blocking on.
 */
suspend fun AscendA2aClient.sendTextSuspending(spec: SendSpec): A2aResponse =
    runInterruptible(Dispatchers.IO) { sendText(spec) }

/**
 * [AscendA2aClient.streamText] as a suspending call; [listener] observes
 * every event as it arrives, on the IO worker thread. Cancelling the
 * coroutine interrupts the client's blocking wait for the turn-ending event
 * (the Java client honors the interrupt and rethrows it), surfacing as a
 * [kotlinx.coroutines.CancellationException].
 */
suspend fun AscendA2aClient.streamTextSuspending(
    spec: SendSpec,
    listener: (StreamingEventKind) -> Unit = {},
): A2aResponse = runInterruptible(Dispatchers.IO) { streamText(spec) { listener(it) } }
