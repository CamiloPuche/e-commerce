# 🛒 E-Commerce Product Catalog API

Esta es una API RESTful robusta diseñada para la gestión de un catálogo de productos de un e-commerce. El proyecto ha sido implementado siguiendo los principios de arquitectura en capas y buenas prácticas de desarrollo con Spring Boot.

## 🚀 Cómo correr el proyecto

### Requisitos previos
- **Java 17** o superior
- **Gradle** (se incluye el wrapper en el proyecto)

### Pasos para ejecutar
1. Clonar el repositorio:
   ```bash
   git clone <url-del-repo>
   cd e-commerce
   ```
2. Ejecutar la aplicación utilizando el Gradle wrapper:
   ```bash
   ./gradlew bootRun
   ```

La aplicación estará disponible en `http://localhost:8080`.

---

## 📖 Documentación y Herramientas

### 🛠️ Swagger / OpenAPI
La API está completamente documentada. Para acceder a la interfaz interactiva y probar los endpoints, ingresá a:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

### 🗄️ Base de Datos (H2 Console)
Se utiliza una base de datos en memoria (H2) para facilitar la evaluación y evitar configuraciones externas. Para acceder a la consola de administración:
- **URL**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **JDBC URL**: `jdbc:h2:mem:ecommerce_db`
- **User**: `sa`
- **Password**: (vacío)

---

## 🏗️ Decisiones de Arquitectura e Infraestructura

De acuerdo a mi experiencia como desarrollador he aplicado los siguientes criterios técnicos para garantizar que la aplicación sea escalable, mantenible y segura:

### 1. Separación de Responsabilidades (Layered Architecture)
He implementado una estructura de tres capas clara:
- **Controller**: Únicamente se encarga de la exposición de los endpoints, validación de entrada y orquestación de respuestas HTTP.
- **Service**: Aquí reside toda la lógica de negocio y las reglas de transformación de datos. Es la única capa que conoce la lógica de dominio.
- **Repository**: Encapsula la persistencia de datos utilizando Spring Data JPA, abstrayendo la complejidad del acceso a la base de datos.

### 2. Uso de DTOs (Data Transfer Objects)
**Decisión**: No se exponen las entidades de base de datos (`Product`) directamente en los endpoints.
**Por qué**: 
- **Desacoplamiento**: El contrato de la API es independiente de la estructura de la base de datos.
- **Seguridad**: Evitamos la exposición de campos internos o sensibles.
- **Flexibilidad**: Permite tener validaciones distintas para la creación (`ProductRequest`) y la actualización (`ProductUpdateRequest`).

### 3. Manejo Global de Excepciones
**Decisión**: Implementación de un `@RestControllerAdvice` junto con un objeto `ErrorResponse` estandarizado.
**Por qué**: 
- **Consistencia**: El cliente siempre recibe la misma estructura de error, independientemente del fallo.
- **UX**: Se transforman los errores técnicos (stacktraces) en mensajes humanos y claros (ej. 404 Not Found).
- **Seguridad**: Se evitan fugas de información técnica del servidor en respuestas 500.

### 4. Validación de Datos
**Decisión**: Uso de `jakarta.validation` (`@NotNull`, `@Positive`, `@NotBlank`).
**Por qué**: Implementamos el concepto de *"Fail Fast"*. Validamos los datos en el borde de la aplicación (Controller) para evitar que datos corruptos lleguen a la capa de servicio o persistencia.

### 5. Persistencia Eficiente
- **H2 In-Memory**: Elegida para optimizar la experiencia del revisor (Zero-Config).
- **Paginación**: Implementada mediante `Pageable` para evitar problemas de performance al listar grandes volúmenes de productos.
- **Auditoría**: Uso de `@CreationTimestamp` para delegar la gestión de fechas al framework.

---

## 🛠️ Stack Tecnológico
- **Java 17**
- **Spring Boot 3.3.4**
- **Spring Data JPA**
- **H2 Database**
- **Lombok**
- **Springdoc OpenAPI**
- **Gradle**
