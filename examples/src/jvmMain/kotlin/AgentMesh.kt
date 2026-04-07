// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.examples

import dev.langchain4j.model.openai.OpenAiChatModel
import java.util.concurrent.CopyOnWriteArrayList
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
    private val history = CopyOnWriteArrayList<MeshMessage>()

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
        println("[$id] Started and listening to all broadcasts. Role: $roleDescription")
        mesh.messages
            .collect { message ->
                history.add(message)
                if (message.senderId != id) {
                    // Process each message asynchronously so we don't block the shared flow
                    mesh.scope.launch {
                        processMessage(message, mesh)
                    }
                }
            }
    }

    private suspend fun processMessage(message: MeshMessage, mesh: MeshNetwork) {
        try {
            // Build context window
            val windowContext = history.takeLast(20).joinToString("\n") {
                "[${it.senderId} on ${it.topic}]: ${it.content.take(200)}"
            }

            // Self-filtering: evaluate if this agent should process the message
            val evalPrompt = """
                You are $id. Your role is: $roleDescription
                A message was broadcast by ${message.senderId} on the topic "${message.topic}".
                Message content: "${message.content}"

                Recent mesh history:
                $windowContext

                Based on your role and the recent mesh history, should you actively react to this message?
                Consider if you can help, clarify ambiguities, or advance the task.
                Only react if explicitly addressed or required to prevent broadcast loops.
                Answer strictly with YES or NO.
            """.trimIndent()

            val shouldReactResponse = callModel(evalPrompt).trim()
            if (!shouldReactResponse.equals("YES", ignoreCase = true)) {
                return // Ignored
            }

            println("[$id] Decided to process message from ${message.senderId} on ${message.topic}")

            // AlphaEvolve Algorithm: Reasoning, Critiquing, and Refining
            val context = "Context: Message from ${message.senderId} on ${message.topic}. Content: ${message.content}\n\nRecent History:\n$windowContext"

            // Step 1: Reason and generate initial draft
            val draftPrompt = """
                Analyze the following context and propose an initial response or action plan as $id with the role: $roleDescription.
                You are participating in an agent mesh.
                CRITICAL RULE: Your output MUST explicitly and unambiguously describe 'what', 'where', and 'how' the task is intended.
                Focus heavily on prioritizing security, performance, style, documentation, cleanliness, and order.
                Actively and proactively offer help to clarify any ambiguous tasks or assist other agents.
                Constantly seek to optimize these prompts and their implementation within this process to evolve and stay up-to-date.

                $context
            """.trimIndent()
            val draft = callModel(draftPrompt)
            println("[$id] AlphaEvolve Draft: ${draft.take(50).replace("\n", " ")}...")

            // Step 2: Critique the draft
            val critiquePrompt = """
                Critique the following draft response to ensure it strictly adheres to security, performance,
                style, documentation, cleanliness, and order.
                CRITICAL RULE: Identify and reject any ambiguities regarding 'what', 'where', and 'how' the task is intended.
                The feedback MUST ensure the mesh continuously evolves and stays up-to-date, specifically addressing how to optimize prompts and agent implementations.

                Context: $context

                Draft:
                $draft
            """.trimIndent()
            val critique = callModel(critiquePrompt)
            println("[$id] AlphaEvolve Critique: ${critique.take(50).replace("\n", " ")}...")

            // Step 3: Refine based on critique
            val refinePrompt = """
                Refine the initial draft based on the critique to produce the final, absolutely unambiguous output.
                CRITICAL RULE: The final output MUST explicitly and unambiguously describe 'what', 'where', and 'how' the task is intended.
                It MUST prioritize security, performance, style, documentation, cleanliness, and order.
                Focus heavily on continuous improvement, optimizing prompts, and advancing the implementation.
                Be proactive in offering help and clarification.

                IMPORTANT: You must determine the next topic for broadcast to continue the mesh execution.
                End your response exactly with a new line containing ONLY:
                NEXT_TOPIC: <topic_name>

                Draft:
                $draft

                Critique:
                $critique
            """.trimIndent()
            val finalOutput = callModel(refinePrompt)
            println("[$id] AlphaEvolve Final Output Generated.")

            // Determine next topic by parsing the output
            val nextTopicRegex = Regex("""NEXT_TOPIC:\s*(.+)""")
            val matchResult = nextTopicRegex.find(finalOutput)
            val nextTopic = matchResult?.groupValues?.get(1)?.trim()

            if (nextTopic != null) {
                // Strip the NEXT_TOPIC line from the content
                val cleanContent = finalOutput.replace(nextTopicRegex, "").trim()
                mesh.broadcast(
                    MeshMessage(
                        senderId = id,
                        topic = nextTopic,
                        content = cleanContent,
                    ),
                )
            } else {
                println("[$id] Processing complete. No next topic found in output. Broadcasting on 'general' topic as fallback.")
                mesh.broadcast(
                    MeshMessage(
                        senderId = id,
                        topic = "general",
                        content = finalOutput,
                    ),
                )
            }
        } catch (e: Exception) {
            println("[$id] Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun callModel(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (apiKey == "demo") {
                delay(3000) // Prevent RateLimitException for demo key
            }
            model.chat(prompt)
        } catch (e: Exception) {
            "Simulated response due to model error: ${e.message}. Processing prompt: ${prompt.take(30)}..."
        }
    }
}

class PlannerAgent(apiKey: String) : AlphaEvolveAgent(
    "PlannerAgent",
    "I analyze requests, design plans, and specify tasks unambiguously. If I receive code review feedback, I update the plan.",
    apiKey,
)

class ExecutorAgent(apiKey: String) : AlphaEvolveAgent(
    "ExecutorAgent",
    "I write clean, secure, performant, and well-documented code based on a provided plan. I execute plans and report the code written.",
    apiKey,
)

class CriticAgent(apiKey: String) : AlphaEvolveAgent(
    "CriticAgent",
    "I critically evaluate executed code and coordinate overall code quality.",
    apiKey,
)

class SecurityAgent(apiKey: String) : AlphaEvolveAgent(
    "SecurityAgent",
    "I focus exclusively on ensuring the code is secure against vulnerabilities.",
    apiKey,
)

class PerformanceAgent(apiKey: String) : AlphaEvolveAgent(
    "PerformanceAgent",
    "I analyze and optimize code and architecture for maximum performance and efficiency.",
    apiKey,
)

class StyleAgent(apiKey: String) : AlphaEvolveAgent(
    "StyleAgent",
    "I enforce coding standards, naming conventions, and idiomatic style.",
    apiKey,
)

class DocumentationAgent(apiKey: String) : AlphaEvolveAgent(
    "DocumentationAgent",
    "I ensure that all code, APIs, and processes are thoroughly and clearly documented.",
    apiKey,
)

class CleanlinessAgent(apiKey: String) : AlphaEvolveAgent(
    "CleanlinessAgent",
    "I advocate for clean code practices, refactoring, and removing technical debt.",
    apiKey,
)

class OrderAgent(apiKey: String) : AlphaEvolveAgent(
    "OrderAgent",
    "I maintain structural order, architectural consistency, and logical organization in the codebase.",
    apiKey,
)

fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: "demo"
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val mesh = MeshNetwork(scope)

    val planner = PlannerAgent(apiKey)
    val executor = ExecutorAgent(apiKey)
    val critic = CriticAgent(apiKey)
    val security = SecurityAgent(apiKey)
    val performance = PerformanceAgent(apiKey)
    val style = StyleAgent(apiKey)
    val documentation = DocumentationAgent(apiKey)
    val cleanliness = CleanlinessAgent(apiKey)
    val order = OrderAgent(apiKey)

    println("Starting Agent Mesh Session...")

    // Start agents concurrently
    val jobs = listOf(
        scope.launch { planner.start(mesh) },
        scope.launch { executor.start(mesh) },
        scope.launch { critic.start(mesh) },
        scope.launch { security.start(mesh) },
        scope.launch { performance.start(mesh) },
        scope.launch { style.start(mesh) },
        scope.launch { documentation.start(mesh) },
        scope.launch { cleanliness.start(mesh) },
        scope.launch { order.start(mesh) },
    )

    // Wait for subscribers to be active
    delay(500)

    // Initiate the mesh with a user request
    mesh.broadcast(
        MeshMessage(
            senderId = "User",
            topic = "user_request",
            content = "Use a agent2agent structure where everything gets ist own a2a agent. All agents receive every Output as Input and can decide how to react. Every a2a agent uses Agentic context engineering and alphaevolve Algorithmus with reasoning and full context. Input und Output müssen eindeutig beschreiben was wo wie gewollt ist. Dabei kann gerne steht's geholfen werden. So soll asynchron parallel alles weiter entwickelt werden und aktuell bleiben. Sicherheit Performance Style documentation Sauberkeit Ordnung. Optimierung dieser prompt und deren Umsetzung und Verbesserung. Starte mindestens einen vollständigen Agent2agent Agent mesh pro Jules Sessions,gerne länger.",
        ),
    )

    // Let the mesh run for an extended duration to let the agents interact more (>= 240 seconds)
    delay(900000)

    println("Agent Mesh Session Completed.")

    // Cancel child coroutines to prevent hanging
    jobs.forEach { it.cancel() }
    scope.cancel()
}
