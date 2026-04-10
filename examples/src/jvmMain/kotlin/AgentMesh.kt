// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

/**
 * Represents a message transmitted over the Agent Mesh.
 * Inputs and outputs between agents in the mesh must explicitly and unambiguously
 * describe 'what', 'where', and 'how' the task is intended.
 */
data class MeshMessage(
    val sender: String,
    val what: String,
    val where: String,
    val how: String,
    val context: String = "",
) {
    override fun toString(): String {
        return "[$sender]:\nWHAT: $what\nWHERE: $where\nHOW: $how\nCONTEXT: $context"
    }
}

/**
 * The broadcast network connecting all agents.
 * It uses a [MutableSharedFlow] as a broadcast event bus to ensure all agents
 * receive all messages asynchronously.
 */
class MeshNetwork {
    private val _bus = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 100)
    val bus = _bus.asSharedFlow()

    suspend fun broadcast(message: MeshMessage) {
        _bus.emit(message)
    }
}

/**
 * The base class for every Agent in the A2A Mesh.
 */
abstract class MeshAgent(val name: String, protected val network: MeshNetwork) {
    /**
     * Start listening to the network and processing messages.
     */
    abstract suspend fun listen()

    /**
     * Send a message to the rest of the agents.
     */
    protected suspend fun broadcast(what: String, where: String, how: String, context: String = "") {
        network.broadcast(MeshMessage(sender = name, what = what, where = where, how = how, context = context))
    }
}

/**
 * Common configuration for LangChain4j OpenAiChatModel
 */
private val chatModel by lazy {
    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank() || apiKey == "dummy") {
        OpenAiChatModel.builder()
            .apiKey("demo")
            .modelName("gpt-4o-mini")
            .build()
    } else {
        OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4o")
            .build()
    }
}

/**
 * Helper to invoke the chat model with rate limiting for demo keys.
 */
private suspend fun invokeChatModel(prompt: String): String = withContext(Dispatchers.IO) {
    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank() || apiKey == "dummy") {
        delay(3000) // Rate limiting for public demo key
    }
    chatModel.chat(prompt)
}

/**
 * PlannerAgent decides the structure and plan for an objective.
 */
class PlannerAgent(network: MeshNetwork) : MeshAgent("PlannerAgent", network) {
    override suspend fun listen() = coroutineScope {
        network.bus.collect { message ->
            if (message.sender == "User" || (message.sender == "CriticAgent" && message.what.contains("PLAN_REVISION"))) {
                launch {
                    println("[$name] Received task: ${message.what}")
                    val prompt = """
                        You are a PlannerAgent in an AlphaEvolve Agentic Mesh.
                        Your job is to break down tasks into clear executable steps.
                        Input Task: ${message.what}
                        Context: ${message.context}

                        Respond EXACTLY with WHAT needs to be done, WHERE it should happen, and HOW it should be executed.
                        Format as a JSON or structured list but must be unambiguous. Prioritize Security, Performance, Style, Cleanliness.
                    """.trimIndent()
                    val plan = invokeChatModel(prompt)

                    broadcast(
                        what = "Execute Plan: $plan",
                        where = "Global Project Scope",
                        how = "Execute steps in order asynchronously",
                        context = "Original request: ${message.what}",
                    )
                }
            }
        }
    }
}

/**
 * Main entry point for the Agent Mesh example.
 */
fun main(): Unit = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank() || apiKey == "dummy") {
        System.err.println("WARNING: OPENAI_API_KEY environment variable is not set. The agents will likely fail to connect to OpenAI.")
    }

    val network = MeshNetwork()

    val planner = PlannerAgent(network)
    val executor = ExecutorAgent(network)
    val critic = CriticAgent(network)

    // Run agents in the background
    val job1 = launch { planner.listen() }
    val job2 = launch { executor.listen() }
    val job3 = launch { critic.listen() }

    // Give agents a moment to start collecting on the SharedFlow
    delay(500)

    // Start at least one full Agent2Agent Agent mesh session per Jules' session requirement
    println("--- Starting Agent Mesh Session ---")
    val initialTask = MeshMessage(
        sender = "User",
        what = "Develop a Hello World Kotlin Multiplatform application.",
        where = "Current Directory",
        how = "Using Kotlin 2.0 with minimal dependencies.",
        context = "Ensure performance, style, cleanliness, order, and security are prioritized.",
    )

    // Seed the initial task
    network.broadcast(initialTask)

    // Let the mesh run for an extended duration to let the agents interact more (>= 240 seconds)
    delay(900000)

    println("Agent Mesh Session Completed.")

    // Cancel child coroutines to prevent hanging
    job1.cancel()
    job2.cancel()
    job3.cancel()
}

/**
 * ExecutorAgent performs the actual steps laid out by the planner.
 */
class ExecutorAgent(network: MeshNetwork) : MeshAgent("ExecutorAgent", network) {
    override suspend fun listen() = coroutineScope {
        network.bus.collect { message ->
            if (message.sender == "PlannerAgent" && message.what.startsWith("Execute Plan:")) {
                launch {
                    println("[$name] Executing plan: ${message.what.take(50)}...")
                    val prompt = """
                        You are an ExecutorAgent in an AlphaEvolve Agentic Mesh.
                        Your task is to take a plan and simulate its execution, generating the resulting code or artifact.
                        Plan: ${message.what}
                        Where: ${message.where}
                        How: ${message.how}

                        Provide the concrete outcome or code implementation. Ensure high security, optimal performance, and clean code style.
                    """.trimIndent()
                    val executionResult = invokeChatModel(prompt)

                    broadcast(
                        what = "Review Execution Result",
                        where = message.where,
                        how = "Evaluate against requirements and AlphaEvolve standards",
                        context = executionResult,
                    )
                }
            }
        }
    }
}

/**
 * CriticAgent reviews the executor's output and determines if refinement is needed or if it's finalized.
 */
class CriticAgent(network: MeshNetwork) : MeshAgent("CriticAgent", network) {
    override suspend fun listen() = coroutineScope {
        network.bus.collect { message ->
            if (message.sender == "ExecutorAgent" && message.what == "Review Execution Result") {
                launch {
                    println("[$name] Critiquing execution result...")
                    val prompt = """
                        You are a CriticAgent in an AlphaEvolve Agentic Mesh.
                        Review the following execution result.
                        Result: ${message.context}
                        Where: ${message.where}
                        How it was intended: ${message.how}

                        Does it meet the criteria for security, performance, style, and documentation?
                        If yes, respond with "APPROVED".
                        If no, respond with a specific explanation starting with "PLAN_REVISION" followed by the needed changes.
                    """.trimIndent()
                    val critique = invokeChatModel(prompt)

                    if (critique.contains("APPROVED")) {
                        println("[$name] APPROVED. Finalizing output.")
                        broadcast(
                            what = "FINALIZED",
                            where = message.where,
                            how = "Integration ready",
                            context = message.context,
                        )
                    } else {
                        println("[$name] REJECTED. Requesting revision.")
                        broadcast(
                            what = "PLAN_REVISION: $critique",
                            where = message.where,
                            how = "Refine according to critique",
                            context = message.context,
                        )
                    }
                }
            }
        }
    }
}
