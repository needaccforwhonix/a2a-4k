// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.AgentCard
import io.github.a2a_4k.models.CancelTaskRequest
import io.github.a2a_4k.models.CancelTaskResponse
import io.github.a2a_4k.models.GetTaskPushNotificationRequest
import io.github.a2a_4k.models.GetTaskPushNotificationResponse
import io.github.a2a_4k.models.GetTaskRequest
import io.github.a2a_4k.models.GetTaskResponse
import io.github.a2a_4k.models.JsonRpcRequest
import io.github.a2a_4k.models.Message
import io.github.a2a_4k.models.PushNotificationConfig
import io.github.a2a_4k.models.SendTaskRequest
import io.github.a2a_4k.models.SendTaskResponse
import io.github.a2a_4k.models.SetTaskPushNotificationRequest
import io.github.a2a_4k.models.SetTaskPushNotificationResponse
import io.github.a2a_4k.models.StringValue
import io.github.a2a_4k.models.TaskIdParams
import io.github.a2a_4k.models.TaskPushNotificationConfig
import io.github.a2a_4k.models.TaskQueryParams
import io.github.a2a_4k.models.TaskSendParams
import io.github.a2a_4k.models.TaskState
import io.github.a2a_4k.models.TextPart
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class A2AServerTest {
    private val host = "localhost"
    private val port = 8080
    private val endpoint = "/api"
    private val baseUrl = "http://$host:$port"
    private val apiUrl = "$baseUrl$endpoint"
    private val agentCardUrl = "$baseUrl/.well-known/agent.json"

    private lateinit var taskManager: BasicTaskManager
    private lateinit var server: A2AServer
    private lateinit var client: HttpClient

    private val json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @BeforeEach
    fun setup() {
        taskManager = BasicTaskManager(NoopTaskHandler())
        server = A2AServer(
            host = host,
            port = port,
            endpoint = endpoint,
            agentCard = agentCard,
            taskManager = taskManager,
        )

        // Start server
        server.start(wait = false)

        // Create HTTP client
        client = HttpClient(CIO) {
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
            }
            install(SSE)
        }

        // Wait for server to start
        Thread.sleep(1000)
    }

    @AfterEach
    fun teardown() {
        client.close()
        server.stop()
    }

    @Test
    fun `test GetAgentCard`(): Unit = runBlocking {
        // When
        val response = client.get(agentCardUrl)

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseAgentCard = json.decodeFromString<AgentCard>(responseBody)

        assertEquals(agentCard.name, responseAgentCard.name)
        assertEquals(agentCard.description, responseAgentCard.description)
        assertEquals(agentCard.version, responseAgentCard.version)
    }

    @Test
    fun `test GetTaskRequest for non-existent task`(): Unit = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = StringValue("req-123")
        val request = GetTaskRequest(
            id = requestId,
            params = TaskQueryParams(id = taskId, historyLength = 10),
        )

        // When
        val response = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val getTaskResponse = json.decodeFromString<GetTaskResponse>(responseBody)

        assertEquals(requestId, getTaskResponse.id)
        assertNull(getTaskResponse.result)
        assertNotNull(getTaskResponse.error)
        assertEquals(-32001, getTaskResponse.error?.code)
    }

    @Test
    fun `test SendTaskRequest and GetTaskRequest`(): Unit = runBlocking {
        // Given
        val taskId = "test-job-123"
        val sessionId = "session-123"
        val requestId = StringValue("req-456")
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))

        // Create task
        val sendRequest = SendTaskRequest(
            id = requestId,
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )

        // When - Send task
        val sendResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), sendRequest))
        }

        // Then - Send task response
        assertEquals(HttpStatusCode.OK, sendResponse.status)
        val sendResponseBody = sendResponse.bodyAsText()
        val sendTaskResponse = json.decodeFromString<SendTaskResponse>(sendResponseBody)

        assertEquals(requestId, sendTaskResponse.id)
        assertNotNull(sendTaskResponse.result)
        assertNull(sendTaskResponse.error)
        assertEquals(taskId, sendTaskResponse.result?.id)
        assertEquals(sessionId, sendTaskResponse.result?.sessionId)
        assertEquals(TaskState.SUBMITTED, sendTaskResponse.result?.status?.state)
        assertEquals(1, sendTaskResponse.result?.history?.size)

        // When - Get task
        val getRequest = GetTaskRequest(
            id = StringValue("get-req-123"),
            params = TaskQueryParams(id = taskId, historyLength = 10),
        )

        val getResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), getRequest))
        }

        // Then - Get task response
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val getResponseBody = getResponse.bodyAsText()
        val getTaskResponse = json.decodeFromString<GetTaskResponse>(getResponseBody)

        assertEquals(StringValue("get-req-123"), getTaskResponse.id)
        assertNotNull(getTaskResponse.result)
        assertNull(getTaskResponse.error)
        assertEquals(taskId, getTaskResponse.result?.id)
        assertEquals(sessionId, getTaskResponse.result?.sessionId)
        assertEquals(TaskState.SUBMITTED, getTaskResponse.result?.status?.state)
        assertEquals(1, getTaskResponse.result?.history?.size)
    }

    @Test
    fun `test CancelTaskRequest for non-existent task`(): Unit = runBlocking {
        // Given
        val taskId = "non-existent-task"
        val requestId = StringValue("req-cancel")
        val request = CancelTaskRequest(
            id = requestId,
            params = TaskIdParams(id = taskId),
        )

        // When
        val response = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val cancelTaskResponse = json.decodeFromString<CancelTaskResponse>(responseBody)

        assertEquals(requestId, cancelTaskResponse.id)
        assertNull(cancelTaskResponse.result)
        assertNotNull(cancelTaskResponse.error)
        assertEquals(-32001, cancelTaskResponse.error?.code)
    }

    @Test
    fun `test SetTaskPushNotification and GetTaskPushNotification`(): Unit = runBlocking {
        // Given
        val taskId = "push-job-123"
        val sessionId = "session-push"
        val requestId = StringValue("req-push")
        val textPart = TextPart(text = "Hello", metadata = emptyMap())
        val message = Message(role = "user", parts = listOf(textPart))

        // Create task
        val sendRequest = SendTaskRequest(
            id = StringValue("send-req-123"),
            params = TaskSendParams(
                id = taskId,
                sessionId = sessionId,
                message = message,
                historyLength = 10,
            ),
        )

        client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), sendRequest))
        }

        // Set push notification
        val pushConfig = PushNotificationConfig(url = "https://example.com/push")
        val setPushRequest = SetTaskPushNotificationRequest(
            id = requestId,
            params = TaskPushNotificationConfig(id = taskId, pushNotificationConfig = pushConfig),
        )

        // When - Set push notification
        val setPushResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), setPushRequest))
        }

        // Then - Set push notification response
        assertEquals(HttpStatusCode.OK, setPushResponse.status)
        val setPushResponseBody = setPushResponse.bodyAsText()
        val setTaskPushResponse = json.decodeFromString<SetTaskPushNotificationResponse>(setPushResponseBody)

        assertEquals(requestId, setTaskPushResponse.id)
        assertNotNull(setTaskPushResponse.result)
        assertNull(setTaskPushResponse.error)
        assertEquals(taskId, setTaskPushResponse.result?.id)
        assertEquals(pushConfig.url, setTaskPushResponse.result?.pushNotificationConfig?.url)

        // When - Get push notification
        val getPushRequest = GetTaskPushNotificationRequest(
            id = StringValue("get-push-req-123"),
            params = TaskIdParams(id = taskId),
        )

        val getPushResponse = client.post(apiUrl) {
            setBody(json.encodeToString(JsonRpcRequest.serializer(), getPushRequest))
        }

        // Then - Get push notification response
        assertEquals(HttpStatusCode.OK, getPushResponse.status)
        val getPushResponseBody = getPushResponse.bodyAsText()
        val getTaskPushResponse = json.decodeFromString<GetTaskPushNotificationResponse>(getPushResponseBody)

        assertEquals(StringValue("get-push-req-123"), getTaskPushResponse.id)
        assertNotNull(getTaskPushResponse.result)
        assertNull(getTaskPushResponse.error)
        assertEquals(taskId, getTaskPushResponse.result?.id)
        assertEquals(pushConfig.url, getTaskPushResponse.result?.pushNotificationConfig?.url)
    }
}
