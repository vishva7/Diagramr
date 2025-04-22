package com.example.diagramr.service.diagram;

import com.example.diagramr.model.Diagram;
import com.example.diagramr.model.DiagramRequest;
import com.example.diagramr.model.DiagramResponse;
import com.example.diagramr.model.User;
import com.example.diagramr.model.DiagramVersion;
import com.example.diagramr.repository.DiagramRepository;
import com.example.diagramr.repository.DiagramVersionRepository;
import com.example.diagramr.service.llm.LlmService;
import com.example.diagramr.util.PlantUmlValidator;
import com.example.diagramr.exception.PlantUmlRenderingException;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.FileFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class PlantUmlDiagramService implements DiagramService {

    private final LlmService llmService;
    private final DiagramRepository diagramRepository;
    private final DiagramVersionRepository diagramVersionRepository;
    private final PlantUmlValidator plantUmlValidator;
    private static final Logger logger = LoggerFactory.getLogger(PlantUmlDiagramService.class);

    public PlantUmlDiagramService(
            LlmService llmService,
            DiagramRepository diagramRepository,
            DiagramVersionRepository diagramVersionRepository,
            PlantUmlValidator plantUmlValidator) {
        this.llmService = llmService;
        this.diagramRepository = diagramRepository;
        this.diagramVersionRepository = diagramVersionRepository;
        this.plantUmlValidator = plantUmlValidator;
    }

    @Override
    public DiagramResponse generateDiagram(DiagramRequest request) {
        String plantUmlCode = null;
        try {
            plantUmlCode = llmService.generatePlantUml(request.getPrompt());
            logger.info("Generated PlantUML code:\n{}", plantUmlCode);

            boolean isValidSyntax = plantUmlValidator.isValid(plantUmlCode);
            String validationErrorMessage = null;
            if (!isValidSyntax) {
                validationErrorMessage = plantUmlValidator.getErrorMessage(plantUmlCode);
                logger.warn("Generated PlantUML code failed initial validation: {}", validationErrorMessage);
            }

            logger.info("Attempting to render generated PlantUML code as SVG.");
            String svgImage = renderDiagramAsSvg(plantUmlCode);
            logger.info("Successfully rendered SVG.");

            return DiagramResponse.builder()
                    .plantUmlCode(plantUmlCode)
                    .svgImage(svgImage)
                    .isValid(true)
                    .build();
        } catch (PlantUmlRenderingException e) {
            logger.error("PlantUML rendering failed for generated code: {}", e.getMessage());
            return DiagramResponse.builder()
                    .plantUmlCode(plantUmlCode)
                    .isValid(false)
                    .errorMessage("PlantUML Syntax Error: " + e.getMessage() + ". Please refine your prompt.")
                    .build();
        } catch (Exception e) {
            logger.error("Error generating diagram", e);
            return DiagramResponse.builder()
                    .plantUmlCode(plantUmlCode)
                    .isValid(false)
                    .errorMessage("Error generating diagram: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public DiagramResponse refineDiagram(DiagramRequest request) {
        String refinedCode = null;
        try {
            if (request.getExistingCode() == null || request.getExistingCode().isEmpty()) {
                logger.warn("Refinement requested with no existing code.");
                return DiagramResponse.builder()
                        .isValid(false)
                        .errorMessage("No existing code provided for refinement")
                        .build();
            }
            logger.info("Refining PlantUML code with feedback: {}", request.getFeedback());
            logger.debug("Existing PlantUML code for refinement:\n{}", request.getExistingCode());

            refinedCode = llmService.refinePlantUml(request.getExistingCode(), request.getFeedback());
            logger.info("Refined PlantUML code:\n{}", refinedCode);

            boolean isValidSyntax = plantUmlValidator.isValid(refinedCode);
            String validationErrorMessage = null;
            if (!isValidSyntax) {
                validationErrorMessage = plantUmlValidator.getErrorMessage(refinedCode);
                logger.warn("Refined PlantUML code failed initial validation: {}", validationErrorMessage);
            }

            logger.info("Attempting to render refined PlantUML code as SVG.");
            String svgImage = renderDiagramAsSvg(refinedCode);
            logger.info("Successfully rendered refined SVG.");

            return DiagramResponse.builder()
                    .plantUmlCode(refinedCode)
                    .svgImage(svgImage)
                    .isValid(true)
                    .build();
        } catch (PlantUmlRenderingException e) {
            logger.error("PlantUML rendering failed for refined code: {}", e.getMessage());
            return DiagramResponse.builder()
                    .plantUmlCode(refinedCode)
                    .isValid(false)
                    .errorMessage(
                            "PlantUML Syntax Error: " + e.getMessage() + ". Please refine your feedback or the code.")
                    .build();
        } catch (Exception e) {
            logger.error("Error refining diagram", e);
            return DiagramResponse.builder()
                    .plantUmlCode(refinedCode)
                    .isValid(false)
                    .errorMessage("Error refining diagram: " + e.getMessage())
                    .build();
        }
    }

    @Override
    @Transactional
    public Diagram saveDiagram(DiagramRequest request, String plantUmlCode, User user) {
        Diagram diagram = new Diagram();
        diagram.setTitle(request.getTitle());
        diagram.setDescription(request.getDescription());
        diagram.setPlantUmlCode(plantUmlCode);
        diagram.setUser(user);

        Diagram savedDiagram = diagramRepository.save(diagram);

        DiagramVersion version = new DiagramVersion();
        version.setPlantUmlCode(plantUmlCode);
        version.setVersionNumber(1);
        version.setVersionLabel("Initial version");
        version.setDiagram(savedDiagram);

        savedDiagram.addVersion(version);

        logger.info("Saving diagram titled '{}' for user {} with initial version", request.getTitle(),
                user.getUsername());
        return diagramRepository.save(savedDiagram);
    }

    @Override
    @Transactional
    public DiagramVersion saveVersion(Diagram diagram, String plantUmlCode, String versionLabel, String versionNotes) {
        DiagramVersion version = new DiagramVersion();
        version.setPlantUmlCode(plantUmlCode);
        version.setVersionLabel(versionLabel);
        version.setVersionNotes(versionNotes);

        diagram.setPlantUmlCode(plantUmlCode);

        diagram.addVersion(version);

        diagramRepository.save(diagram);

        logger.info("Created new version {} for diagram id {} with label: {}",
                version.getVersionNumber(), diagram.getId(), versionLabel);

        return version;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiagramVersion> getDiagramVersions(Long diagramId) {
        Optional<Diagram> diagramOpt = diagramRepository.findById(diagramId);
        if (diagramOpt.isEmpty()) {
            logger.warn("Attempt to get versions for non-existent diagram id {}", diagramId);
            return List.of();
        }

        Diagram diagram = diagramOpt.get();
        logger.info("Fetching versions for diagram id {}", diagramId);
        return diagramVersionRepository.findByDiagramOrderByVersionNumberDesc(diagram);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiagramVersion> getDiagramVersionById(Long versionId) {
        logger.info("Fetching diagram version by id {}", versionId);
        return diagramVersionRepository.findById(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiagramVersion> getDiagramVersionByNumber(Long diagramId, Integer versionNumber) {
        Optional<Diagram> diagramOpt = diagramRepository.findById(diagramId);
        if (diagramOpt.isEmpty()) {
            logger.warn("Attempt to get version {} for non-existent diagram id {}", versionNumber, diagramId);
            return Optional.empty();
        }

        Diagram diagram = diagramOpt.get();
        logger.info("Fetching version {} for diagram id {}", versionNumber, diagramId);
        return diagramVersionRepository.findByDiagramAndVersionNumber(diagram, versionNumber);
    }

    @Override
    @Transactional
    public boolean switchDiagramVersion(Long diagramId, Integer versionNumber, User user) {
        Optional<Diagram> diagramOpt = diagramRepository.findById(diagramId);
        if (diagramOpt.isEmpty()) {
            logger.warn("Attempt to switch version for non-existent diagram id {}", diagramId);
            return false;
        }

        Diagram diagram = diagramOpt.get();

        if (!diagram.getUser().getId().equals(user.getId())) {
            logger.warn("User {} attempted to switch version for diagram {} owned by user {}",
                    user.getUsername(), diagramId, diagram.getUser().getUsername());
            return false;
        }

        Optional<DiagramVersion> versionOpt = diagramVersionRepository.findByDiagramAndVersionNumber(diagram,
                versionNumber);
        if (versionOpt.isEmpty()) {
            logger.warn("Attempt to switch to non-existent version {} for diagram id {}", versionNumber, diagramId);
            return false;
        }

        DiagramVersion version = versionOpt.get();

        diagram.setPlantUmlCode(version.getPlantUmlCode());
        diagram.setCurrentVersionNumber(versionNumber);
        diagramRepository.save(diagram);

        logger.info("Switched diagram id {} to version {}", diagramId, versionNumber);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteDiagramVersion(Long versionId, User user) {
        Optional<DiagramVersion> versionOpt = diagramVersionRepository.findById(versionId);

        if (versionOpt.isEmpty()) {
            logger.warn("Attempt to delete non-existent diagram version id {}", versionId);
            return false;
        }

        DiagramVersion version = versionOpt.get();
        Diagram diagram = version.getDiagram();

        if (!diagram.getUser().getId().equals(user.getId())) {
            logger.warn("User {} attempted to delete version {} for diagram {} owned by user {}",
                    user.getUsername(), versionId, diagram.getId(), diagram.getUser().getUsername());
            return false;
        }

        long versionCount = diagramVersionRepository.countByDiagram(diagram);
        if (versionCount <= 1) {
            logger.warn("Attempt to delete the only version for diagram id {}", diagram.getId());
            return false;
        }

        if (diagram.getCurrentVersionNumber().equals(version.getVersionNumber())) {
            Optional<DiagramVersion> newCurrentVersionOpt = diagramVersionRepository
                    .findFirstByDiagramAndIdNotOrderByVersionNumberDesc(
                            diagram, version.getId());

            if (newCurrentVersionOpt.isPresent()) {
                DiagramVersion newCurrentVersion = newCurrentVersionOpt.get();
                diagram.setCurrentVersionNumber(newCurrentVersion.getVersionNumber());
                diagram.setPlantUmlCode(newCurrentVersion.getPlantUmlCode());
                diagramRepository.save(diagram);
            }
        }

        diagramVersionRepository.delete(version);
        logger.info("Deleted version {} for diagram id {}", version.getVersionNumber(), diagram.getId());

        return true;
    }

    @Override
    public List<Diagram> getUserDiagrams(User user) {
        logger.info("Fetching diagrams for user {}", user.getUsername());
        return diagramRepository.findByUserOrderByUpdatedAtDesc(user);
    }

    @Override
    public Optional<Diagram> getDiagramById(Long id) {
        logger.info("Fetching diagram by ID {}", id);
        return diagramRepository.findById(id);
    }

    @Override
    public void deleteDiagram(Long id) {
        logger.info("Deleting diagram by ID {}", id);
        diagramRepository.deleteById(id);
    }

    @Override
    public String renderDiagramAsSvg(String plantUmlCode) throws PlantUmlRenderingException {
        if (plantUmlCode == null || !plantUmlCode.trim().startsWith("@startuml")
                || !plantUmlCode.trim().endsWith("@enduml")) {
            logger.error("Invalid PlantUML code passed to renderDiagramAsSvg (missing @startuml/@enduml):\n{}",
                    plantUmlCode);
            throw new IllegalArgumentException(
                    "Invalid PlantUML code: Must start with @startuml and end with @enduml.");
        }
        logger.debug("Rendering PlantUML to SVG:\n{}", plantUmlCode);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            String description = reader.generateImage(outputStream, new FileFormatOption(FileFormat.SVG));
            logger.info("PlantUML generateImage description: {}", description);

            if (description == null || description.contains("Error")) {
                logger.error("PlantUML reported an error during rendering: {}", description);
                throw new PlantUmlRenderingException(
                        "PlantUML syntax error: " + (description != null ? description : "Unknown error"));
            }

            String svg = new String(outputStream.toByteArray(), StandardCharsets.UTF_8).trim(); // Trim whitespace
            if (!(svg.startsWith("<svg") || (svg.startsWith("<?xml") && svg.contains("<svg")))) {
                logger.error("PlantUML output did not seem to be valid SVG. Output:\n{}", svg);
                throw new PlantUmlRenderingException(
                        "Failed to render valid SVG content. PlantUML output might be incomplete or invalid.");
            }
            logger.debug("Rendered SVG content (first 100 chars): {}", svg.substring(0, Math.min(svg.length(), 100)));
            return svg;
        } catch (IOException e) {
            logger.error("IO error while rendering diagram as SVG", e);
            throw new PlantUmlRenderingException("IO error while rendering diagram as SVG", e);
        } catch (PlantUmlRenderingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during PlantUML rendering", e);
            throw new PlantUmlRenderingException("Unexpected error rendering diagram as SVG: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] renderDiagramAsPng(String plantUmlCode) throws PlantUmlRenderingException {
        if (plantUmlCode == null || !plantUmlCode.trim().startsWith("@startuml")
                || !plantUmlCode.trim().endsWith("@enduml")) {
            logger.error("Invalid PlantUML code passed to renderDiagramAsPng (missing @startuml/@enduml):\n{}",
                    plantUmlCode);
            throw new IllegalArgumentException(
                    "Invalid PlantUML code: Must start with @startuml and end with @enduml.");
        }
        logger.debug("Rendering PlantUML to PNG:\n{}", plantUmlCode);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            SourceStringReader reader = new SourceStringReader(plantUmlCode);
            String description = reader.generateImage(outputStream, new FileFormatOption(FileFormat.PNG));
            logger.info("PlantUML generateImage (PNG) description: {}", description);

            if (description == null || description.contains("Error")) {
                logger.error("PlantUML reported an error during PNG rendering: {}", description);
                throw new PlantUmlRenderingException(
                        "PlantUML syntax error: " + (description != null ? description : "Unknown error"));
            }

            byte[] pngData = outputStream.toByteArray();
            if (pngData == null || pngData.length == 0) {
                logger.error("PlantUML PNG output was empty.");
                throw new PlantUmlRenderingException("Failed to render PNG content. PlantUML output was empty.");
            }
            logger.debug("Rendered PNG data length: {}", pngData.length);
            return pngData;
        } catch (IOException e) {
            logger.error("IO error while rendering diagram as PNG", e);
            throw new PlantUmlRenderingException("IO error while rendering diagram as PNG", e);
        } catch (PlantUmlRenderingException e) {
            throw e; // Re-throw specific exception
        } catch (Exception e) {
            logger.error("Unexpected error during PlantUML PNG rendering", e);
            throw new PlantUmlRenderingException("Unexpected error rendering diagram as PNG: " + e.getMessage(), e);
        }
    }
}