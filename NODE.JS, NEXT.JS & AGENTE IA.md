# Node.js Microservice & AI Agent Integration

## Parte A: Microservicio de Notificaciones

Para implementar el sistema de notificaciones, se ha utilizado el **Patrón Strategy**. Este diseño permite que el sistema sea extensible: añadir un nuevo canal de notificación no requiere modificar la lógica de enrutamiento, cumpliendo con el principio de Abierto/Cerrado (OCP) de SOLID.

### Implementación en TypeScript

```typescript
// --- Infraestructura de Logging ---
class Logger {
    info(message: string) { console.log(`[INFO] ${new Date().toISOString()} - ${message}`); }
    error(message: string) { console.error(`[ERROR] ${new Date().toISOString()} - ${message}`); }
}

const logger = new Logger();

// --- Dominio y Estrategias ---

interface NotificationStrategy {
    send(userId: string, message: string): Promise<void>;
}

class EmailStrategy implements NotificationStrategy {
    private readonly logger = logger; // Inyectado o referenciado

    async send(userId: string, message: string): Promise<void> {
        this.logger.info(`Sending Email to user ${userId}: ${message}`);
        // Integración con proveedor de email (ej. SendGrid)
    }
}

class SmsStrategy implements NotificationStrategy {
    private readonly logger = logger;

    async send(userId: string, message: string): Promise<void> {
        this.logger.info(`Sending SMS to user ${userId}: ${message}`);
        // Integración con proveedor de SMS (ej. Twilio)
    }
}

class PushStrategy implements NotificationStrategy {
    private readonly logger = logger;

    async send(userId: string, message: string): Promise<void> {
        this.logger.info(`Sending Push Notification to user ${userId}: ${message}`);
        // Integración con FCM (Firebase Cloud Messaging)
    }
}

// --- Orquestador (Express Controller) ---
import express, { Request, Response } from 'express';

const app = express();
app.use(express.json());

const strategies: Record<string, NotificationStrategy> = {
    email: new EmailStrategy(),
    sms: new SmsStrategy(),
    push: new PushStrategy(),
};

app.post('/notify', async (req: Request, res: Response) => {
    const { userId, message, channel } = req.body;

    if (!userId || !message || !channel) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    const strategy = strategies[channel];

    if (!strategy) {
        logger.error(`Attempt to use unsupported channel: ${channel}`);
        return res.status(400).json({ error: `Unsupported channel: ${channel}` });
    }

    try {
        await strategy.send(userId, message);
        return res.status(200).json({ success: true, message: `Notification sent via ${channel}` });
    } catch (error) {
        logger.error(`Error sending notification via ${channel}: ${error}`);
        return res.status(500).json({ error: 'Internal server error' });
    }
});

app.listen(3000, () => logger.info('Notification service running on port 3000'));
```

---

## Parte B: Integración de Agente IA (Claude)

Para integrar a Claude en el flujo de órdenes del e-commerce, propongo un enfoque de **Agente Operativo** basado en **Tool Use (Function Calling)** y **RAG**.

### 1. Propuesta Técnica: Tool Use
En lugar de que Claude sea un chatbot pasivo, se le definen "herramientas" (funciones) que el modelo puede decidir invocar según la intención del usuario.

**Ejemplo de flujo:**
- **Usuario**: *"¿Dónde está mi pedido #123 y puedo cambiar la dirección?"*
- **Claude**: Identifica que debe llamar a `getOrderStatus(orderId)` y `updateShippingAddress(orderId, address)`.
- **Sistema**: Ejecuta las llamadas a la API de Spring Boot y le devuelve los datos a Claude.
- **Claude**: Sintetiza el resultado técnico en una respuesta natural: *"Tu pedido está en camino y ya he actualizado la dirección por vos"*.

### 2. Tareas Delegadas al Agente
- **Soporte Autónomo**: Consulta de estados de envío y gestión de cancelaciones.
- **Asistente de Ventas Personalizado**: Recomendaciones basadas en el historial del usuario utilizando **RAG (Retrieval Augmented Generation)** sobre el catálogo de productos.
- **Gestión de Devoluciones**: Guiar al usuario en el proceso de devolución validando las políticas de la empresa.

### 3. Conectividad y Arquitectura
- **Interfaz**: Claude se conecta a través de un orquestador (Node.js) que actúa como puente entre la API de Anthropic y la API de Spring Boot.
- **Contexto**: Se implementa RAG para que la IA tenga acceso actualizado a las políticas de la empresa sin necesidad de re-entrenar el modelo.
- **Seguridad**: El agente no tiene acceso directo a la DB; todas las acciones pasan por la API con validaciones de IAM y permisos de usuario.
