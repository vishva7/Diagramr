package com.example.diagramr.service.llm;

public interface LlmService {
    String generatePlantUml(String prompt);

    String refinePlantUml(String existingCode, String feedback);
}