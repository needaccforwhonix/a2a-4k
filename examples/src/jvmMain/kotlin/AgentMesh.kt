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
    val topicsOfInterest: Set<String>
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
    override val topicsOfInterest: Set<String>,
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
        println("[$id] Started and listening for topics: $topicsOfInterest")
        mesh.messages
            .filter { it.topic in topicsOfInterest && it.senderId != id }
            .collect { message ->
                println("[$id] Received message from ${message.senderId} on ${message.topic}")
                processMessage(message, mesh)
            }
    }

    private suspend fun processMessage(message: MeshMessage, mesh: MeshNetwork) {
        try {
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
                cleanliness, and order.

                Draft:
                $draft

                Critique:
                $critique
            """.trimIndent()
            val finalOutput = callModel(refinePrompt)
            println("[$id] AlphaEvolve Final Output Generated.")

            // Determine next topic based on agent type (simple routing)
            val nextTopic = determineNextTopic(message.topic)

            if (nextTopic != null) {
                mesh.broadcast(
                    MeshMessage(
                        senderId = id,
                        topic = nextTopic,
                        content = finalOutput,
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
            model.chat(prompt)
        } catch (e: Exception) {
            "Simulated response due to model error: ${e.message}. Processing prompt: ${prompt.take(30)}..."
        }
    }

    abstract fun determineNextTopic(currentTopic: String): String?
}

class PlannerAgent(apiKey: String) : AlphaEvolveAgent("PlannerAgent", setOf("user_request", "code_review"), apiKey) {
    override fun determineNextTopic(currentTopic: String): String? {
        return when (currentTopic) {
            "user_request" -> "plan_created"
            "code_review" -> "plan_updated"
            else -> null
        }
    }
}

class ExecutorAgent(apiKey: String) : AlphaEvolveAgent("ExecutorAgent", setOf("plan_created", "plan_updated"), apiKey) {
    override fun determineNextTopic(currentTopic: String): String? {
        return when (currentTopic) {
            "plan_created", "plan_updated" -> "code_executed"
            else -> null
        }
    }
}

class CriticAgent(apiKey: String) : AlphaEvolveAgent("CriticAgent", setOf("code_executed"), apiKey) {
    override fun determineNextTopic(currentTopic: String): String? {
        return when (currentTopic) {
            "code_executed" -> "code_review" // loops back to planner or finalize
            else -> null
        }
    }
}

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
            content = "Create a new Kotlin class for handling HTTP requests securely and efficiently. Provide clean, well-documented code.",
        ),
    )

    // Let the mesh run for a while
    delay(15000)

    println("Agent Mesh Session Completed.")

    // Cancel child coroutines to prevent hanging
    plannerJob.cancel()
    executorJob.cancel()
    criticJob.cancel()
    scope.cancel()
}
