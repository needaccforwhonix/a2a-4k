// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.examples

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Represents a message in the Agent Mesh.
 */
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val type: MeshMessageType = MeshMessageType.INFO,
    val target: String? = null // Null means broadcast to all
)

enum class MeshMessageType {
    TASK,
    RESPONSE,
    CRITIQUE,
    REFINEMENT,
    INFO
}

/**
 * The Network (Event Bus) that connects all agents.
 */
class MeshNetwork {
    private val _events = MutableSharedFlow<MeshMessage>()
    val events = _events.asSharedFlow()

    suspend fun broadcast(message: MeshMessage) {
        _events.emit(message)
    }
}

/**
 * Abstract Base Agent in the Mesh.
 */
abstract class MeshAgent(
    val name: String,
    protected val network: MeshNetwork,
    protected val scope: CoroutineScope
) {
    init {
        scope.launch {
            network.events
                .filter { it.sender != name } // Don't process own messages
                .filter { it.target == null || it.target == name } // Process broadcast or direct messages
                .collect { message ->
                    processMessage(message)
                }
        }
    }

    protected abstract suspend fun processMessage(message: MeshMessage)

    protected suspend fun send(content: String, type: MeshMessageType = MeshMessageType.INFO, target: String? = null) {
        val msg = MeshMessage(sender = name, content = content, type = type, target = target)
        println("[$name] -> [${target ?: "ALL"}]: $content")
        network.broadcast(msg)
    }
}

/**
 * Agent implementing the AlphaEvolve algorithm (Reason -> Critique -> Refine).
 */
abstract class AlphaEvolveAgent(
    name: String,
    network: MeshNetwork,
    scope: CoroutineScope
) : MeshAgent(name, network, scope) {

    // Simulated LLM context
    protected val context = mutableListOf<String>()

    protected fun addToContext(role: String, text: String) {
        context.add("$role: $text")
    }

    /**
     * The AlphaEvolve loop: Reason -> Critique -> Refine
     */
    protected suspend fun evolve(input: String): String {
        // 1. Reason
        val reasoning = reason(input)

        // 2. Critique
        val critique = critique(reasoning)

        // 3. Refine
        val refinedOutput = refine(reasoning, critique)

        return refinedOutput
    }

    private suspend fun reason(input: String): String {
        // In a real scenario, this would call an LLM with a "Reasoning" prompt
        delay(100) // Simulate processing
        return "Reasoning about '$input'..."
    }

    private suspend fun critique(reasoning: String): String {
        // In a real scenario, this would call an LLM with a "Critique" prompt
        delay(100) // Simulate processing
        return "Critique of reasoning: valid but could be more specific."
    }

    private suspend fun refine(reasoning: String, critique: String): String {
        // In a real scenario, this would call an LLM with a "Refine" prompt
        delay(100) // Simulate processing
        return "Refined output based on '$reasoning' and '$critique'."
    }
}

// --- Concrete Agents ---

class PlannerAgent(network: MeshNetwork, scope: CoroutineScope) : AlphaEvolveAgent("Planner", network, scope) {
    override suspend fun processMessage(message: MeshMessage) {
        if (message.type == MeshMessageType.TASK) {
            println("[$name] Received task: ${message.content}")
            addToContext("User", message.content)

            // Apply Agentic Context Engineering
            val contextheader = "Role: Planner. Objective: Decompose tasks into actionable steps."

            val plan = evolve("Create a plan for: ${message.content}")

            // Simulate a concrete plan output
            val concretePlan = "1. Analyze requirements. 2. Implement core logic. 3. Test."

            send("Plan created: $concretePlan", MeshMessageType.RESPONSE)
        }
    }
}

class ExecutorAgent(network: MeshNetwork, scope: CoroutineScope) : AlphaEvolveAgent("Executor", network, scope) {
    override suspend fun processMessage(message: MeshMessage) {
        if (message.type == MeshMessageType.RESPONSE && message.sender == "Planner") {
            println("[$name] Received plan: ${message.content}")

            val executionResult = evolve("Execute: ${message.content}")

            // Simulate execution
            val result = "Execution complete. All steps passed."

            // Sending as CRITIQUE just to distinguish or maybe RESPONSE is fine, but let's make sure Critic picks it up
            // Critic expects RESPONSE from Executor
            send(result, MeshMessageType.RESPONSE)
        }
    }
}

class CriticAgent(network: MeshNetwork, scope: CoroutineScope) : AlphaEvolveAgent("Critic", network, scope) {
    override suspend fun processMessage(message: MeshMessage) {
        if (message.type == MeshMessageType.RESPONSE && message.sender == "Executor") {
             println("[$name] Reviewing execution: ${message.content}")

             val review = evolve("Review: ${message.content}")

             // Simulate review
             val finalVerdict = "Approved. Great job!"

             send(finalVerdict, MeshMessageType.INFO)
        }
    }
}


fun main() = runBlocking {
    println("Starting Agent Mesh...")

    val network = MeshNetwork()
    val scope = CoroutineScope(Dispatchers.Default) // Or use a custom scope

    // Initialize Agents
    val planner = PlannerAgent(network, scope)
    val executor = ExecutorAgent(network, scope)
    val critic = CriticAgent(network, scope)

    // Allow agents to subscribe
    delay(500)

    // Inject initial task
    val initialTask = "Build a new feature for the Agent Mesh."
    println("[System] Injecting Task: $initialTask")
    network.broadcast(MeshMessage(sender = "System", content = initialTask, type = MeshMessageType.TASK))

    // Keep the process alive for a bit to see the interaction
    delay(5000)

    println("Agent Mesh Demo Finished.")
}
