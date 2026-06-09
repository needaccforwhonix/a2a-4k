// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.arc.serveA2A
import io.github.a2a_4k.models.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.eclipse.lmos.arc.agents.agent.Skill
import org.eclipse.lmos.arc.agents.agents

/**
 * Example using the Kotlin AI Framework Ard, see https://eclipse.dev/lmos/arc2/.
 */
fun main() = runBlocking {
    // Only required if OPENAI_API_KEY is not set as an environment variable.
    // System.setProperty("OPENAI_API_KEY", "****")

    // Create your Agent and host it in an A2A server.
    agents {
        agent {
            name = "MyAgent"
            model { "gpt-4o" }
            skills {
                listOf(
                    Skill(
                        id = "greeting",
                        name = "Greeting",
                        description = "A simple greeting skill",
                        tags = emptyList(),
                    ),
                )
            }
            prompt {
                """ You are a helpful assistant. Greet the user with "Hello, from A2A"! """
            }
        }
    }.serveA2A(wait = true, devMode = true)

    // Wait for the server to start
    delay(1_000)

    // Use the client to connect to the server
    val client = A2AClient(baseUrl = "http://localhost:5001")

    try {
        // Get the Agent's metadata
        val agentCard = client.getAgentCard()
        println("Connected to Agent: $agentCard")

        // Create a message to send to the Agent
        val message = "Hello, Agent!".toUserMessage()

        // Send a task to the Agent
        val response = client.sendTask(taskId = "job-123", sessionId = "session-456", message = message)

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
