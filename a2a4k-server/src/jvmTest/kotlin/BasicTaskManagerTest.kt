// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BasicTaskManagerTest {

    private lateinit var taskManager: BasicTaskManager

    @BeforeEach
    fun setup() {
        taskManager = BasicTaskManager(NoopTaskHandler())
    }

    @Test
    fun `test onGetTask returns task not found error when task does not exist`() = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = StringValue("req-123")
        val request = GetTaskRequest(
            id = requestId,
            params = TaskQueryParams(id = taskId, historyLength = 10),
        )

        // When
        val response = taskManager.onGetTask(request)

        // Then
        assertEquals(requestId, response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32001, response.error?.code)
    }

    @Test
    fun `test onSendTask creates a new task when it does not exist`() = runBlocking {
        // Given
        val taskId = "new-job-123"
        val sessionId = "session-123"
        val requestId = StringValue("req-456")
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))
        val request = SendTaskRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )

        // When
        val response = taskManager.onSendTask(request)

        // Then
        assertEquals(requestId, response.id)
        assertNotNull(response.result)
        assertNull(response.error)
        assertEquals(taskId, response.result?.id)
        assertEquals(sessionId, response.result?.sessionId)
        assertEquals(TaskState.SUBMITTED, response.result?.status?.state)
        assertEquals(1, response.result?.history?.size)
    }

    @Test
    fun `test onSendTask updates an existing task`() = runBlocking {
        // Given
        val taskId = "existing-job-123"
        val sessionId = "session-123"
        val requestId = StringValue("req-789")

        // Create initial task
        val textPart1 = TextPart(text = "Hello", metadata = emptyMap())
        val message1 = Message(role = "user", parts = listOf(textPart1))
        val request1 = SendTaskRequest(
            id = StringValue("req-initial"),
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message1,
                historyLength = 10,
            ),
        )
        taskManager.onSendTask(request1)

        // Update task
        val textPart2 = TextPart(text = "How are you?", metadata = emptyMap())
        val message2 = Message(role = "user", parts = listOf(textPart2))
        val request2 = SendTaskRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message2,
                historyLength = 10,
            ),
        )

        // When
        val response = taskManager.onSendTask(request2)

        // Then
        assertEquals(requestId, response.id)
        assertNotNull(response.result)
        assertNull(response.error)
        assertEquals(taskId, response.result?.id)
        assertEquals(sessionId, response.result?.sessionId)
        assertEquals(TaskState.SUBMITTED, response.result?.status?.state)
        assertEquals(2, response.result?.history?.size)
    }

    @Test
    fun `test onCancelTask returns task not found error when task does not exist`() = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = StringValue("req-cancel")
        val request = CancelTaskRequest(
            id = requestId,
            params = TaskIdParams(id = taskId),
        )

        // When
        val response = taskManager.onCancelTask(request)

        // Then
        assertEquals(requestId, response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32001, response.error?.code)
    }

    @Test
    fun `test onCancelTask returns task not cancelable error for existing task`() = runBlocking {
        // Given
        val taskId = "existing-job-456"
        val sessionId = "session-456"
        val requestId = StringValue("req-cancel-existing")

        // Create task
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))
        val createRequest = SendTaskRequest(
            id = StringValue("req-create"),
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )
        taskManager.onSendTask(createRequest)

        // Try to cancel task
        val cancelRequest = CancelTaskRequest(
            id = requestId,
            params = TaskIdParams(id = taskId),
        )

        // When
        val response = taskManager.onCancelTask(cancelRequest)

        // Then
        assertEquals(requestId, response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32002, response.error?.code)
    }

    @Test
    fun `test onSetTaskPushNotification sets push notification for existing task`() = runBlocking {
        // Given
        val taskId = "existing-job-789"
        val sessionId = "session-789"
        val requestId = StringValue("req-push")

        // Create task
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))
        val createRequest = SendTaskRequest(
            id = StringValue("req-create"),
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )
        taskManager.onSendTask(createRequest)

        // Set push notification
        val pushConfig = PushNotificationConfig(url = "https://example.com/push")
        val pushRequest = SetTaskPushNotificationRequest(
            id = requestId,
            params = TaskPushNotificationConfig(id = taskId, pushNotificationConfig = pushConfig),
        )

        // When
        val response = taskManager.onSetTaskPushNotification(pushRequest)

        // Then
        assertEquals(requestId, response.id)
        assertNotNull(response.result)
        assertNull(response.error)
        assertEquals(taskId, response.result?.id)
        assertEquals(pushConfig.url, response.result?.pushNotificationConfig?.url)
    }

    @Test
    fun `test onGetTaskPushNotification returns push notification for existing task`() = runBlocking {
        // Given
        val taskId = "existing-job-push"
        val sessionId = "session-push"
        val requestId = StringValue("req-get-push")

        // Create task
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))
        val createRequest = SendTaskRequest(
            id = StringValue("req-create"),
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )
        taskManager.onSendTask(createRequest)

        // Set push notification
        val pushConfig = PushNotificationConfig(url = "https://example.com/push")
        val pushRequest = SetTaskPushNotificationRequest(
            id = StringValue("req-set-push"),
            params = TaskPushNotificationConfig(id = taskId, pushNotificationConfig = pushConfig),
        )
        taskManager.onSetTaskPushNotification(pushRequest)

        // Get push notification
        val getRequest = GetTaskPushNotificationRequest(
            id = requestId,
            params = TaskIdParams(id = taskId),
        )

        // When
        val response = taskManager.onGetTaskPushNotification(getRequest)

        // Then
        assertEquals(requestId, response.id)
        assertNotNull(response.result)
        assertNull(response.error)
        assertEquals(taskId, response.result?.id)
        assertEquals(pushConfig.url, response.result?.pushNotificationConfig?.url)
    }

    @Test
    fun `test onSendTaskSubscribe creates a new task and returns a flow`() = runBlocking {
        // Given
        val taskId = "streaming-job-123"
        val sessionId = "session-stream"
        val requestId = StringValue("req-stream")
        val textPart = TextPart(text = "Hello streaming", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))
        val request = SendTaskStreamingRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )

        // When
        val responseFlow = taskManager.onSendTaskSubscribe(request)
        val firstResponse = responseFlow.first()

        // Then
        assertEquals(requestId, firstResponse.id)
        assertNotNull(firstResponse.result)
        assertNull(firstResponse.error)
        assertEquals(taskId, (firstResponse.result as TaskStatusUpdateEvent).id)
        assertEquals(TaskState.SUBMITTED, (firstResponse.result as TaskStatusUpdateEvent).status.state)
    }

    @Test
    fun `test onResubscribeToTask returns error for non-existent task`() = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = StringValue("req-resub")
        val request = TaskResubscriptionRequest(
            id = requestId,
            params = TaskQueryParams(id = taskId, historyLength = 10),
        )

        // When
        val responseFlow = taskManager.onResubscribeToTask(request)
        val firstResponse = responseFlow.first()

        // Then
        assertEquals(requestId, firstResponse.id)
        assertNull(firstResponse.result)
        assertNotNull(firstResponse.error)
        assertEquals(-32001, firstResponse.error?.code)
    }
}
