package com.example.diagramr.repository;

import com.example.diagramr.model.Diagram;
import com.example.diagramr.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiagramRepository extends JpaRepository<Diagram, Long> {
    List<Diagram> findByUserOrderByUpdatedAtDesc(User user);
    List<Diagram> findByTitleContainingAndUser(String title, User user);
}