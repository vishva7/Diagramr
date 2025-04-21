package com.example.diagramr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "diagrams")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Diagram {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String plantUmlCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(nullable = false)
    private Integer currentVersionNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @OneToMany(mappedBy = "diagram", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiagramVersion> versions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addVersion(DiagramVersion version) {
        versions.add(version);
        version.setDiagram(this);
        version.setVersionNumber(versions.size());
        this.currentVersionNumber = versions.size();
    }
    
    public DiagramVersion getCurrentVersion() {
        return versions.stream()
            .filter(v -> v.getVersionNumber().equals(this.currentVersionNumber))
            .findFirst()
            .orElse(null);
    }
}