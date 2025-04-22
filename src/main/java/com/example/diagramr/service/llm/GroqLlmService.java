package com.example.diagramr.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroqLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmService.class);

    private final ChatModel chatModel;
    private final String SYSTEM_PROMPT_TEMPLATE = """
            You are an expert at creating PlantUML diagrams.
            Your task is to convert natural language descriptions into valid PlantUML code.

            Rules:
            1. Always begin with @startuml and end with @enduml. Do not include any other uml tags.
            2. Use appropriate PlantUML syntax for the type of diagram
            3. Include meaningful relationships between elements
            4. Add comments to explain complex parts
            5. Focus only on generating valid PlantUML code which does not have any syntax errors
            6. Return ONLY the PlantUML code without any explanations or markdown formatting

            Examples:
            - Class Diagram:
            @startuml
            class User {
            -id: Long
            -username: String
            -email: String
            +login(): boolean
            +logout(): void
            }

            class Order {
            -orderId: String
            -orderDate: Date
            -total: Double
            +calculateTotal(): Double
            +cancel(): boolean
            }

            class Product {
            -productId: String
            -name: String
            -price: Double
            +isAvailable(): boolean
            }

            User "1" -- "many" Order: places >
            Order "many" -- "many" Product: contains >
            @enduml

            - Activity Diagram:
            @startuml
            start
            :Customer places order;
            if (Items in stock?) then (yes)
            :Process payment;
            if (Payment successful?) then (yes)
                :Ship order;
                :Send confirmation email;
            else (no)
                :Notify payment failure;
            endif
            else (no)
            :Add to backorder;
            :Notify customer;
            endif
            :Close order process;
            stop
            @enduml

            - Use Case Diagram:
            @startuml
            left to right direction
            actor Customer
            actor Administrator

            rectangle "E-commerce System" {
            Customer -- (Browse Products)
            Customer -- (Add to Cart)
            Customer -- (Checkout)
            (Manage Inventory) -- Administrator
            (Process Returns) -- Administrator

            (Checkout) .> (Process Payment) : includes
            (Process Returns) .> (Update Inventory) : includes
            }
            @enduml

            - Sequence Diagram:
            @startuml
            actor User
            participant "Web Browser" as Browser
            participant "Web Server" as Server
            participant "Database" as DB

            User -> Browser: Enter login credentials
            Browser -> Server: POST /login
            Server -> DB: Validate credentials
            DB --> Server: Return user data
            alt successful login
                Server --> Browser: Return success + token
                Browser --> User: Show dashboard
            else failed login
                Server --> Browser: Return error
                Browser --> User: Show error message
            end
            @enduml

            - State Diagram:
            @startuml
            title Document Approval Process

            [*] --> Draft : Create new document
            Draft --> Review : Submit for review
            Review --> Revision : Request changes
            Revision --> Review : Resubmit
            Review --> Approved : Accept document
            Approved --> Published : Publish
            Published --> [*]

            state Review {
            [*] --> TechnicalReview
            TechnicalReview --> ContentReview
            ContentReview --> [*]
            }

            note right of Draft : Author works on document
            note right of Revision : Author makes requested changes
            note right of Published : Document available to public
            @enduml

            Diagram type context: %s
            """;

    public GroqLlmService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generatePlantUml(String prompt) {
        String diagramType = determineDiagramType(prompt);
        log.info("Determined diagram type for generation: {}", diagramType);

        Message systemMessage = getSystemMessage(diagramType);

        Message userMessage = new UserMessage(prompt);
        log.info("User prompt for generation: {}", prompt);

        Prompt aiPrompt = new Prompt(List.of(systemMessage, userMessage));

        String response = chatModel.call(aiPrompt).getResult().getOutput().getText();
        log.info("LLM response for generation:\n{}", response);
        return response;
    }

    @Override
    public String refinePlantUml(String existingCode, String feedback) {
        String diagramType = determineDiagramTypeFromCode(existingCode);
        log.info("Determined diagram type for refinement: {}", diagramType);

        Message systemMessage = getSystemMessage(diagramType);

        String userPrompt = String.format(
                "Refine this PlantUML diagram based on the following feedback:\n\nFeedback: %s\n\nExisting code:\n```\n%s\n```",
                feedback,
                existingCode);
        log.info("User prompt for refinement: {}", userPrompt);

        Message userMessage = new UserMessage(userPrompt);

        Prompt aiPrompt = new Prompt(List.of(systemMessage, userMessage));

        String response = chatModel.call(aiPrompt).getResult().getOutput().getText();
        log.info("LLM response for refinement:\n{}", response);
        return response;
    }

    private Message getSystemMessage(String diagramType) {
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, diagramType);
        log.info("System prompt: {}", systemPrompt);
        return new SystemMessage(systemPrompt);
    }

    private String determineDiagramType(String prompt) {
        prompt = prompt.toLowerCase();

        if (prompt.contains("class") || prompt.contains("inheritance") || prompt.contains("attributes")
                || prompt.contains("methods")) {
            return "Class Diagram";
        } else if (prompt.contains("sequence") || prompt.contains("message") || prompt.contains("actor")) {
            return "Sequence Diagram";
        } else if (prompt.contains("use case") || prompt.contains("actor") || prompt.contains("user")) {
            return "Use Case Diagram";
        } else if (prompt.contains("activity") || prompt.contains("workflow") || prompt.contains("process")) {
            return "Activity Diagram";
        } else if (prompt.contains("component") || prompt.contains("interface") || prompt.contains("service")) {
            return "Component Diagram";
        } else if (prompt.contains("state") || prompt.contains("transition")) {
            return "State Diagram";
        } else {
            return "General Diagram";
        }
    }

    private String determineDiagramTypeFromCode(String code) {
        code = code.toLowerCase();

        if (code.contains("class ")) {
            return "Class Diagram";
        } else if (code.contains("actor ") || code.contains("participant ")) {
            return "Sequence Diagram";
        } else if (code.contains("usecase")) {
            return "Use Case Diagram";
        } else if (code.contains("start") && code.contains("end") && (code.contains("if") || code.contains("while"))) {
            return "Activity Diagram";
        } else if (code.contains("component")) {
            return "Component Diagram";
        } else if (code.contains("state")) {
            return "State Diagram";
        } else {
            return "General Diagram";
        }
    }
}