package com.example.diagramr.service.diagram;

import com.example.diagramr.model.Diagram;
import com.example.diagramr.model.DiagramRequest;
import com.example.diagramr.model.DiagramResponse;
import com.example.diagramr.model.User;
import com.example.diagramr.model.DiagramVersion;
import com.example.diagramr.exception.PlantUmlRenderingException;

import java.util.List;
import java.util.Optional;

public interface DiagramService {
    DiagramResponse generateDiagram(DiagramRequest request);

    DiagramResponse refineDiagram(DiagramRequest request);

    Diagram saveDiagram(DiagramRequest request, String plantUmlCode, User user);

    DiagramVersion saveVersion(Diagram diagram, String plantUmlCode, String versionLabel, String versionNotes);

    List<DiagramVersion> getDiagramVersions(Long diagramId);

    Optional<DiagramVersion> getDiagramVersionById(Long versionId);

    Optional<DiagramVersion> getDiagramVersionByNumber(Long diagramId, Integer versionNumber);

    boolean switchDiagramVersion(Long diagramId, Integer versionNumber, User user);

    boolean deleteDiagramVersion(Long versionId, User user);

    List<Diagram> getUserDiagrams(User user);

    Optional<Diagram> getDiagramById(Long id);

    void deleteDiagram(Long id);

    String renderDiagramAsSvg(String plantUmlCode) throws PlantUmlRenderingException;

    byte[] renderDiagramAsPng(String plantUmlCode) throws PlantUmlRenderingException;
}