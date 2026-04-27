# Google Cloud Platform & Docker

## 1. Arquitectura de Despliegue

Para este proyecto, la elección tecnológica es **Cloud Run**.

### Justificación Técnica:
- **Escalabilidad**: Cloud Run es serverless y escala automáticamente basándose en las peticiones HTTP. Puede escalar desde 0 hasta miles de instancias en segundos, ideal para manejar picos de 10k req/min.
- **Costo**: A diferencia de GKE, donde pagas por los nodos activos, en Cloud Run pagas solo por el tiempo de CPU y Memoria utilizados durante el procesamiento de la solicitud.
- **Complejidad Operacional**: Elimina la necesidad de gestionar clústers de Kubernetes. El enfoque es "Container-to-URL", lo que reduce drásticamente el tiempo de mantenimiento.

| Criterio | App Engine | GKE | Cloud Run |
| :--- | :--- | :--- | :--- |
| **Escalabilidad** | Media | Muy Alta | Alta (Serverless) |
| **Costo** | Medio | Alto | Muy Bajo (Escala a 0) |
| **Op. Complexity** | Baja | Muy Alta | Muy Baja |

---

## 2. Dockerfile Optimizado

Se ha implementado un **Multi-stage Build** para garantizar que la imagen final sea lo más liviana y segura posible, eliminando el JDK y las herramientas de compilación del entorno de ejecución.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiamos los archivos de Gradle para aprovechar la caché de capas
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Descargamos las dependencias primero
RUN ./gradlew dependencies --no-daemon

# Copiamos el código fuente y construimos el jar
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear un usuario no-root por seguridad (Principio de Menor Privilegio)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiamos solo el JAR final desde la etapa de build
COPY --from=build /app/build/libs/*.jar app.jar

# Optimización de la JVM para contenedores
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## 3. Manejo de Secretos en GCP

Para evitar la exposición de credenciales, se implementará **Google Secret Manager**.

### Flujo de Integración:
1. **Almacenamiento**: Los secretos (DB\_PASS, API\_KEYS) se cargan en Secret Manager.
2. **Acceso**: Se asigna el rol `roles/secretmanager.secretAccessor` a la cuenta de servicio de Cloud Run.
3. **Inyección**: Los secretos se mapean como **variables de entorno** directamente en la configuración de Cloud Run. La aplicación los consume mediante `${VARIABLE_NAME}`, manteniendo las credenciales fuera del código y del repositorio.

---

## 4. Estrategia de CI/CD

Se utilizará un pipeline automatizado con **GitHub Actions**.

### Flujo del Pipeline:
`Merge a main` $\rightarrow$ `Run Tests` $\rightarrow$ `Docker Build` $\rightarrow$ `Push to Artifact Registry` $\rightarrow$ `Deploy to Cloud Run`.

- **Build & Test**: Ejecución de tests unitarios con Gradle.
- **Containerization**: Construcción de la imagen optimizada y push al Artifact Registry de GCP.
- **Deployment**: Actualización del servicio de Cloud Run mediante el CLI de Google Cloud (`gcloud run deploy`).
