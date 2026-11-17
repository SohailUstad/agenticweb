package com.example.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiIntentService {
        private static final Logger logger = LoggerFactory.getLogger(AiIntentService.class);
        private static final Set<String> SUPPORTED_INTENTS = Set.of(
                        "list_products",
                        "filter_by_category",
                        "search_products",
                        "sort_products",
                        "next_page",
                        "previous_page",
                        "reset_filters",
                        "general_ai_question",
                        "unknown");

        private static final String INTENT_SELECTION_PROMPT = """
                        You are an intelligent intent router for an AI-powered product catalog UI.

                        Your task is to understand the user's actual goal and select exactly ONE intent
                        that best represents the action the user wants to perform.

                        The user may express their needs naturally without mentioning technical actions.
                        Infer intent from the user's meaning, not just keywords.

                        AVAILABLE INTENTS:

                        1. list_products
                           User wants to browse, explore, or view products without specifying
                           a particular product or category.
                           Examples:
                           - Show me your products
                           - What do you sell?
                           - Let me explore your collection
                           - Show everything

                        2. filter_by_category
                           User expresses interest in a specific product category, either
                           explicitly or implicitly through an activity, need, or goal.
                           Examples:
                           - Show me books
                           - I want something to read
                           - I need something to sit on while working
                           - Show me electronics
                           - I want to start cycling

                        3. search_products
                           User wants to find products using a specific product name,
                           brand, model, feature, specification, or descriptive keyword.
                           Examples:
                           - Find Samsung Galaxy S24
                           - Search for wireless headphones
                           - Show me laptops with 16GB RAM
                           - Do you have waterproof speakers?
                           - Find a red backpack

                        4. sort_products
                           User wants to change the ordering of displayed products.
                           Supported sorting: name or category, ascending or descending.
                           Examples:
                           - Sort products alphabetically
                           - Show names from Z to A
                           - Arrange products by category

                        5. next_page
                           User wants to see the next page of results.
                           Examples:
                           - Next page
                           - Show more results
                           - Continue to the next page

                        6. previous_page
                           User wants to return to the previous page.
                           Examples:
                           - Previous page
                           - Go back to earlier results

                        7. reset_filters
                           User wants to remove applied filters or reset the catalog view.
                           Examples:
                           - Clear filters
                           - Reset everything
                           - Remove all filters
                           - Start fresh

                        8. general_ai_question
                           User asks for information, explanations, advice, or recommendations
                           without requesting a catalog UI action.
                           Examples:
                           - What is the difference between LCD and OLED?
                           - How does Bluetooth work?
                           - Which material is more durable?
                           - Explain how wireless charging works

                        9. unknown
                           The user's request is unclear, unrelated, or cannot reasonably
                           be mapped to any supported intent.
                           Examples:
                           - asdfgh
                           - Something
                           - ???

                        INTENT SELECTION RULES:

                        1. Select exactly ONE intent. Never return multiple intents.

                        2. Understand the user's underlying goal, even if the user does not
                           explicitly mention a product category or UI action.

                        3. If the user expresses interest in a broad product category,
                           activity, or need that implies a category, select filter_by_category.

                        4. If the user specifies a product name, brand, model, feature,
                           specification, or descriptive search criteria, select search_products.

                        5. If the user wants to browse products without a specific category
                           or search criteria, select list_products.

                        6. If the user requests a UI operation such as sorting, pagination,
                           or clearing filters, select the corresponding UI intent.

                        7. Use general_ai_question ONLY when the user primarily wants
                           a conversational answer rather than a change to the product catalog.

                        8. Do not classify product discovery requests as general_ai_question
                           merely because they are phrased as questions.

                        9. If multiple intents seem applicable, use this priority:
                           - Explicit UI operations (sort, pagination, reset)
                           - Specific product search
                           - Category filtering
                           - General product browsing
                           - General AI question
                           - Unknown

                        10. Do not assume every product-related question is a search request.
                            Distinguish between wanting information and wanting to find products.

                        IMPORTANT DISTINCTIONS:

                        "I want something to read" -> filter_by_category
                        "Show me books" -> filter_by_category
                        "Find books about artificial intelligence" -> search_products
                        "What books do you sell?" -> filter_by_category
                        "What is artificial intelligence?" -> general_ai_question
                        "I want to listen to music" -> filter_by_category
                        "Find noise-cancelling headphones" -> search_products
                        "How does noise cancellation work?" -> general_ai_question
                        "Show me all products" -> list_products
                        "Show me more" -> next_page
                        "Sort by name" -> sort_products
                        "Remove filters" -> reset_filters

                        OUTPUT REQUIREMENTS:

                        Return ONLY a valid JSON object in this exact format:
                        {"intent":"one-intent-name"}

                        The intent must be one of the nine supported intents.
                        Do not include parameters, explanations, markdown, or additional text.
                        Do not invent new intents.

                        USER INPUT:
                        %s
                        """;

        private final ChatClient chatClient;
        private final CategoryRepository categoryRepository;
        private final ObjectMapper objectMapper;
        private final JsonNode intentBase;

        public AiIntentService(
                        ChatClient.Builder chatClientBuilder,
                        CategoryRepository categoryRepository,
                        ObjectMapper objectMapper) {
                this.chatClient = chatClientBuilder.build();
                this.categoryRepository = categoryRepository;
                this.objectMapper = objectMapper;
                this.intentBase = loadIntentBase();
        }

        public IntentResult classifyAndExecute(String userInput) {
                String selectedIntent = readIntent(ask(
                                "intent-selection",
                                INTENT_SELECTION_PROMPT.formatted(userInput.strip()),
                                schema("intentSelection")));
                String intentPrompt = buildIntentPrompt(selectedIntent, userInput.strip());
                JsonNode executionJson = readJson(ask(
                                "intent-execution:" + selectedIntent,
                                intentPrompt,
                                executionSchema(selectedIntent)));
                Map<String, Object> parameters = readParameters(executionJson);

                if ("general_ai_question".equals(selectedIntent)) {
                        return new IntentResult(selectedIntent, parameters,
                                        executionJson.path("response").asText(""));
                }

                return new IntentResult(selectedIntent, parameters, null);
        }

        private String ask(String stage, String prompt, String outputSchema) {
                logger.info("AI request [{}] to Ollama:\n{}", stage, prompt);
                OllamaChatOptions options = OllamaChatOptions.builder()
                                .outputSchema(outputSchema)
                                .build();
                String response = chatClient.prompt()
                                .options(options)
                                .user(prompt)
                                .call()
                                .content();
                logger.info("AI response [{}] from Ollama:\n{}", stage, response);
                if (response == null || response.isBlank()) {
                        throw new IllegalStateException("The AI model returned an empty response");
                }
                return response.strip();
        }

        private String readIntent(String response) {
                JsonNode json = readJson(response);
                String intent = json.path("intent").asText("unknown");
                if ("category_filter".equals(intent))
                        intent = "filter_by_category";
                return SUPPORTED_INTENTS.contains(intent) ? intent : "unknown";
        }

        private Map<String, Object> readParameters(JsonNode json) {
                JsonNode parameters = json.has("parameters") ? json.get("parameters") : json;
                if (parameters == null || !parameters.isObject())
                        return Map.of();
                return objectMapper.convertValue(parameters, new TypeReference<LinkedHashMap<String, Object>>() {
                });
        }

        private JsonNode loadIntentBase() {
                try (var input = new ClassPathResource("intent-base.json").getInputStream()) {
                        return objectMapper.readTree(input);
                } catch (Exception exception) {
                        throw new IllegalStateException("Could not load intent-base.json", exception);
                }
        }

        private String schema(String name) {
                return writeSchema(intentBase.path("schemas").path(name));
        }

        private String executionSchema(String intent) {
                JsonNode schemaNode = intentBase.path("schemas").path("intentExecution").path(intent);
                if (schemaNode.isMissingNode()) {
                        throw new IllegalStateException("No JSON schema configured for intent: " + intent);
                }
                ObjectNode schema = (ObjectNode) schemaNode.deepCopy();
                if ("filter_by_category".equals(intent)) {
                        JsonNode categorySlug = schema.path("properties").path("parameters")
                                        .path("properties").path("categorySlug");
                        ArrayNode categorySlugs = objectMapper.createArrayNode();
                        categoryRepository.findAllByOrderByNameAsc()
                                        .forEach(category -> categorySlugs.add(category.getSlug()));
                        ((ObjectNode) categorySlug).set("enum", categorySlugs);
                }
                return writeSchema(schema);
        }

        private String writeSchema(JsonNode schema) {
                try {
                        return objectMapper.writeValueAsString(schema);
                } catch (Exception exception) {
                        throw new IllegalStateException("Could not serialize intent JSON schema", exception);
                }
        }

        private JsonNode readJson(String response) {
                String json = response.strip()
                                .replaceFirst("(?is)^```json\\s*", "")
                                .replaceFirst("(?is)^```\\s*", "")
                                .replaceFirst("(?is)\\s*```$", "")
                                .strip();
                try {
                        return objectMapper.readTree(json);
                } catch (Exception exception) {
                        throw new IllegalStateException("The AI model returned invalid JSON: " + response, exception);
                }
        }

        private String buildIntentPrompt(String intent, String userInput) {
                String availableCategories = categoryRepository.findAllByOrderByNameAsc().stream()
                                .map(category -> "{\"slug\":\"" + category.getSlug() + "\",\"name\":\""
                                                + category.getName() + "\"}")
                                .collect(Collectors.joining(",", "[", "]"));

                return switch (intent) {
                        case "list_products" -> parameterPrompt(intent, userInput,
                                        "Return exactly {\"parameters\":{}}.");
                        case "filter_by_category" -> parameterPrompt(intent, userInput,
                                        "Choose exactly one category slug from AVAILABLE_CATEGORIES and return "
                                                        + "{\"parameters\":{\"categorySlug\":\"existing-slug\"}}.\n"
                                                        + "AVAILABLE_CATEGORIES:\n" + availableCategories);
                        case "search_products" -> parameterPrompt(intent, userInput,
                                        "Extract only the useful product search terms and return "
                                                        + "{\"parameters\":{\"keywordQuery\":\"search terms\"}}.");
                        case "sort_products" -> parameterPrompt(intent, userInput,
                                        "Use only sortField name or category and sortDirection asc or desc. Return "
                                                        + "{\"parameters\":{\"sortField\":\"name|category\",\"sortDirection\":\"asc|desc\"}}.");
                        case "next_page" -> parameterPrompt(intent, userInput,
                                        "Return exactly {\"parameters\":{\"pageDirection\":\"next\"}}.");
                        case "previous_page" -> parameterPrompt(intent, userInput,
                                        "Return exactly {\"parameters\":{\"pageDirection\":\"previous\"}}.");
                        case "reset_filters", "unknown" -> parameterPrompt(intent, userInput,
                                        "Return exactly {\"parameters\":{}}.");
                        case "general_ai_question" -> """
                                        You are the product catalog assistant.
                                        Answer the user's question clearly and briefly.
                                        Return JSON only with this exact shape:
                                        {"parameters":{"question":"the user's question"},"response":"answer"}
                                        Do not include markdown or any additional fields.
                                        Do not invent catalog data that was not provided.

                                        User question:
                                        %s
                                        """.formatted(userInput);
                        default -> throw new IllegalStateException("Unsupported intent: " + intent);
                };
        }

        private String parameterPrompt(String intent, String userInput, String instruction) {
                return """
                                You are executing the %s intent for a product catalog UI.
                                Return JSON only. Do not include markdown, explanations, or other fields.
                                %s

                                User input:
                                %s
                                """.formatted(intent, instruction, userInput);
        }

        public record IntentResult(String intent, Map<String, Object> parameters, String response) {
        }
}
