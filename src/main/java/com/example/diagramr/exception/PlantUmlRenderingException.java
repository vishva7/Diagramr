package com.example.diagramr.exception;

public class PlantUmlRenderingException extends Exception {
    public PlantUmlRenderingException(String message) {
        super(message);
    }

    public PlantUmlRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}