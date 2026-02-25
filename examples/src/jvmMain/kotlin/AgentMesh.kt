// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

// Data class for messages
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

// Abstract Mesh Agent
abstract class MeshAgent(val name: String, private val mesh: AgentMesh) {
    protected val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Channel to receive messages
    private val inbox = Channel<MeshMessage>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (msg in inbox) {
                if (msg.sender != name) { // Don't process own messages
                    try {
                        process(msg)
                    } catch (e: Exception) {
                        println("[$name] Error processing message: ${e.message}")
                    }
                }
            }
        }
    }

    fun receive(message: MeshMessage) {
        inbox.trySend(message)
    }

    protected abstract suspend fun process(message: MeshMessage)

    protected suspend fun broadcast(content: String, metadata: Map<String, String> = emptyMap()) {
        val msg = MeshMessage(sender = name, content = content, metadata = metadata)
        mesh.broadcast(msg)
    }

    // Agentic Context Engineering: Reason -> Critique -> Refine
    protected suspend fun agenticContextEngineering(input: String): String {
        // Step 1: Reasoning
        val reasoning = reason(input)
        delay(100) // Simulate processing time

        // Step 2: Critique
        val critique = critique(reasoning)
        delay(100) // Simulate processing time

        // Step 3: Refine
        val refined = refine(reasoning, critique)
        delay(100) // Simulate processing time

        return refined
    }

    // AlphaEvolve: Iterative improvement
    protected suspend fun alphaEvolve(context: String): String {
        var currentOutput = context
        // Simulate evolution loop
        repeat(2) { iteration ->
             delay(50)
             currentOutput = refine(currentOutput, "Self-reflection iteration ${iteration + 1}: Improving clarity and depth.")
        }
        return currentOutput
    }

    private fun reason(input: String): String {
        return "Analysis of '$input': This requires decomposing the problem into sub-tasks and identifying constraints."
    }

    private fun critique(input: String): String {
        return "Critique: The analysis seems sound but could be more specific about the 'constraints' mentioned."
    }

    private fun refine(input: String, feedback: String): String {
        return "$input\n[Refined with feedback: $feedback]"
    }
}

// Concrete Agents
class PlannerAgent(mesh: AgentMesh) : MeshAgent("Planner", mesh) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("PLAN_REQUEST")) {
            println("[$name] Received request: ${message.content}")
            val plan = agenticContextEngineering("Create a detailed plan for: ${message.content}")
            val evolvedPlan = alphaEvolve(plan)

            val output = """
                PLAN_CREATED:
                $evolvedPlan
            """.trimIndent()

            broadcast(output)
        }
    }
}

class ExecutorAgent(mesh: AgentMesh) : MeshAgent("Executor", mesh) {
    override suspend fun process(message: MeshMessage) {
         if (message.content.contains("PLAN_CREATED")) {
            println("[$name] Received plan to execute.")
            val execution = agenticContextEngineering("Executing the plan described in: ${message.content.lines().firstOrNull()}")
            val evolvedExecution = alphaEvolve(execution)

            val output = """
                EXECUTION_RESULT:
                $evolvedExecution
            """.trimIndent()

            broadcast(output)
        }
    }
}

class CriticAgent(mesh: AgentMesh) : MeshAgent("Critic", mesh) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("EXECUTION_RESULT")) {
             println("[$name] Received execution result to review.")
             val review = agenticContextEngineering("Reviewing execution result")
             val evolvedReview = alphaEvolve(review)

             val output = """
                 REVIEW_RESULT:
                 $evolvedReview
             """.trimIndent()

             broadcast(output)
        }
    }
}

class OptimizerAgent(mesh: AgentMesh) : MeshAgent("Optimizer", mesh) {
    override suspend fun process(message: MeshMessage) {
        if (message.content.contains("REVIEW_RESULT")) {
            println("[$name] Optimizing based on review.")
            val optimization = agenticContextEngineering("Optimizing process based on review")

            val output = """
                OPTIMIZATION_COMPLETE:
                $optimization
            """.trimIndent()

            broadcast(output)
        }
    }
}


// Agent Mesh Manager
class AgentMesh {
    private val agents = mutableListOf<MeshAgent>()

    fun register(agent: MeshAgent) {
        agents.add(agent)
        println("[Mesh] Registered agent: ${agent.name}")
    }

    suspend fun broadcast(message: MeshMessage) {
        println("\n[Mesh] Broadcasting message from ${message.sender} [${message.id.take(8)}]")
        agents.forEach { it.receive(message) }
    }
}

fun main() = runBlocking {
    println("Starting Agent Mesh...")
    val mesh = AgentMesh()

    val planner = PlannerAgent(mesh)
    val executor = ExecutorAgent(mesh)
    val critic = CriticAgent(mesh)
    val optimizer = OptimizerAgent(mesh)

    mesh.register(planner)
    mesh.register(executor)
    mesh.register(critic)
    mesh.register(optimizer)

    delay(1000)

    // Initial Trigger
    println("Injecting initial prompt...")
    mesh.broadcast(MeshMessage(sender = "User", content = "PLAN_REQUEST: Build a secure and performant Agent2Agent framework."))

    // Run for a fixed duration to allow agents to interact
    delay(10000)

    println("Agent Mesh finished.")
    // Cancel all agent scopes
    // In a real app we would have a shutdown mechanism
}
