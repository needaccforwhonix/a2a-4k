// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import dev.langchain4j.model.openai.OpenAiChatModel
import io.github.a2a_4k.models.AgentCard
import io.github.a2a_4k.models.Capabilities
import io.github.a2a_4k.models.Message
import io.github.a2a_4k.models.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Example using LangChain4j.
 */
fun main() = runBlocking {
    // Call LangChain4j to answer the message.
    val callLangChain4j = { message: Message ->
        val model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4o-mini")
            .build()
        model.chat(message.content())
    }

// Create your TaskHandler
    val taskHandler = BasicTaskHandler(callLangChain4j)

// Create a TaskManager with your TaskHandler
    val taskManager = BasicTaskManager(taskHandler)

// Define your Agent's capabilities
    val capabilities = Capabilities()

// Create an AgentCard for your Agent
    val agentCard = AgentCard(
        name = "My Agent",
        description = "A custom A2A Agent",
        url = "https://example.com",
        version = "1.0.0",
        capabilities = capabilities,
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = listOf(),
    )

    // Create and start the A2A Server
    val server = A2AServer(
        host = "0.0.0.0",
        port = 5001,
        endpoint = "/",
        agentCard = agentCard,
        taskManager = taskManager,
    )

    // Start the server
    server.start(wait = false)

    // Wait for the server to start
    delay(1_000)

    val client = A2AClient(baseUrl = "http://localhost:5001")

    try {
        // Get the Agent's metadata
        val agentCard = client.getAgentCard()
        println("Connected to Agent: ${agentCard.name}")

        // Create a message to send to the Agent
        val message = "Hello, Agent!".toUserMessage()

        // Send a task to the Agent
        val response = client.sendTask(taskId = "my-job-123", sessionId = "session-456", message = message)

        // Process the response
        if (response.result != null) {
            println("Agent response: $response")
        } else {
            println("Error: ${response.error?.message}")
        }
    } finally {
        // Close the client
        client.close()
    }
}
