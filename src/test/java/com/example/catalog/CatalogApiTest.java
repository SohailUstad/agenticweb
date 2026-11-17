package com.example.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
class CatalogApiTest {
    @Autowired MockMvc mvc;

    @Test
    void seedsCatalogAndPaginates() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
        mvc.perform(get("/api/products").param("size", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(300))
                .andExpect(jsonPath("$.content.length()").value(6));
    }

    @Test
    void filtersByCategoryAndSearchesKeywords() throws Exception {
        mvc.perform(get("/api/products").param("category", "coffee-gear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].category.slug").value("coffee-gear"));
        mvc.perform(get("/api/products").param("q", "wifi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/products").param("category", "coffee-gear")
                        .param("q", "grinder").param("sort", "category").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Roast Burr Grinder"));
    }

    @Test
    void sortsAndRejectsInvalidPagination() throws Exception {
        mvc.perform(get("/api/products").param("sort", "name").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Woven Wall Hanging"));
        mvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servesPageAndBundledJquery() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/catalog.js")));
        mvc.perform(get("/webjars/jquery/3.7.1/jquery.min.js"))
                .andExpect(status().isOk());
        mvc.perform(get("/js/command.js"))
                .andExpect(status().isOk());
        mvc.perform(get("/css/command-dock.css"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsBlankAiPrompt() throws Exception {
        mvc.perform(post("/api/ai/prompt").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_TEST", matches = "true")
    void returnsResponseFromLocalOllama() throws Exception {
        mvc.perform(post("/api/ai/prompt").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Reply with the word catalog.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").isNotEmpty())
                .andExpect(jsonPath("$.parameters").exists());
    }
}
