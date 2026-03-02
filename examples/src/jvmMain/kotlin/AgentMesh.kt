// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

package io.github.a2a_4k

import dev.langchain4j.model.openai.OpenAiChatModel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Message payload for the Agent Mesh.
 * Inputs and outputs must unambiguously describe 'what', 'where', and 'how' the task is intended.
 */
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val what: String = "",
    val where: String = "",
    val how: String = "",
    val context: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Represents the broadcast network connecting all agents.
 */
class MeshNetwork {
    private val _messages = MutableSharedFlow<MeshMessage>()
    val messages = _messages.asSharedFlow()

    suspend fun broadcast(message: MeshMessage) {
        _messages.emit(message)
    }
}

/**
 * Base interface for an autonomous agent in the mesh.
 */
abstract class MeshAgent(
    val name: String,
    protected val network: MeshNetwork,
) {
    private val memory = mutableListOf<MeshMessage>()

    abstract suspend fun process(message: MeshMessage)

    open suspend fun start() = coroutineScope {
        launch {
            network.messages.collect { message ->
                memory.add(message)
                // Only process messages not sent by self
                if (message.sender != name) {
                    launch {
                        process(message)
                    }
                }
            }
        }
    }

    protected suspend fun broadcast(
        content: String,
        what: String = "",
        where: String = "",
        how: String = "",
        context: String = "",
    ) {
        network.broadcast(
            MeshMessage(
                sender = name,
                content = content,
                what = what,
                where = where,
                how = how,
                context = context,
            ),
        )
    }

    /**
     * AlphaEvolve reasoning algorithm using Agentic Context Engineering.
     */
    protected suspend fun alphaEvolveReasoning(input: String, role: String): String = withContext(Dispatchers.IO) {
        val model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY") ?: "dummy")
            .modelName("gpt-4o-mini")
            .build()

        val prompt = """
            You are $name, acting as $role.
            Use Agentic context engineering and alphaevolve algorithm (reasoning, critiquing, and refining outputs before finalizing).
            Analyze the input with full context.
            Input: $input
            Your output must explicitly and unambiguously describe 'what', 'where', and 'how' it is intended.
            Ensure security, performance, style, documentation, cleanliness, and order.
        """.trimIndent()

        try {
            model.chat(prompt)
        } catch (e: Exception) {
            "Simulated response for $name based on $input"
        }
    }
}

class PlannerAgent(network: MeshNetwork) : MeshAgent("PlannerAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("task:", ignoreCase = true) && !message.content.contains("plan:", ignoreCase = true)) {
            println("[$name] Received task from ${message.sender}. Formulating plan...")
            val response = alphaEvolveReasoning(message.content, "Planner")
            broadcast(
                content = "plan: $response",
                what = "Formulate a plan",
                where = "PlannerAgent",
                how = "Agentic context engineering and AlphaEvolve reasoning",
                context = "Context from Planner",
            )
        }
    }
}

class ExecutorAgent(network: MeshNetwork) : MeshAgent("ExecutorAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("plan:", ignoreCase = true) && !message.content.contains("executed:", ignoreCase = true)) {
            println("[$name] Received plan from ${message.sender}. Executing...")
            val response = alphaEvolveReasoning(message.content, "Executor")
            broadcast(
                content = "executed: $response",
                what = "Execute the plan",
                where = "ExecutorAgent",
                how = "Agentic context engineering and AlphaEvolve reasoning",
                context = "Context from Executor",
            )
        }
    }
}

class CriticAgent(network: MeshNetwork) : MeshAgent("CriticAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("executed:", ignoreCase = true) && !message.content.contains("reviewed:", ignoreCase = true)) {
            println("[$name] Received execution result from ${message.sender}. Critiquing...")
            val response = alphaEvolveReasoning(message.content, "Critic")
            broadcast(
                content = "reviewed: $response",
                what = "Critique execution",
                where = "CriticAgent",
                how = "Agentic context engineering and AlphaEvolve reasoning",
                context = "Context from Critic",
            )
        }
    }
}

fun main(): Unit = runBlocking {
    val network = MeshNetwork()
    val planner = PlannerAgent(network)
    val executor = ExecutorAgent(network)
    val critic = CriticAgent(network)

    val job = launch {
        launch { planner.start() }
        launch { executor.start() }
        launch { critic.start() }
    }

    // Wait for subscribers to be active
    delay(500)

    val initialTask = "task: Implement a secure and performant sorting algorithm in Kotlin with comprehensive documentation."
    println("[System] Broadcasting initial task: $initialTask")
    network.broadcast(
        MeshMessage(
            sender = "System",
            content = initialTask,
            what = "Implement sorting algorithm",
            where = "Any appropriate module",
            how = "Performant, secure, with comprehensive documentation",
            context = "Initial user request",
        ),
    )

    // Let the mesh evolve
    delay(10000)
    println("[System] Shutting down agent mesh...")
    job.cancelAndJoin()
}
