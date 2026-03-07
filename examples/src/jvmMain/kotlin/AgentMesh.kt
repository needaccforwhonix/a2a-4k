// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.examples

import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

/**
 * Data class representing a message exchanged between agents in the mesh.
 * Inputs and outputs between agents must explicitly and unambiguously describe
 * 'what', 'where', and 'how' the task is intended.
 */
@Serializable
data class MeshMessage(
    val senderId: String,
    val topic: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

interface MeshAgent {
    val id: String
    val roleDescription: String
    suspend fun start(mesh: MeshNetwork)
}

class MeshNetwork(val scope: CoroutineScope) {
    private val _messages = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 100)
    val messages = _messages.asSharedFlow()

    suspend fun broadcast(message: MeshMessage) {
        println("[Network] Broadcast from ${message.senderId} on topic ${message.topic}: ${message.content.take(50)}...")
        _messages.emit(message)
    }
}

abstract class AlphaEvolveAgent(
    override val id: String,
    override val roleDescription: String,
    private val apiKey: String,
) : MeshAgent {
    // using open ai model with a demo key for simulation or real one if provided
    private val model = OpenAiChatModel.builder()
        .apply {
            if (apiKey == "demo") {
                baseUrl("http://langchain4j.dev/demo/openai/v1")
                modelName("gpt-3.5-turbo")
            } else {
                modelName("gpt-4o-mini")
            }
        }
        .apiKey(apiKey)
        .build()

    override suspend fun start(mesh: MeshNetwork) {
        println("[$id] Started and listening to all messages. Role: $roleDescription")
        mesh.messages
            .filter { it.senderId != id }
            .collect { message ->
                println("[$id] Received message from ${message.senderId} on ${message.topic}")
                processMessage(message, mesh)
            }
    }

    private suspend fun shouldReact(message: MeshMessage): Boolean {
        val prompt = """
            You are $id, an AI agent with the following role: "$roleDescription"
            You received a message from ${message.senderId} on the topic "${message.topic}".
            Message content: "${message.content}"

            Based on your role, should you process and react to this message?
            Respond with only "YES" or "NO".
        """.trimIndent()

        val response = callModel(prompt)
        return response.trim().uppercase() == "YES"
    }

    private suspend fun processMessage(message: MeshMessage, mesh: MeshNetwork) {
        try {
            if (!shouldReact(message)) {
                println("[$id] Decided not to react to message from ${message.senderId} on ${message.topic}")
                return
            }

            // AlphaEvolve Algorithm: Reasoning, Critiquing, and Refining
            val context = "Context: Message from ${message.senderId} on ${message.topic}. Content: ${message.content}"

            // Step 1: Reason and generate initial draft
            val draftPrompt = """
                Analyze the following context and propose an initial response or action plan as $id.
                You are participating in an agent mesh. Your output must explicitly and unambiguously
                describe 'what', 'where', and 'how' the task is intended. Focus on prioritizing
                security, performance, style, documentation, cleanliness, and order.

                $context
            """.trimIndent()
            val draft = callModel(draftPrompt)
            println("[$id] AlphaEvolve Draft: ${draft.take(50).replace("\n", " ")}...")

            // Step 2: Critique the draft
            val critiquePrompt = """
                Critique the following draft response to ensure it adheres to security, performance,
                style, documentation, cleanliness, and order. Identify any ambiguities regarding
                'what', 'where', and 'how' the task is intended.

                Context: $context

                Draft:
                $draft
            """.trimIndent()
            val critique = callModel(critiquePrompt)
            println("[$id] AlphaEvolve Critique: ${critique.take(50).replace("\n", " ")}...")

            // Step 3: Refine based on critique
            val refinePrompt = """
                Refine the initial draft based on the critique to produce the final, unambiguous output.
                The final output must explicitly and unambiguously describe 'what', 'where', and 'how'
                the task is intended, and must prioritize security, performance, style, documentation,
                cleanliness, and order. Include any required source code, steps, or feedback.

                At the very end of your response, on a new line, provide exactly one next topic for your broadcast,
                formatted precisely as: NEXT_TOPIC: <topic_name>
                If no further broadcast is needed, output: NEXT_TOPIC: NONE

                Draft:
                $draft

                Critique:
                $critique
            """.trimIndent()
            val finalOutput = callModel(refinePrompt)
            println("[$id] AlphaEvolve Final Output Generated.")

            // Extract the next topic
            val topicRegex = Regex("NEXT_TOPIC:\\s*(\\w+)")
            val matchResult = topicRegex.find(finalOutput)
            val nextTopic = matchResult?.groupValues?.get(1)?.trim()

            // Remove the topic marker from the content
            val cleanOutput = finalOutput.replace(topicRegex, "").trim()

            if (nextTopic != null && nextTopic != "NONE") {
                mesh.broadcast(
                    MeshMessage(
                        senderId = id,
                        topic = nextTopic,
                        content = cleanOutput,
                    ),
                )
            } else {
                println("[$id] Processing complete. No further broadcast.")
            }
        } catch (e: Exception) {
            println("[$id] Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun callModel(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (apiKey == "demo") {
                delay(3000)
            }
            model.chat(prompt)
        } catch (e: Exception) {
            "Simulated response due to model error: ${e.message}. Processing prompt: ${prompt.take(30)}..."
        }
    }
}

class PlannerAgent(apiKey: String) : AlphaEvolveAgent("PlannerAgent", "I am a planner responsible for creating and updating execution plans based on user requests and code reviews.", apiKey)

class ExecutorAgent(apiKey: String) : AlphaEvolveAgent("ExecutorAgent", "I am an executor responsible for implementing code based on the provided plans.", apiKey)

class CriticAgent(apiKey: String) : AlphaEvolveAgent("CriticAgent", "I am a critic responsible for reviewing executed code and providing feedback.", apiKey)

fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: "demo"
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val mesh = MeshNetwork(scope)

    val planner = PlannerAgent(apiKey)
    val executor = ExecutorAgent(apiKey)
    val critic = CriticAgent(apiKey)

    println("Starting Agent Mesh Session...")

    // Start agents concurrently
    val plannerJob = scope.launch { planner.start(mesh) }
    val executorJob = scope.launch { executor.start(mesh) }
    val criticJob = scope.launch { critic.start(mesh) }

    // Wait for subscribers to be active
    delay(500)

    // Initiate the mesh with a user request
    mesh.broadcast(
        MeshMessage(
            senderId = "User",
            topic = "user_request",
            content = "Create a new Kotlin class for handling HTTP requests securely and efficiently. Provide clean, well-documented code. Please help refine the prompt continuously.",
        ),
    )

    // Let the mesh run for an extended duration to allow asynchronous processing
    delay(60000)

    println("Agent Mesh Session Completed.")

    // Cancel child coroutines to prevent hanging
    plannerJob.cancel()
    executorJob.cancel()
    criticJob.cancel()
    scope.cancel()
}
