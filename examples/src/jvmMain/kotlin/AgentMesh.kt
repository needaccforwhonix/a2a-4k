import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class MeshMessage(
    val sender: String,
    val content: String,
)

class MeshNetwork {
    private val _messages = MutableSharedFlow<MeshMessage>(replay = 100)
    val messages = _messages.asSharedFlow()

    suspend fun broadcast(message: MeshMessage) {
        _messages.emit(message)
    }
}

abstract class MeshAgent(
    protected val name: String,
    protected val network: MeshNetwork,
    private val model: OpenAiChatModel,
) {
    abstract suspend fun processMessage(message: MeshMessage)

    suspend fun start() {
        network.messages.collect { message ->
            // Do not process own messages to avoid infinite loops immediately
            if (message.sender != name) {
                processMessage(message)
            }
        }
    }

    protected suspend fun broadcast(content: String) {
        network.broadcast(MeshMessage(name, content))
    }

    protected suspend fun chatWithModel(prompt: String): String {
        return withContext(Dispatchers.IO) {
            model.chat(prompt)
        }
    }
}

class PlannerAgent(
    network: MeshNetwork,
    model: OpenAiChatModel,
) : MeshAgent("Planner", network, model) {
    private var hasPlanned = false

    override suspend fun processMessage(message: MeshMessage) {
        if (message.sender == "User" && !hasPlanned) {
            hasPlanned = true
            println("[$name] Analyzing user request: ${message.content}")
            val prompt = """
                You are the Planner Agent in an AlphaEvolve Agent Mesh.
                Using Agentic Context Engineering, analyze the following user request and create a detailed plan outlining what, where, and how the task should be accomplished.
                Focus on security, performance, style, documentation, cleanliness, and order.

                User Request: ${message.content}

                Provide a structured plan.
            """.trimIndent()

            val plan = chatWithModel(prompt)
            println("[$name] Broadcasting Plan.")
            broadcast("PLAN:\n$plan")
        }
    }
}

class ExecutorAgent(
    network: MeshNetwork,
    model: OpenAiChatModel,
) : MeshAgent("Executor", network, model) {
    override suspend fun processMessage(message: MeshMessage) {
        if (message.sender == "Planner" && message.content.startsWith("PLAN:")) {
            println("[$name] Receiving Plan, executing task...")
            val prompt = """
                You are the Executor Agent in an AlphaEvolve Agent Mesh.
                Based on the following plan, generate the specific implementation details or code required.
                Ensure your output unambiguously describes what is being built, where it goes, and how it works. Focus on security, performance, style, documentation, cleanliness, and order.

                Plan:
                ${message.content}

                Provide the execution details.
            """.trimIndent()

            val execution = chatWithModel(prompt)
            println("[$name] Broadcasting Execution.")
            broadcast("EXECUTION:\n$execution")
        }
    }
}

class CriticAgent(
    network: MeshNetwork,
    model: OpenAiChatModel,
) : MeshAgent("Critic", network, model) {
    private var evaluationCount = 0
    private val maxEvaluations = 1

    override suspend fun processMessage(message: MeshMessage) {
        if (message.sender == "Executor" && message.content.startsWith("EXECUTION:") && evaluationCount < maxEvaluations) {
            evaluationCount++
            println("[$name] Critiquing Execution...")
            val prompt = """
                You are the Critic Agent in an AlphaEvolve Agent Mesh.
                Evaluate the following execution details based on the criteria: security, performance, style, documentation, cleanliness, and order.
                Provide constructive feedback and suggest refinements.

                Execution:
                ${message.content}

                Provide your critique and refinement suggestions.
            """.trimIndent()

            val critique = chatWithModel(prompt)
            println("[$name] Broadcasting Critique.")
            broadcast("CRITIQUE:\n$critique")
        } else if (message.sender == "Critic" && message.content.startsWith("CRITIQUE:")) {
            // If we wanted to allow the executor to refine it, we would listen to CRITIQUE in Executor.
        }
    }
}

fun main(): Unit = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY") ?: "demo"
    val builder = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName("gpt-4o-mini")

    if (apiKey == "demo") {
        builder.baseUrl("http://langchain4j.dev/demo/openai/v1")
    }

    val model = builder.build()

    val network = MeshNetwork()

    val planner = PlannerAgent(network, model)
    val executor = ExecutorAgent(network, model)
    val critic = CriticAgent(network, model)

    println("Starting Agent Mesh...")

    val plannerJob = launch { planner.start() }
    val executorJob = launch { executor.start() }
    val criticJob = launch { critic.start() }

    // Ensure subscribers are active before broadcasting
    delay(500)

    val initialRequest = "Create a simple Python script to calculate the Fibonacci sequence up to N terms, prioritizing security and performance."
    println("[User] Broadcasting initial request: $initialRequest")
    network.broadcast(MeshMessage("User", initialRequest))

    // Let the mesh run for a bit to process the messages
    delay(60000)

    println("Shutting down Agent Mesh...")
    plannerJob.cancel()
    executorJob.cancel()
    criticJob.cancel()
}
