package com.example.diagramr.util;

import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.FileFormat;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class PlantUmlValidator {
    public boolean isValid(String plantUmlCode) {
        if (plantUmlCode == null || !plantUmlCode.trim().startsWith("@startuml") || !plantUmlCode.trim().endsWith("@enduml")) {
            return false;
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            reader.generateImage(os, new FileFormatOption(FileFormat.PNG));
            return os.size() > 0;
        } catch (IOException e) {
             return false;
        } catch (Exception e) {
            return false;
        }
    }

    public String getErrorMessage(String plantUmlCode) {
        if (plantUmlCode == null || !plantUmlCode.trim().startsWith("@startuml") || !plantUmlCode.trim().endsWith("@enduml")) {
            return "PlantUML code must start with @startuml and end with @enduml";
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            reader.generateImage(os, new FileFormatOption(FileFormat.PNG));
            return null;
        } catch (IOException e) {
             return "Error processing diagram: " + e.getMessage();
        } catch (Exception e) {
            return "PlantUML Syntax Error: " + e.getMessage();
        }
    }
}