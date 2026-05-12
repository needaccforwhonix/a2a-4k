// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.models

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TaskTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `test Task round-trip serialization`() {
        // Given
        val originalTask = Task(
            id = "task-12345",
            sessionId = "session-67890",
            status = TaskStatus(
                state = TaskState.WORKING,
                message = Message(
                    role = "user",
                    parts = listOf(TextPart("Test message")),
                ),
                timestamp = "2025-01-01T12:00:00Z",
            ),
            history = listOf(
                Message(
                    role = "assistant",
                    parts = listOf(TextPart("Previous message")),
                ),
            ),
            artifacts = listOf(
                Artifact(
                    name = "test-artifact",
                    description = "Test artifact data",
                    parts = listOf(TextPart("Test artifact content")),
                    metadata = mapOf("key" to "value"),
                ),
            ),
            metadata = mapOf("test" to "value", "number" to "42"),
        )

        // When
        val jsonString = json.encodeToString(Task.serializer(), originalTask)
        val deserializedTask = json.decodeFromString(Task.serializer(), jsonString)

        // Then
        assertEquals(originalTask.id, deserializedTask.id)
        assertEquals(originalTask.sessionId, deserializedTask.sessionId)
        assertEquals(originalTask.status.state, deserializedTask.status.state)
        assertEquals(originalTask.status.timestamp, deserializedTask.status.timestamp)
        assertEquals(originalTask.history?.size, deserializedTask.history?.size)
        assertEquals(originalTask.artifacts?.size, deserializedTask.artifacts?.size)
        assertEquals(originalTask.metadata, deserializedTask.metadata)
        assertEquals(originalTask, deserializedTask)
    }

    @Test
    fun `test Task minimal serialization`() {
        // Given
        val minimalTask = Task(
            id = "task-minimal",
            status = TaskStatus(
                state = TaskState.SUBMITTED,
            ),
        )

        // When
        val jsonString = json.encodeToString(Task.serializer(), minimalTask)
        val deserializedTask = json.decodeFromString(Task.serializer(), jsonString)

        // Then
        assertEquals(minimalTask.id, deserializedTask.id)
        assertEquals(minimalTask.sessionId, deserializedTask.sessionId) // Should be null
        assertEquals(minimalTask.status.state, deserializedTask.status.state)
        assertEquals(minimalTask.history, deserializedTask.history) // Should be null
        assertEquals(minimalTask.artifacts, deserializedTask.artifacts) // Should be null
        assertEquals(minimalTask.metadata, deserializedTask.metadata) // Should be empty map
        assertEquals(minimalTask, deserializedTask)
    }

    @Test
    fun `test TaskStatusUpdateEvent round-trip serialization`() {
        // Given
        val originalEvent = TaskStatusUpdateEvent(
            id = "task-update-123",
            status = TaskStatus(
                state = TaskState.COMPLETED,
                message = Message(
                    role = "assistant",
                    parts = listOf(TextPart("Task completed successfully")),
                ),
                timestamp = "2025-01-01T12:30:00Z",
            ),
            final = true,
            metadata = mapOf("completion_time" to "30s", "quality" to "high"),
        )

        // When
        val jsonString = json.encodeToString(TaskStatusUpdateEvent.serializer(), originalEvent)
        val deserializedEvent = json.decodeFromString(TaskStatusUpdateEvent.serializer(), jsonString)

        // Then
        assertEquals(originalEvent.id, deserializedEvent.id)
        assertEquals(originalEvent.status.state, deserializedEvent.status.state)
        assertEquals(originalEvent.final, deserializedEvent.final)
        assertEquals(originalEvent.metadata, deserializedEvent.metadata)
        assertEquals(originalEvent, deserializedEvent)
    }

    @Test
    fun `test TaskArtifactUpdateEvent round-trip serialization`() {
        // Given
        val originalEvent = TaskArtifactUpdateEvent(
            id = "task-artifact-123",
            artifact = Artifact(
                name = "image-artifact",
                description = "Generated image artifact",
                parts = listOf(TextPart("base64-encoded-image-data")),
                metadata = mapOf("format" to "png", "size" to "1024x768"),
            ),
            metadata = mapOf("source" to "generator", "version" to "1.0"),
        )

        // When
        val jsonString = json.encodeToString(TaskArtifactUpdateEvent.serializer(), originalEvent)
        val deserializedEvent = json.decodeFromString(TaskArtifactUpdateEvent.serializer(), jsonString)

        // Then
        assertEquals(originalEvent.id, deserializedEvent.id)
        assertEquals(originalEvent.artifact.name, deserializedEvent.artifact.name)
        assertEquals(originalEvent.artifact.parts.size, deserializedEvent.artifact.parts.size)
        assertEquals(originalEvent.artifact.metadata, deserializedEvent.artifact.metadata)
        assertEquals(originalEvent.metadata, deserializedEvent.metadata)
        assertEquals(originalEvent, deserializedEvent)
    }

    @Test
    fun `test TaskSendParams round-trip serialization`() {
        // Given
        val originalParams = TaskSendParams(
            id = "send-params-123",
            sessionId = "session-456",
            message = Message(
                role = "user",
                parts = listOf(TextPart("Please process this request")),
            ),
            historyLength = 10,
            pushNotification = PushNotificationConfig(
                url = "https://example.com/webhook",
                token = "Bearer token123",
            ),
            metadata = mapOf("priority" to "high", "source" to "api"),
        )

        // When
        val jsonString = json.encodeToString(TaskSendParams.serializer(), originalParams)
        val deserializedParams = json.decodeFromString(TaskSendParams.serializer(), jsonString)

        // Then
        assertEquals(originalParams.id, deserializedParams.id)
        assertEquals(originalParams.sessionId, deserializedParams.sessionId)
        assertEquals(originalParams.message.role, deserializedParams.message.role)
        assertEquals(originalParams.historyLength, deserializedParams.historyLength)
        assertNotNull(deserializedParams.pushNotification)
        assertEquals(originalParams.pushNotification?.url, deserializedParams.pushNotification?.url)
        assertEquals(originalParams.metadata, deserializedParams.metadata)
        assertEquals(originalParams, deserializedParams)
    }

    @Test
    fun `test JSON format stability for Task identifiers`() {
        // Given
        val task = Task(
            id = "stable-id-format-test",
            sessionId = "stable-session-id",
            status = TaskStatus(state = TaskState.WORKING),
        )

        // When
        val jsonString = json.encodeToString(Task.serializer(), task)

        // Then - Verify that identifiers are serialized as strings
        assert(jsonString.contains("\"id\":\"stable-id-format-test\"")) {
            "Task ID should be serialized as string"
        }
        assert(jsonString.contains("\"sessionId\":\"stable-session-id\"")) {
            "Session ID should be serialized as string"
        }

        // Verify canonical format consistency
        val deserializedTask = json.decodeFromString(Task.serializer(), jsonString)
        val reserializedJson = json.encodeToString(Task.serializer(), deserializedTask)
        assertEquals(jsonString, reserializedJson, "Serialization should be stable and canonical")
    }
}
