# Diagramr - AI-Powered UML Diagram Generator

Diagramr is a web application that allows users to generate, refine, and manage UML diagrams using natural language. It leverages AI to transform text descriptions into PlantUML diagrams, making UML diagram creation accessible to everyone.

## Features

### Core Functionality

**AI-Powered Generation**: Create UML diagrams by describing them in natural language

**Iterative Refinement**: Refine your diagrams with simple text feedback

**Version Control**: Save and manage multiple versions of your diagrams

**Multiple Diagram Types**: Support for Class, Sequence, Use Case, Component, and other diagram types

### User Experience

**Visual Editor**: Preview diagrams in real-time as you refine them

**Code View**: Access and edit the underlying PlantUML code

**Version History**: Track changes and revert to previous versions

**Export Options**: Download diagrams as PNG files

### Technologies Used

1. Backend: Java with Spring Boot
2. Frontend: Thymeleaf, Bootstrap 5
3. Diagram Rendering: PlantUML
4. AI Integration: LLM services for natural language processing
5. Database: PostgreSQL database with JPA/Hibernate

## Getting Started

### Prerequisites

- Java 21
- Gradle
- Internet connection (for Bootstrap CDN and AI services)
- PostgreSQL

## Installation

Clone the repository:

```bash
git clone https://github.com/vishva7/Diagramr.git
cd diagramr
```

Build the project:

```bash
./gradlew build
```

Create a database called Diagramr in PostgreSQL (using psql):

```sql
CREATE DATABASE diagramr;
```

Add the postgres user's password to application.properties and your Groq API key.

Run the application:

```bash
./gradlew bootRun
```

Access the application at http://localhost:8080

## Usage Guide

### Creating a New Diagram

1. Register or log in to your account
2. Navigate to "New Diagram"
3. Fill in the title and description
4. Enter a natural language description of your diagram (e.g., "Create a class diagram for a library management system with books, authors, and users")
5. Click "Generate Diagram" to create the initial version
6. Save your diagram when satisfied

### Refining a Diagram

1. Open an existing diagram
2. Provide feedback in the refinement panel (e.g., "Add getter and setter methods to the Book class")
3. Click "Refine Diagram" to apply changes
4. Save as a new version to keep track of changes

### Managing Versions

1. View version history in the "Version History" tab
2. Switch between versions to compare changes
3. Set any version as the current version
4. Delete unwanted versions

### Exporting Diagrams

1. Open the diagram you want to export
2. Click "Download PNG" to save the diagram as an image

### Acknowledgments

- PlantUML for diagram rendering
- Bootstrap for UI components
- Spring Boot for the application framework
