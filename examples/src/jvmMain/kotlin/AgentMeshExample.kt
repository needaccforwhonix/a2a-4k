// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k

import io.github.a2a_4k.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import kotlin.random.Random
import java.util.concurrent.atomic.AtomicInteger

// --- Optimized Prompt Implementation ---
const val OPTIMIZED_PROMPT = """
Task: Design and implement a highly autonomous multi-agent system (Agent Mesh) using the Agent2Agent (A2A) protocol.

Requirements:
1.  **Agentic Context Engineering**: Each agent must dynamically structure its context based on role, task, and history to maximize understanding and performance.
2.  **AlphaEvolve Algorithm**: Implement an iterative reasoning process where agents draft, critique, and refine their outputs before finalizing.
3.  **Full Context Awareness**: Agents must maintain and utilize the full conversation history and shared state.
4.  **Asynchronous Mesh Communication**: Agents must broadcast their finalized outputs as inputs to all other relevant agents in the mesh, enabling parallel evolution of the solution.
5.  **Robustness & Safety**: Ensure secure communication, handle errors gracefully, and prevent infinite loops.
6.  **Self-Improvement**: The system should facilitate continuous refinement of both the agents' logic and the overall solution.

Implementation Goal: Create a runnable example demonstrating this mesh with at least three specialized agents (Planner, Developer, Reviewer) interacting asynchronously.
"""

// --- Simulation Helpers ---

/**
 * Simulates the "AlphaEvolve" algorithm: Draft -> Critique -> Refine.
 */
suspend fun alphaEvolve(input: String, role: String): String {
    // In a real system, this would call an LLM with specific prompts for each stage.
    // Here we simulate the process with delays and string manipulation.

    val draft = "Draft: Based on '$input', as a $role, I propose to..."
    delay(100) // Simulate thinking

    val critique = "Critique: The draft is too generic. Needs more specific details."
    delay(100) // Simulate critique

    val refinement = "Refined: Okay, I will add specific implementation details for '$input'."
    delay(100) // Simulate refinement

    val finalOutput = "Final Answer from $role: I have processed '$input' and produced a robust solution."
    return "$draft\n$critique\n$refinement\n$finalOutput"
}

/**
 * Constructs a rich context for the agent.
 */
fun createRichContext(role: String, task: String, history: List<Message>): String {
    val historyText = history.joinToString("\n") { it.content() }
    return """
        === Context Engineering ===
        Role: $role
        Current Task: $task
        History Summary: ${historyText.takeLast(200)}... (truncated)
        Instructions: Use AlphaEvolve logic.
        ===========================
    """.trimIndent()
}

// --- Agent Implementation ---

class AlphaEvolveAgent(
    val name: String,
    val port: Int,
    val peerUrls: List<String>,
) : TaskHandler {
    private val log = LoggerFactory.getLogger(name)

    // Counter to prevent infinite loops in this demo
    private val processedCount = AtomicInteger(0)

    override fun handle(task: Task): Flow<TaskUpdate> = flow {
        val inputMessage = task.history?.lastOrNull() ?: return@flow
        val input = inputMessage.content()

        log.info("[$name] Received task: $input")

        if (processedCount.get() > 5) {
            log.info("[$name] Max processing limit reached. Stopping.")
            emit(StatusUpdate(TaskStatus(TaskState.COMPLETED, message = assistantMessage("Limit reached."))))
            return@flow
        }
        processedCount.incrementAndGet()

        // 1. Context Engineering
        val context = createRichContext(name, input, task.history ?: emptyList())
        log.debug("[$name] Context created: $context")

        // 2. AlphaEvolve Execution
        val result = alphaEvolve(input, name)
        log.info("[$name] Generated result: $result")

        // 3. Emit Result
        val artifact = Artifact(
            name = "response-$name-${System.currentTimeMillis()}",
            parts = listOf(TextPart(text = result)),
        )
        emit(ArtifactUpdate(listOf(artifact)))
        emit(StatusUpdate(TaskStatus(TaskState.COMPLETED, message = assistantMessage(result))))

        // 4. Broadcast to Mesh (Asynchronously)
        // We launch a coroutine to not block the response flow
        CoroutineScope(Dispatchers.IO).launch {
            broadcastToPeers(result)
        }
    }

    private suspend fun broadcastToPeers(message: String) {
        log.info("[$name] Broadcasting result to peers: $peerUrls")
        val a2aClient = A2AClient(baseUrl = "") // We will construct URLs manually or use A2AClient properly
        // Note: A2AClient takes a baseUrl. We have multiple peers with different base URLs.
        // So we should create a client for each peer or reuse instances.

        peerUrls.forEach { url ->
            try {
                // In a real scenario, we would reuse clients.
                // For this example, we create a fresh one to target the specific peer.
                val peerClient = A2AClient(baseUrl = url)
                // We send the result as a new task input to the peer
                // To avoid infinite loops, we should add metadata, but here we rely on the counter.
                peerClient.sendTask(
                    message = message.toUserMessage(),
                    taskId = "broadcast-${System.currentTimeMillis()}-${Random.nextInt()}",
                    sessionId = "mesh-session",
                )
                peerClient.close()
            } catch (e: Exception) {
                log.warn("[$name] Failed to broadcast to $url: ${e.message}")
            }
        }
    }
}

// --- Main Execution ---

fun main() = runBlocking {
    // 1. Setup Configuration
    val plannerPort = 5001
    val developerPort = 5002
    val reviewerPort = 5003

    val plannerUrl = "http://localhost:$plannerPort"
    val developerUrl = "http://localhost:$developerPort"
    val reviewerUrl = "http://localhost:$reviewerPort"

    // 2. Instantiate Agents
    // Planner talks to Developer
    val planner = AlphaEvolveAgent("Planner", plannerPort, listOf(developerUrl))
    // Developer talks to Reviewer
    val developer = AlphaEvolveAgent("Developer", developerPort, listOf(reviewerUrl))
    // Reviewer talks back to Planner (closing the loop, but handled by counters)
    val reviewer = AlphaEvolveAgent("Reviewer", reviewerPort, listOf(plannerUrl))

    val agents = listOf(planner, developer, reviewer)

    // 3. Start Servers
    val servers = agents.map { agent ->
        val taskManager = BasicTaskManager(agent)
        val capabilities = Capabilities(streaming = true)
        val agentCard = AgentCard(
            name = agent.name,
            description = "AlphaEvolve Mesh Agent",
            url = "http://localhost:${agent.port}",
            version = "1.0.0",
            capabilities = capabilities,
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = listOf(),
        )

        A2AServer(
            port = agent.port,
            agentCard = agentCard,
            taskManager = taskManager,
        ).also { it.start(wait = false) }
    }

    println("Agents started on ports $plannerPort, $developerPort, $reviewerPort")
    delay(2000) // Wait for servers to boot

    // 4. Trigger the Mesh
    println("Triggering the mesh with initial task...")
    val triggerClient = A2AClient(baseUrl = plannerUrl)
    try {
        triggerClient.sendTask(
            message = "Create a Hello World application in Kotlin.".toUserMessage(),
            taskId = "init-task",
            sessionId = "mesh-session",
        )
    } catch (e: Exception) {
        println("Failed to trigger mesh: ${e.message}")
    }
    triggerClient.close()

    // 5. Run for a while to allow interaction
    println("Mesh running... (Press Ctrl+C to stop manually, or wait 10s)")
    delay(10000)

    // 6. Cleanup
    println("Stopping servers...")
    servers.forEach { it.stop() }
    println("Done.")
}
