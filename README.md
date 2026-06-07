[![GitHub Actions Build Status](https://github.com/eclipse-lmos/arc/actions/workflows/gradle.yml/badge.svg?branch=main)](https://github.com/eclipse-lmos/arc/actions/workflows/gradle.yml)
[![GitHub Actions Publish Status](https://github.com/eclipse-lmos/arc/actions/workflows/gradle-publish.yml/badge.svg?branch=main)](https://github.com/eclipse-lmos/arc/actions/workflows/gradle-publish.yml)
[![Apache 2.0 License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.1-4baaaa.svg)](CODE_OF_CONDUCT.md)

# A2A-4K WIP

A2A-4K is a kotlin implementation of the Agent2Agent (A2A) protocol (https://github.com/google/A2A).

Check out the examples in the `examples` directory for a quick start.

The project is currently very much WIP and looking for contributors.

We have examples uing Arc (https://eclipse.dev/lmos/arc2)
and Langchain4j.

## Usage Examples

Don't forget to check out the Langchain4j and Arc examples [here](https://github.com/a2a-4k/a2a-4k/tree/main/examples/src/jvmMain/kotlin).

### Server-side: Implementing a TaskHandler

The `TaskHandler` interface is the core component for handling tasks in the A2A system. It's used to call an Agent of your choice. Here's a simple example of implementing a TaskHandler:

```kotlin
import io.github.a2a_4k.TaskHandler
import io.github.a2a_4k.models.*

// Custom TaskHandler that calls your Agent
class MyAgentTaskHandler : TaskHandler {
    override fun handle(task: Task): Task {
        // Extract the message from the task
        val message = task.history?.lastOrNull()

        // Call your Agent with the message
        val response = callYourAgent(message)

        // Create a response Artifact
        val responseArtifact = Artifact(
            name = "agent-response",
            parts = listOf(TextPart(text = response)),
        )

        // Update the task status
        val updatedStatus = TaskStatus(state = TaskState.COMPLETED)

        // Return the updated task
        return task.copy(status = updatedStatus,
            artifacts = listOf(responseArtifact)
        )
    }

    private fun callYourAgent(message: Message?): String {
        // Implement your Agent call here
        // This could be an API call to an LLM, a custom AI service, etc.
        return "Response from your Agent"
    }
}
```

### Setting up the A2A Server

```kotlin
import io.github.a2a_4k.*
import io.github.a2a_4k.models.*

// Create your TaskHandler
val taskHandler = MyAgentTaskHandler()

// Create a TaskManager with your TaskHandler
val taskManager = BasicTaskManager(taskHandler)

// Define your Agent's capabilities
val capabilities = Capabilities(
    streaming = true,
    pushNotifications = true,
    stateTransitionHistory = true
)

// Create an AgentCard for your Agent
val agentCard = AgentCard(
    name = "My Agent",
    description = "A custom A2A Agent",
    url = "https://example.com",
    version = "1.0.0",
    capabilities = capabilities,
    defaultInputModes = listOf("text"),
    defaultOutputModes = listOf("text"),
    skills = listOf(/* Define your Agent's skills here */)
)

// Create and start the A2A Server
val server = A2AServer(
    host = "0.0.0.0",
    port = 5000,
    endpoint = "/",
    agentCard = agentCard,
    taskManager = taskManager
)

// Start the server
server.start()
```

### Client-side: Interacting with an A2A Server

```kotlin
import io.github.a2a_4k.A2AClient
import io.github.a2a_4k.models.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create an A2A Client
    val client = A2AClient(baseUrl = "http://localhost:5000")

    try {
        // Get the Agent's metadata
        val agentCard = client.getAgentCard()
        println("Connected to Agent: ${agentCard.name}")

        // Create a message to send to the Agent
        val message = "Hello, Agent!".toUserMessage()

        // Send a task to the Agent
        val response = client.sendTask(
            taskId = "my-job-123",
            sessionId = "session-456",
            message = message
        )

        // Process the response
        if (response.result != null) {
            println("Agent response: $response")
        } else {
            println("Error: ${response.error?.message}")
        }
    } finally {
        // Close the client
        client.close()
    }
}
```

## Project Structure

The A2A-4K project is organized into several modules, each with a specific purpose:

### a2a4k-models

This module contains the data models and serialization logic for the A2A protocol. It defines:
- JSON-RPC request and response structures
- Task and message models
- Agent capabilities and metadata (AgentCard)
- Serialization/deserialization utilities

### a2a4k-server

This module provides the core server-side functionality for the A2A protocol. It includes:
- TaskManager interface and implementation (BasicTaskManager)
- TaskHandler interface for processing tasks
- TaskStorage interface and implementation for storing tasks
- Core logic for managing task lifecycle

### a2a4k-server-ktor

This module implements a Ktor-based server for the A2A protocol, building on the core functionality provided by a2a4k-server. It includes:
- A2AServer class for creating and managing an A2A server
- A2AModule for configuring Ktor routing
- HTTP endpoints for the A2A protocol

### a2a4k-client

This module provides client-side functionality for communicating with A2A servers. It includes:
- A2AClient class for sending requests to A2A servers
- Methods for all A2A operations (getTask, sendTask, cancelTask, etc.)
- Support for streaming responses
- Error handling

### a2a4k-storage-redis

This module provides a Redis implementation of the TaskStorage interface, allowing tasks and notification configurations to be stored in Redis.

## Using RedisTaskStorage

The A2A-4K project includes a Redis implementation of the TaskStorage interface, 
which allows you to store tasks and notification configurations in Redis instead of in-memory. 

This provides several benefits:

- **Persistence**: Tasks and configurations are stored in Redis and survive application restarts
- **Scalability**: Multiple instances of your application can share the same Redis database
- **Performance**: Redis offers high-performance storage with configurable persistence options

To use RedisTaskStorage, add the following dependency to your project:

```kotlin
// Gradle Kotlin DSL
implementation("org.a2a4k:a2a4k-storage-redis:$a2a4kVersion")
```

and then set the following environment variables / system properties:

- `A2A_STORAGE_REDIS_HOST`: The hostname or IP address of your Redis server (required)
- `A2A_STORAGE_REDIS_PORT`: The port number of your Redis server (optional, defaults to Redis standard port)
- `A2A_STORAGE_REDIS_USERNAME`: Username for Redis authentication (optional)
- `A2A_STORAGE_REDIS_PASSWORD`: Password for Redis authentication (optional)
- `A2A_STORAGE_REDIS_SSL`: Set to "true" to enable SSL connection (optional, defaults to false)
- `A2A_STORAGE_REDIS_TLS`: Set to "true" to enable TLS connection (optional, defaults to false)


This will automatically load and configure the RedisTaskStorage.

## Code of Conduct

This project has adopted the [Contributor Covenant](https://www.contributor-covenant.org/) in version 2.1 as our code of conduct. Please see the details in our [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). All contributors must abide by the code of conduct.

By participating in this project, you agree to abide by its [Code of Conduct](./CODE_OF_CONDUCT.md) at all times.

## Licensing
Copyright (c) 2025

Sourcecode licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0) (the "License"); you may not use this project except in compliance with the License.

This project follows the [REUSE standard for software licensing](https://reuse.software/).    
Each file contains copyright and license information, and license texts can be found in the [./LICENSES](./LICENSES) folder. For more information visit https://reuse.software/.   

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the LICENSE for the specific language governing permissions and limitations under the License.
