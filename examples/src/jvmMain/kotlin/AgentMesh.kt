// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

package io.github.a2a_4k

import dev.langchain4j.model.openai.OpenAiChatModel
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
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
interface MeshAgent {
    val name: String
    suspend fun process(message: MeshMessage)
    suspend fun start()
}

/**
 * Abstract class implementing the AlphaEvolve algorithm.
 */
abstract class AlphaEvolveAgent(
    override val name: String,
    protected val network: MeshNetwork,
) : MeshAgent {
    protected val memory = CopyOnWriteArrayList<MeshMessage>()

    override suspend fun start() = coroutineScope {
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

        val recentHistory = memory.takeLast(20).joinToString("\n") { "[${it.sender}]: ${it.content}" }

        val prompt = """
            You are $name, acting as $role.
            Use Agentic context engineering and alphaevolve algorithm (reasoning, critiquing, and refining outputs before finalizing).
            Analyze the input with full context.
            Recent History:
            $recentHistory

            Input: $input
            Your output must explicitly and unambiguously describe 'what', 'where', and 'how' it is intended.
            Ensure security, performance, style, documentation, cleanliness, and order.

            If you determine that another agent should process this next, append 'NEXT_TOPIC: <topic_name>' to your response, where <topic_name> is a keyword like 'plan', 'execute', 'critique', 'optimize', 'secure', 'perform', 'style', 'document', 'clean', 'order'.
        """.trimIndent()

        delay(3000) // Rate limiting for demo environment

        try {
            model.chat(prompt)
        } catch (e: Exception) {
            "Simulated response for $name based on $input\nNEXT_TOPIC: execute"
        }
    }
}

class PlannerAgent(network: MeshNetwork) : AlphaEvolveAgent("PlannerAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("task:", ignoreCase = true) || message.content.contains("NEXT_TOPIC: plan", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Planner")
            broadcast(
                content = response,
                what = "Formulate a plan",
                where = "PlannerAgent",
                how = "Agentic context engineering and AlphaEvolve reasoning",
                context = "Context from Planner",
            )
        }
    }
}

class ExecutorAgent(network: MeshNetwork) : AlphaEvolveAgent("ExecutorAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: execute", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Executor")
            broadcast(
                content = response,
                what = "Execute the plan",
                where = "ExecutorAgent",
                how = "Agentic context engineering and AlphaEvolve reasoning",
                context = "Context from Executor",
            )
        }
    }
}

class CriticAgent(network: MeshNetwork) : AlphaEvolveAgent("CriticAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: critique", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Critic")
            broadcast(
                content = response,
                what = "Critique execution",
                where = "CriticAgent",
                how = "Agentic context engineering and AlphaEvolve reasoning",
                context = "Context from Critic",
            )
        }
    }
}

class OptimizationAgent(network: MeshNetwork) : AlphaEvolveAgent("OptimizationAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: optimize", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Optimizer")
            broadcast(content = response, what = "Optimize", where = "OptimizationAgent", how = "AlphaEvolve")
        }
    }
}

class SecurityAgent(network: MeshNetwork) : AlphaEvolveAgent("SecurityAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: secure", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Security Expert")
            broadcast(content = response, what = "Security check", where = "SecurityAgent", how = "AlphaEvolve")
        }
    }
}

class PerformanceAgent(network: MeshNetwork) : AlphaEvolveAgent("PerformanceAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: perform", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Performance Expert")
            broadcast(content = response, what = "Performance check", where = "PerformanceAgent", how = "AlphaEvolve")
        }
    }
}

class StyleAgent(network: MeshNetwork) : AlphaEvolveAgent("StyleAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: style", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Style Expert")
            broadcast(content = response, what = "Style check", where = "StyleAgent", how = "AlphaEvolve")
        }
    }
}

class DocumentationAgent(network: MeshNetwork) : AlphaEvolveAgent("DocumentationAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: document", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Documentation Expert")
            broadcast(content = response, what = "Documentation check", where = "DocumentationAgent", how = "AlphaEvolve")
        }
    }
}

class CleanlinessAgent(network: MeshNetwork) : AlphaEvolveAgent("CleanlinessAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: clean", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Cleanliness Expert")
            broadcast(content = response, what = "Cleanliness check", where = "CleanlinessAgent", how = "AlphaEvolve")
        }
    }
}

class OrderAgent(network: MeshNetwork) : AlphaEvolveAgent("OrderAgent", network) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("NEXT_TOPIC: order", ignoreCase = true)) {
            println("[$name] Processing message from ${message.sender}...")
            val response = alphaEvolveReasoning(message.content, "Order Expert")
            broadcast(content = response, what = "Order check", where = "OrderAgent", how = "AlphaEvolve")
        }
    }
}

fun main(): Unit = runBlocking {
    val network = MeshNetwork()

    val agents = listOf(
        PlannerAgent(network),
        ExecutorAgent(network),
        CriticAgent(network),
        OptimizationAgent(network),
        SecurityAgent(network),
        PerformanceAgent(network),
        StyleAgent(network),
        DocumentationAgent(network),
        CleanlinessAgent(network),
        OrderAgent(network),
    )

    val job = launch {
        agents.forEach { agent ->
            launch { agent.start() }
        }
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

    // Let the mesh evolve for at least 240 seconds
    delay(240_000)
    println("[System] Shutting down agent mesh...")
    job.cancelAndJoin()
}
