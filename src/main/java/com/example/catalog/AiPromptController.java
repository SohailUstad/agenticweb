package com.example.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin (origins = "*")
public class AiPromptController {
    private final AiIntentService aiIntentService;

    public AiPromptController(AiIntentService aiIntentService) {
        this.aiIntentService = aiIntentService;
    }

    @PostMapping("/prompt")
    public PromptResponse prompt(@RequestBody PromptRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt must not be blank");
        }
        AiIntentService.IntentResult result = aiIntentService.classifyAndExecute(request.prompt());
        return new PromptResponse(result.intent(), result.parameters(), result.response());
    }

    public record PromptRequest(String prompt) {}
    public record PromptResponse(String intent, java.util.Map<String, Object> parameters, String response) {}
}
