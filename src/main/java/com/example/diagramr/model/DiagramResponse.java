package com.example.diagramr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramResponse {
    private String plantUmlCode;
    private String svgImage;
    private boolean isValid;
    private String errorMessage;
}