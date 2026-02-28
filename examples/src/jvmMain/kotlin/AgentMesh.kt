// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Represents a message broadcasted across the Agent Mesh.
 * Explicitly describes what is wanted, where, and how.
 */
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val intent: String,
    val content: String,
    val context: String,
    val requiresAction: Boolean = false,
) {
    override fun toString(): String {
        return "[$sender -> MESH | Intent: $intent] Content: ${content.take(100)}..."
    }
}

/**
 * Interface for an agent in the Agent Mesh.
 */
interface MeshAgent {
    val name: String
    suspend fun start(network: MeshNetwork, scope: CoroutineScope)
    suspend fun processMessage(message: MeshMessage, network: MeshNetwork)
}

/**
 * The broadcast network connecting all agents.
 * All agents receive every output as input.
 */
class MeshNetwork {
    private val _bus = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 100)
    val bus = _bus.asSharedFlow()

    suspend fun broadcast(message: MeshMessage) {
        _bus.emit(message)
    }
}

/**
 * Base abstract class for an A2A agent implementing AlphaEvolve and Agentic Context Engineering.
 */
abstract class BaseA2AAgent(override val name: String) : MeshAgent {
    protected val logger = LoggerFactory.getLogger(name)

    protected val model = OpenAiChatModel.builder()
        .baseUrl("http://langchain4j.dev/demo/openai/v1")
        .apiKey(System.getenv("OPENAI_API_KEY") ?: "demo")
        .modelName("gpt-4o-mini")
        .build()

    override suspend fun start(network: MeshNetwork, scope: CoroutineScope) {
        network.bus.onEach { message ->
            // In a true broadcast topology, agents receive everything.
            // They ignore their own messages to avoid immediate trivial loops,
            // though they could process them if reflection is needed.
            if (message.sender != name) {
                try {
                    processMessage(message, network)
                } catch (e: Exception) {
                    logger.error("Error processing message in $name", e)
                }
            }
        }.launchIn(scope)
        logger.info("$name started and listening to the mesh.")
    }

    /**
     * Agentic Context Engineering & AlphaEvolve Algorithm
     * Prompts the agent to reason, critique, refine, and produce output.
     */
    protected fun applyAlphaEvolve(task: String, fullContext: String): String {
        val prompt = """
            |You are an autonomous agent in an Agent2Agent (A2A) mesh network.
            |Your core principles are Security, Performance, Style, Documentation, Cleanliness, and Order.
            |
            |Agentic Context Engineering & AlphaEvolve Algorithm
            |
            |Full Context:
            |$fullContext
            |
            |Current Task:
            |$task
            |
            |Follow this iterative process (AlphaEvolve) before producing the final output:
            |1. REASON: Analyze the task and context. Determine what needs to be done.
            |2. CRITIQUE: Identify potential flaws, security risks, performance issues, or style violations in your reasoning.
            |3. REFINE: Improve the approach based on the critique, ensuring cleanliness, order, and full documentation.
            |4. OUTPUT: Provide the final optimal response clearly defining WHAT is wanted, WHERE it goes, and HOW it operates.
            |
            |Provide your final response structured with the above 4 sections.
        """.trimMargin()

        return prompt
    }

    protected suspend fun chatWithModel(prompt: String): String {
        return withContext(Dispatchers.IO) {
            model.chat(prompt)
        }
    }
}

class PlannerAgent : BaseA2AAgent("PlannerAgent") {
    override suspend fun processMessage(message: MeshMessage, network: MeshNetwork) {
        if (message.intent == "PLAN_REQUEST" && message.requiresAction) {
            logger.info("PlannerAgent received plan request from ${message.sender}")
            val prompt = applyAlphaEvolve("Create a detailed plan for: ${message.content}", message.context)
            val response = chatWithModel(prompt)

            network.broadcast(
                MeshMessage(
                    sender = name,
                    intent = "PLAN_CREATED",
                    content = response,
                    context = message.context + "\n[Plan added by PlannerAgent]",
                    requiresAction = true,
                ),
            )
        }
    }
}

class ExecutorAgent : BaseA2AAgent("ExecutorAgent") {
    override suspend fun processMessage(message: MeshMessage, network: MeshNetwork) {
        if (message.intent == "PLAN_CREATED" && message.requiresAction) {
            logger.info("ExecutorAgent received plan to execute from ${message.sender}")
            val prompt = applyAlphaEvolve("Execute the following plan robustly: ${message.content}", message.context)
            val response = chatWithModel(prompt)

            network.broadcast(
                MeshMessage(
                    sender = name,
                    intent = "EXECUTION_COMPLETED",
                    content = response,
                    context = message.context + "\n[Execution added by ExecutorAgent]",
                    requiresAction = true,
                ),
            )
        }
    }
}

class CriticAgent : BaseA2AAgent("CriticAgent") {
    override suspend fun processMessage(message: MeshMessage, network: MeshNetwork) {
        if (message.intent == "EXECUTION_COMPLETED" && message.requiresAction) {
            logger.info("CriticAgent received execution for review from ${message.sender}")
            val prompt = applyAlphaEvolve("Review the execution against the original context for security, performance, style, and cleanliness: ${message.content}", message.context)
            val response = chatWithModel(prompt)

            network.broadcast(
                MeshMessage(
                    sender = name,
                    intent = "REVIEW_COMPLETED",
                    content = response,
                    context = message.context + "\n[Review added by CriticAgent]",
                    requiresAction = false,
                ),
            )
            println("Agent Mesh processing reached the Critic Review stage.")
        }
    }
}

fun main() = runBlocking {
    val network = MeshNetwork()
    // Using an unconfined or default dispatcher for the mesh agents
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val agents = listOf(
        PlannerAgent(),
        ExecutorAgent(),
        CriticAgent(),
    )

    // Start all agents. They all listen to the broadcast network.
    agents.forEach { it.start(network, scope) }

    delay(1000) // allow agents to start and subscribe

    println("Starting the Agent Mesh Session...")

    // The initial request acts as the seed for the mesh
    network.broadcast(
        MeshMessage(
            sender = "System",
            intent = "PLAN_REQUEST",
            content = "Implement a simple secure REST endpoint for user greeting in Kotlin.",
            context = "Kotlin project focusing on security, performance, and clean code.",
            requiresAction = true,
        ),
    )

    // Wait to allow agents to process and respond asynchronously
    // In a real application, we might wait for a specific terminal event
    delay(45000)

    scope.cancel()
    println("Agent Mesh Session completed.")
}
