package com.example.diagramr.repository;

import com.example.diagramr.model.Diagram;
import com.example.diagramr.model.DiagramVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DiagramVersionRepository extends JpaRepository<DiagramVersion, Long> {
    List<DiagramVersion> findByDiagramOrderByVersionNumberDesc(Diagram diagram);

    Optional<DiagramVersion> findByDiagramAndVersionNumber(Diagram diagram, Integer versionNumber);

    long countByDiagram(Diagram diagram);

    Optional<DiagramVersion> findFirstByDiagramAndIdNotOrderByVersionNumberDesc(Diagram diagram, Long id);
}