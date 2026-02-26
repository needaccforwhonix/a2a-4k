// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.AgentCard
import io.github.a2a_4k.models.Capabilities
import io.github.a2a_4k.models.Message
import io.github.a2a_4k.models.Task
import io.github.a2a_4k.models.TaskState
import io.github.a2a_4k.models.TaskStatus
import io.github.a2a_4k.models.TextPart
import io.github.a2a_4k.models.assistantMessage
import io.github.a2a_4k.models.toUserMessage
import io.github.a2a_4k.models.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a message in the mesh.
 */
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Context for an agent, storing history.
 */
class MeshContext {
    private val history = mutableListOf<MeshMessage>()

    fun add(message: MeshMessage) {
        history.add(message)
    }

    fun getHistory(): List<MeshMessage> = history.toList()

    fun getFormattedHistory(): String {
        return history.joinToString("\n") { "[${it.sender}]: ${it.content}" }
    }
}

/**
 * Base class for an agent in the mesh.
 * Implements the Agentic Context Engineering and AlphaEvolve logic.
 */
abstract class MeshAgent(
    val name: String,
    val port: Int,
    private val orchestrator: MeshOrchestrator
) {
    private val context = MeshContext()
    private val logger = LoggerFactory.getLogger(name)

    // Server instance
    private var server: A2AServer? = null

    fun start() {
        val taskHandler = object : TaskHandler {
            override fun handle(task: Task): Flow<TaskUpdate> = flow {
                val message = task.history?.last() ?: error("Message was not found!")
                val inputContent = message.content()

                // Store input in context
                context.add(MeshMessage(sender = "External", content = inputContent))

                // AlphaEvolve Logic: Reasoning, Critiquing, Refining
                val output = alphaEvolveProcess(inputContent)

                // Store output in context
                context.add(MeshMessage(sender = name, content = output))

                // Emit completion
                emit(StatusUpdate(TaskStatus(TaskState.COMPLETED, message = assistantMessage(output))))
            }
        }

        val agentCard = AgentCard(
            name = name,
            description = "Mesh Agent $name",
            url = "http://localhost:$port",
            version = "0.0.1",
            capabilities = Capabilities(),
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = emptyList()
        )

        server = A2AServer(
            host = "0.0.0.0",
            port = port,
            agentCard = agentCard,
            taskManager = BasicTaskManager(taskHandler)
        )

        server?.start(wait = false)
        logger.info("$name started on port $port")
    }

    fun stop() {
        server?.stop()
    }

    /**
     * The AlphaEvolve algorithm implementation.
     * Iteratively reasons, critiques, and refines the output.
     */
    private suspend fun alphaEvolveProcess(input: String): String {
        logger.info("Starting AlphaEvolve process for input: $input")

        var currentThought = generateInitialThought(input)
        val maxIterations = 3

        for (i in 1..maxIterations) {
            val critique = critiqueThought(currentThought)
            if (critique.isGoodEnough) {
                logger.info("Thought accepted at iteration $i")
                break
            }
            logger.info("Refining thought at iteration $i based on critique: ${critique.feedback}")
            currentThought = refineThought(currentThought, critique)
        }

        return currentThought
    }

    // Abstract methods to be implemented by concrete agents or with default simulated logic
    open fun generateInitialThought(input: String): String {
        return "Initial thought on '$input' by $name based on context: ${context.getHistory().size} messages."
    }

    open fun critiqueThought(thought: String): Critique {
        // specific agents can override this
        return Critique(isGoodEnough = true, feedback = "Looks good.")
    }

    open fun refineThought(thought: String, critique: Critique): String {
        return "$thought (Refined: ${critique.feedback})"
    }

    data class Critique(val isGoodEnough: Boolean, val feedback: String)
}

// Concrete Agents

class PlannerAgent(port: Int, orchestrator: MeshOrchestrator) : MeshAgent("Planner", port, orchestrator) {
    override fun generateInitialThought(input: String): String {
        return "Plan: Break down '$input' into subtasks."
    }

    override fun critiqueThought(thought: String): Critique {
        if (!thought.contains("step")) {
             return Critique(false, "The plan needs explicit steps.")
        }
        return Critique(true, "Plan looks solid.")
    }

    override fun refineThought(thought: String, critique: Critique): String {
        return "$thought -> Step 1: Analyze. Step 2: Execute."
    }
}

class ExecutorAgent(port: Int, orchestrator: MeshOrchestrator) : MeshAgent("Executor", port, orchestrator) {
    override fun generateInitialThought(input: String): String {
        return "Executing: processing '$input'."
    }
}

class CriticAgent(port: Int, orchestrator: MeshOrchestrator) : MeshAgent("Critic", port, orchestrator) {
    override fun generateInitialThought(input: String): String {
        return "Review: analyzing '$input' for errors."
    }
}

/**
 * Orchestrates the mesh.
 */
class MeshOrchestrator {
    private val agents = mutableListOf<MeshAgent>()

    fun register(agent: MeshAgent) {
        agents.add(agent)
    }

    fun startAll() {
        agents.forEach { it.start() }
    }

    fun stopAll() {
        agents.forEach { it.stop() }
    }

    suspend fun runScenario(initialInput: String) {
        println("Starting Scenario: $initialInput")

        // 1. Send to Planner
        val planner = agents.find { it is PlannerAgent } ?: return
        val response1 = sendToAgent(planner, initialInput)
        println("Planner Output: $response1")

        // 2. Broadcast Planner output to Executor and Critic
        val executor = agents.find { it is ExecutorAgent } ?: return
        val critic = agents.find { it is CriticAgent } ?: return

        // Parallel execution
        val scope = CoroutineScope(Dispatchers.IO)
        val job1 = scope.launch {
            val res = sendToAgent(executor, response1)
            println("Executor Output: $res")
        }
        val job2 = scope.launch {
            val res = sendToAgent(critic, response1)
            println("Critic Output: $res")
        }

        job1.join()
        job2.join()

        println("Scenario Complete.")
    }

    private suspend fun sendToAgent(agent: MeshAgent, content: String): String {
        val client = A2AClient(baseUrl = "http://localhost:${agent.port}")
        return try {
            val response = client.sendTask(
                taskId = UUID.randomUUID().toString(),
                sessionId = UUID.randomUUID().toString(),
                message = content.toUserMessage()
            )
            client.close()

            // Extract result from Task
            val task = response.result
            val message = task?.status?.message
            message?.content() ?: "No response"

        } catch (e: Exception) {
            client.close()
            "Error: ${e.message}"
        }
    }
}

object RunAgentMesh {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val orchestrator = MeshOrchestrator()

        // Using distinct ports
        val planner = PlannerAgent(8081, orchestrator)
        val executor = ExecutorAgent(8082, orchestrator)
        val critic = CriticAgent(8083, orchestrator)

        orchestrator.register(planner)
        orchestrator.register(executor)
        orchestrator.register(critic)

        orchestrator.startAll()

        // Give servers time to start
        delay(2000)

        try {
            orchestrator.runScenario("Build a better search engine.")
        } finally {
            orchestrator.stopAll()
            // Force exit
            System.exit(0)
        }
    }
}
