package com.example.diagramr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagram_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String plantUmlCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private String versionLabel;

    @Column(nullable = true, length = 1000)
    private String versionNotes;

    @Column(nullable = false)
    private Integer versionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false)
    private Diagram diagram;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}