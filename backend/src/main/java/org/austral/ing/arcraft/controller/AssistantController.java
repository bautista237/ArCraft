package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.service.GeminiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * AI assistant page — players ask natural-language questions about the server's data and
 * Gemini answers using a live snapshot of the database. The page calls {@link #ask} via fetch.
 */
@Controller
@RequiredArgsConstructor
public class AssistantController {

    private final GeminiService geminiService;

    @GetMapping("/assistant")
    public String page(Model model) {
        model.addAttribute("aiEnabled", geminiService.isConfigured());
        return "assistant";
    }

    @PostMapping("/assistant/ask")
    @ResponseBody
    public Map<String, String> ask(@RequestParam String question) {
        return Map.of("answer", geminiService.ask(question));
    }
}
