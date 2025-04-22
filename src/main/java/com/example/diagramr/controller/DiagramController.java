package com.example.diagramr.controller;

import com.example.diagramr.model.Diagram;
import com.example.diagramr.model.DiagramRequest;
import com.example.diagramr.model.DiagramResponse;
import com.example.diagramr.model.User;
import com.example.diagramr.model.DiagramVersion;
import com.example.diagramr.service.diagram.DiagramService;
import com.example.diagramr.service.user.UserService;
import com.example.diagramr.exception.PlantUmlRenderingException;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class DiagramController {

    private final DiagramService diagramService;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(DiagramController.class);

    public DiagramController(DiagramService diagramService, UserService userService) {
        this.diagramService = diagramService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user, BindingResult result, Model model) { // Added
        if (result.hasErrors()) {
            logger.warn("Validation errors during registration for user: {}", user.getUsername());
            return "register";
        }

        try {
            logger.info("Attempting registration for user: {}", user.getUsername());
            userService.registerNewUser(user);
            logger.info("Registration successful for user: {}", user.getUsername());
            return "redirect:/login?registered";
        } catch (RuntimeException e) {
            logger.error("Registration failed for user: {}: {}", user.getUsername(), e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Username already exists")) {
                result.rejectValue("username", "error.user", e.getMessage());
            } else if (e.getMessage() != null && e.getMessage().contains("Email already exists")) {
                result.rejectValue("email", "error.user", e.getMessage());
            } else {
                result.reject("error.user", "An unexpected error occurred during registration. Please try again.");
                logger.error("Unexpected error during registration", e);
            }
            return "register";
        }
    }

    @GetMapping("/diagrams")
    public String listDiagrams(Model model, Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Diagram> diagrams = diagramService.getUserDiagrams(user);
        model.addAttribute("diagrams", diagrams);

        return "diagrams/list";
    }

    @GetMapping("/diagrams/new")
    public String newDiagram(Model model) {
        model.addAttribute("diagramRequest", new DiagramRequest());
        return "diagrams/new";
    }

    @PostMapping("/diagrams/generate")
    @ResponseBody
    public ResponseEntity<DiagramResponse> generateDiagram(@RequestBody DiagramRequest request) {
        DiagramResponse response = diagramService.generateDiagram(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/diagrams/refine")
    @ResponseBody
    public ResponseEntity<DiagramResponse> refineDiagram(@RequestBody DiagramRequest request) {
        DiagramResponse response = diagramService.refineDiagram(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/diagrams/save")
    public String saveDiagram(
            @Valid @ModelAttribute DiagramRequest request,
            @RequestParam String plantUmlCode,
            Authentication authentication) {

        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        diagramService.saveDiagram(request, plantUmlCode, user);

        return "redirect:/diagrams";
    }

    @GetMapping("/diagrams/{id}")
    public String viewDiagram(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Diagram> diagramOpt = diagramService.getDiagramById(id);

        if (diagramOpt.isEmpty() || !diagramOpt.get().getUser().getId().equals(user.getId())) {
            logger.warn("Attempt to access diagram id {} by unauthorized user {}", id, user.getUsername());
            return "redirect:/diagrams";
        }

        Diagram diagram = diagramOpt.get();
        model.addAttribute("diagram", diagram);

        // Get diagram versions
        List<DiagramVersion> versions = diagramService.getDiagramVersions(id);
        model.addAttribute("versions", versions);

        String svgImage = null;
        String svgRenderingError = null;

        try {
            svgImage = diagramService.renderDiagramAsSvg(diagram.getPlantUmlCode());
        } catch (PlantUmlRenderingException e) {
            logger.error("Failed to render SVG for diagram id {}: {}", id, e.getMessage());
            svgRenderingError = "Could not render diagram preview: " + e.getMessage();
            svgImage = "<svg width='100%' height='100'><text x='10' y='50' fill='red'>Error rendering diagram.</text></svg>";
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PlantUML code structure for diagram id {}: {}", id, e.getMessage());
            svgRenderingError = "Invalid PlantUML code structure: " + e.getMessage();
            svgImage = "<svg width='100%' height='100'><text x='10' y='50' fill='red'>Invalid code structure.</text></svg>";
        }

        model.addAttribute("svgImage", svgImage);
        if (svgRenderingError != null) {
            model.addAttribute("svgRenderingError", svgRenderingError);
        }

        DiagramRequest refinementRequest = new DiagramRequest();
        refinementRequest.setTitle(diagram.getTitle());
        refinementRequest.setDescription(diagram.getDescription());
        refinementRequest.setExistingCode(diagram.getPlantUmlCode());
        model.addAttribute("refinementRequest", refinementRequest);

        return "diagrams/view";
    }

    @PostMapping("/diagrams/{id}/save-version")
    public String saveVersion(
            @PathVariable Long id,
            @RequestParam String plantUmlCode,
            @RequestParam(required = false) String versionLabel,
            @RequestParam(required = false) String versionNotes,
            Authentication authentication) {

        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Diagram> diagramOpt = diagramService.getDiagramById(id);

        if (diagramOpt.isEmpty() || !diagramOpt.get().getUser().getId().equals(user.getId())) {
            logger.warn("Attempt to save version for diagram id {} by unauthorized user {}", id, user.getUsername());
            return "redirect:/diagrams";
        }

        Diagram diagram = diagramOpt.get();

        diagramService.saveVersion(diagram, plantUmlCode, versionLabel, versionNotes);

        return "redirect:/diagrams/" + id;
    }

    @PostMapping("/diagrams/{id}/switch-version")
    public String switchVersion(
            @PathVariable Long id,
            @RequestParam Integer versionNumber,
            Authentication authentication) {

        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean success = diagramService.switchDiagramVersion(id, versionNumber, user);

        if (!success) {
            logger.warn("Failed to switch version {} for diagram id {}", versionNumber, id);
        }

        return "redirect:/diagrams/" + id;
    }

    @GetMapping("/diagrams/{id}/versions/{versionNumber}")
    public String viewVersion(
            @PathVariable Long id,
            @PathVariable Integer versionNumber,
            Model model,
            Authentication authentication) {

        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Diagram> diagramOpt = diagramService.getDiagramById(id);

        if (diagramOpt.isEmpty() || !diagramOpt.get().getUser().getId().equals(user.getId())) {
            logger.warn("Attempt to view version for diagram id {} by unauthorized user {}", id, user.getUsername());
            return "redirect:/diagrams";
        }

        Diagram diagram = diagramOpt.get();
        model.addAttribute("diagram", diagram);

        // Get all versions for the version history panel
        List<DiagramVersion> versions = diagramService.getDiagramVersions(id);
        model.addAttribute("versions", versions);

        // Get the specific version requested
        Optional<DiagramVersion> versionOpt = diagramService.getDiagramVersionByNumber(id, versionNumber);

        if (versionOpt.isEmpty()) {
            logger.warn("Attempt to view non-existent version {} for diagram id {}", versionNumber, id);
            return "redirect:/diagrams/" + id;
        }

        DiagramVersion version = versionOpt.get();
        model.addAttribute("selectedVersion", version);

        String svgImage = null;
        String svgRenderingError = null;

        try {
            svgImage = diagramService.renderDiagramAsSvg(version.getPlantUmlCode());
        } catch (PlantUmlRenderingException e) {
            logger.error("Failed to render SVG for diagram id {} version {}: {}", id, versionNumber, e.getMessage());
            svgRenderingError = "Could not render diagram preview: " + e.getMessage();
            svgImage = "<svg width='100%' height='100'><text x='10' y='50' fill='red'>Error rendering diagram.</text></svg>";
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PlantUML code structure for diagram id {} version {}: {}", id, versionNumber,
                    e.getMessage());
            svgRenderingError = "Invalid PlantUML code structure: " + e.getMessage();
            svgImage = "<svg width='100%' height='100'><text x='10' y='50' fill='red'>Invalid code structure.</text></svg>";
        }

        model.addAttribute("svgImage", svgImage);
        if (svgRenderingError != null) {
            model.addAttribute("svgRenderingError", svgRenderingError);
        }

        DiagramRequest refinementRequest = new DiagramRequest();
        refinementRequest.setTitle(diagram.getTitle());
        refinementRequest.setDescription(diagram.getDescription());
        refinementRequest.setExistingCode(version.getPlantUmlCode());
        model.addAttribute("refinementRequest", refinementRequest);

        return "diagrams/version";
    }

    @GetMapping("/diagrams/{id}/download/png")
    public ResponseEntity<byte[]> downloadDiagramAsPng(@PathVariable Long id, Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Diagram> diagramOpt = diagramService.getDiagramById(id);

        if (diagramOpt.isEmpty() || !diagramOpt.get().getUser().getId().equals(user.getId())) {
            logger.warn("Attempt to download diagram id {} by unauthorized user {}", id, user.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        Diagram diagram = diagramOpt.get();
        try {
            byte[] pngData = diagramService.renderDiagramAsPng(diagram.getPlantUmlCode());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            String filename = diagram.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + ".png";
            headers.setContentDispositionFormData("attachment", filename);

            return new ResponseEntity<>(pngData, headers, HttpStatus.OK);

        } catch (PlantUmlRenderingException | IllegalArgumentException e) {
            logger.error("Failed to render PNG for download for diagram id {}: {}", id, e.getMessage());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(("Error rendering diagram: " + e.getMessage()).getBytes(), headers,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Unexpected error during PNG download for diagram id {}: {}", id, e.getMessage(), e);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(("Unexpected error creating diagram file.").getBytes(), headers,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/diagrams/render/png")
    public ResponseEntity<byte[]> renderDiagramAsPngFromCode(@RequestBody String plantUmlCode,
            @RequestParam(required = false, defaultValue = "diagram") String filename) {
        if (plantUmlCode == null || plantUmlCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("PlantUML code cannot be empty.".getBytes());
        }
        logger.debug("Received request to render PNG from code. Filename hint: {}", filename);

        try {
            byte[] pngData = diagramService.renderDiagramAsPng(plantUmlCode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            String sanitizedFilename = filename.replaceAll("[^a-zA-Z0-9.-]", "_") + ".png";
            headers.setContentDispositionFormData("attachment", sanitizedFilename);

            return new ResponseEntity<>(pngData, headers, HttpStatus.OK);

        } catch (PlantUmlRenderingException | IllegalArgumentException e) {
            logger.error("Failed to render PNG from provided code: {}", e.getMessage());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(("Error rendering diagram: " + e.getMessage()).getBytes(), headers,
                    HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Unexpected error during PNG rendering from code: {}", e.getMessage(), e);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(("Unexpected error creating diagram file.").getBytes(), headers,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/diagrams/{id}/delete")
    public String deleteDiagram(@PathVariable Long id, Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Diagram> diagram = diagramService.getDiagramById(id);

        if (diagram.isPresent() && diagram.get().getUser().getId().equals(user.getId())) {
            diagramService.deleteDiagram(id);
        }

        return "redirect:/diagrams";
    }

    @PostMapping("/diagrams/versions/{versionId}/delete")
    public String deleteVersion(@PathVariable Long versionId, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = userService.getUserByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<DiagramVersion> versionOpt = diagramService.getDiagramVersionById(versionId);

        if (versionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Version not found");
            return "redirect:/diagrams";
        }

        DiagramVersion version = versionOpt.get();
        Long diagramId = version.getDiagram().getId();

        boolean deleted = diagramService.deleteDiagramVersion(versionId, user);

        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "Version deleted successfully");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete this version. Make sure it's not the only version.");
        }

        return "redirect:/diagrams/" + diagramId;
    }
}