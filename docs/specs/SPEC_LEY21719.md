# SPEC — Adecuación de ChessQuery a Ley N° 21.719 de Protección de Datos Personales

**Fecha:** 2026-04-27  
**Objetivo:** Implementar cumplimiento completo de la Ley 21.719 (vigencia plena diciembre 2026) en la plataforma ChessQuery  
**Prioridad:** ALTA (requisito legal obligatorio)  
**Tipo:** Compliance + Implementación técnica

---

## Contexto Legal

La Ley N° 21.719 regula la protección y tratamiento de datos personales en Chile, con vigencia plena desde diciembre 2026. ChessQuery trata datos personales de jugadores, organizadores y administradores, incluyendo:

- **Datos identificatorios:** RUT, nombre, email, fecha nacimiento, género
- **Datos sensibles:** Ninguno actualmente (no trata salud, biometría, ideología)
- **Datos públicos:** Ratings ELO, resultados de torneos, historial de partidas

**Obligaciones principales:**
1. Consentimiento libre, específico, informado e inequívoco (Art. 12)
2. Derechos ARCOP del titular (Acceso, Rectificación, Cancelación/Supresión, Oposición, Portabilidad) (Art. 4-9)
3. Seguridad adecuada (cifrado, control acceso, logs) (Art. 14 quinquies)
4. Retención limitada al plazo necesario (Art. 3 literal c)
5. Notificación de vulneraciones de seguridad (Art. 14 sexies)
6. Privacidad desde el diseño (Art. 14 quáter)

---

## Requisitos de Cumplimiento

### REQ-1: Gestión de Consentimiento Granular

**Descripción:** Implementar sistema de consentimiento que cumpla con Art. 12 (libre, informado, específico, inequívoco).

**Criterios de aceptación:**
- [ ] Tabla `CONSENT_LOG` con versionamiento de políticas
- [ ] Consentimientos separados por finalidad (operativa, marketing, transferencia)
- [ ] Registro de fecha/hora, versión política, método captura, IP
- [ ] Mecanismo de revocación simple y gratuito
- [ ] UI de gestión de consentimientos en perfil de usuario

**Impacto:** MS-Auth, MS-Users, Frontend (chess-portal)

---

### REQ-2: Implementación de Derechos ARCOP

**Descripción:** Endpoints y flujos para ejercicio de derechos del titular (Art. 4-9).

**Criterios de aceptación:**
- [ ] **Acceso:** Exportar todos los datos del titular en JSON/CSV
- [ ] **Rectificación:** Actualizar datos inexactos/desactualizados
- [ ] **Supresión:** Borrado físico o anonimización según retención legal
- [ ] **Oposición:** Bloquear tratamiento específico (ej. marketing)
- [ ] **Portabilidad:** Exportar datos en formato estructurado interoperable
- [ ] Respuesta en máximo 30 días corridos (Art. 11)
- [ ] Formulario web accesible sin login para solicitudes

**Impacto:** Todos los microservicios, BFF-Player, Frontend

---

### REQ-3: Seguridad y Cifrado

**Descripción:** Medidas técnicas de seguridad según Art. 14 quinquies.

**Criterios de aceptación:**
- [ ] Cifrado en reposo: AES-256 para campos sensibles (email, RUT)
- [ ] Cifrado en tránsito: TLS 1.3 obligatorio en todas las conexiones
- [ ] RBAC granular: principio de mínimo privilegio
- [ ] Logs de auditoría: quién accedió a qué dato y cuándo
- [ ] Seudonimización en entornos no productivos (staging/dev)
- [ ] Hashing de tokens con SHA-256 (refresh tokens)

**Impacto:** Infraestructura, PostgreSQL, API Gateway, todos los servicios

---

### REQ-4: Políticas de Retención Automática

**Descripción:** Ciclo de vida de datos según Art. 3 literal c (proporcionalidad).

**Criterios de aceptación:**
- [ ] Tabla `DATA_RETENTION_POLICY` con reglas por tipo de dato
- [ ] Job programado (cron) para purga automática
- [ ] Usuarios inactivos >24 meses: anonimización automática
- [ ] Datos de torneos: retención 10 años (prescripción legal deportiva)
- [ ] Logs de auditoría: retención 5 años
- [ ] Notificación al usuario 30 días antes de purga

**Impacto:** MS-Users, MS-Tournament, MS-Game, MS-ETL

---

### REQ-5: Notificación de Vulneraciones

**Descripción:** Protocolo de reporte según Art. 14 sexies.

**Criterios de aceptación:**
- [ ] Tabla `SECURITY_INCIDENT_LOG`
- [ ] Procedimiento documentado de respuesta a incidentes
- [ ] Notificación a Agencia de Protección de Datos en <72h
- [ ] Notificación a usuarios afectados si datos sensibles (no aplica actualmente)
- [ ] Registro de naturaleza, efectos, categorías de datos, medidas adoptadas

**Impacto:** MS-Auth, MS-Users, Infraestructura

---

### REQ-6: Privacidad desde el Diseño

**Descripción:** Privacy by Design según Art. 14 quáter.

**Criterios de aceptación:**
- [ ] Auditoría de esquemas: eliminar campos innecesarios
- [ ] Minimización: solo recolectar datos estrictamente necesarios
- [ ] Separación de datos identificatorios de datos transaccionales
- [ ] Configuración por defecto: solo datos mínimos
- [ ] Evaluación de impacto (PIA) para nuevos tratamientos masivos

**Impacto:** Diseño de BD, todos los microservicios

---

### REQ-7: Transparencia e Información

**Descripción:** Deber de información según Art. 14 ter.

**Criterios de aceptación:**
- [ ] Política de Privacidad publicada y accesible
- [ ] Aviso de Privacidad en registro/login
- [ ] Información clara sobre: finalidades, destinatarios, plazo retención, derechos
- [ ] Identificación del responsable de datos (ChessQuery / DuocUC)
- [ ] Canal de contacto para ejercicio de derechos

**Impacto:** Frontend, documentación legal

---

### REQ-8: Transferencia Internacional

**Descripción:** Cumplimiento Art. 27-29 (si aplica).

**Criterios de aceptación:**
- [ ] Inventario de transferencias internacionales (ej. MinIO en AWS fuera de Chile)
- [ ] Cláusulas contractuales estándar si país destino no es adecuado
- [ ] Información al usuario sobre transferencias
- [ ] Consentimiento explícito si no hay decisión de adecuación

**Impacto:** MS-Game (MinIO/S3), infraestructura cloud

---

## Diseño de Solución

### Arquitectura de Cumplimiento

```
┌─────────────────────────────────────────────────────────────┐
│                    CAPA DE CUMPLIMIENTO                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Consent Mgmt │  │ Rights Mgmt  │  │ Audit Logger │      │
│  │   Service    │  │   Service    │  │   Service    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                  │                  │              │
│         └──────────────────┴──────────────────┘              │
│                            │                                 │
├────────────────────────────┼─────────────────────────────────┤
│                            ▼                                 │
│              ┌──────────────────────────┐                    │
│              │   MS-Compliance (nuevo)  │                    │
│              │   Puerto: 8087           │                    │
│              └──────────────────────────┘                    │
│                            │                                 │
├────────────────────────────┼─────────────────────────────────┤
│                            ▼                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐        │
│  │MS-Auth  │  │MS-Users │  │MS-Tour  │  │MS-Game  │        │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘        │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Nuevo Microservicio: MS-Compliance

**Responsabilidad:** Centralizar lógica de cumplimiento legal.

**Endpoints:**
- `POST /compliance/consent` - Registrar consentimiento
- `GET /compliance/consent/{userId}` - Consultar consentimientos
- `DELETE /compliance/consent/{userId}/{purpose}` - Revocar consentimiento
- `POST /compliance/rights/access` - Solicitud de acceso
- `POST /compliance/rights/rectification` - Solicitud de rectificación
- `POST /compliance/rights/erasure` - Solicitud de supresión
- `POST /compliance/rights/portability` - Solicitud de portabilidad
- `GET /compliance/audit/{userId}` - Logs de auditoría
- `POST /compliance/incident` - Reportar incidente de seguridad

**Base de datos:** `compliance_db` (PostgreSQL)

---

### Modelo de Datos Extendido

#### Nueva BD: compliance_db

```sql
-- Gestión de consentimiento
CREATE TABLE CONSENT_LOG (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(50) NOT NULL, -- 'OPERATIONAL', 'MARKETING', 'TRANSFER'
    consent_given BOOLEAN NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    capture_method VARCHAR(20) NOT NULL, -- 'WEB', 'APP', 'EMAIL'
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP,
    UNIQUE(user_id, purpose, created_at)
);

CREATE INDEX idx_consent_user_purpose ON CONSENT_LOG(user_id, purpose);

-- Políticas de privacidad versionadas
CREATE TABLE PRIVACY_POLICY (
    id SERIAL PRIMARY KEY,
    version VARCHAR(20) UNIQUE NOT NULL,
    content TEXT NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Solicitudes de ejercicio de derechos
CREATE TABLE RIGHTS_REQUEST (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_type VARCHAR(20) NOT NULL, -- 'ACCESS', 'RECTIFICATION', 'ERASURE', 'PORTABILITY', 'OPPOSITION'
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'
    request_data JSONB,
    response_data JSONB,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP,
    responder_id BIGINT,
    rejection_reason TEXT,
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'))
);

CREATE INDEX idx_rights_user_status ON RIGHTS_REQUEST(user_id, status);

-- Logs de auditoría de acceso a datos
CREATE TABLE AUDIT_LOG (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL, -- Usuario cuyos datos fueron accedidos
    accessor_id BIGINT NOT NULL, -- Usuario/sistema que accedió
    accessor_type VARCHAR(20) NOT NULL, -- 'USER', 'SYSTEM', 'ADMIN'
    action VARCHAR(50) NOT NULL, -- 'READ', 'UPDATE', 'DELETE', 'EXPORT'
    resource_type VARCHAR(50) NOT NULL, -- 'PLAYER', 'GAME', 'TOURNAMENT'
    resource_id BIGINT,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_date ON AUDIT_LOG(user_id, created_at DESC);
CREATE INDEX idx_audit_accessor ON AUDIT_LOG(accessor_id, created_at DESC);

-- Políticas de retención
CREATE TABLE DATA_RETENTION_POLICY (
    id SERIAL PRIMARY KEY,
    data_type VARCHAR(50) UNIQUE NOT NULL, -- 'USER_PROFILE', 'GAME_RECORD', 'TOURNAMENT', 'AUDIT_LOG'
    retention_days INT NOT NULL,
    action_on_expiry VARCHAR(20) NOT NULL, -- 'DELETE', 'ANONYMIZE', 'ARCHIVE'
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (action_on_expiry IN ('DELETE', 'ANONYMIZE', 'ARCHIVE'))
);

-- Incidentes de seguridad
CREATE TABLE SECURITY_INCIDENT (
    id BIGSERIAL PRIMARY KEY,
    incident_type VARCHAR(50) NOT NULL, -- 'DATA_BREACH', 'UNAUTHORIZED_ACCESS', 'DATA_LOSS'
    severity VARCHAR(20) NOT NULL, -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    description TEXT NOT NULL,
    affected_users_count INT,
    affected_data_categories TEXT[], -- Array de categorías afectadas
    detected_at TIMESTAMP NOT NULL,
    reported_to_agency_at TIMESTAMP,
    reported_to_users_at TIMESTAMP,
    mitigation_measures TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'INVESTIGATING', 'MITIGATED', 'CLOSED'
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CHECK (status IN ('OPEN', 'INVESTIGATING', 'MITIGATED', 'CLOSED'))
);
```

#### Extensiones a BDs existentes

**auth_db:**
```sql
-- Agregar a AUTH_USER
ALTER TABLE AUTH_USER ADD COLUMN data_processing_consent BOOLEAN DEFAULT FALSE;
ALTER TABLE AUTH_USER ADD COLUMN marketing_consent BOOLEAN DEFAULT FALSE;
ALTER TABLE AUTH_USER ADD COLUMN last_consent_update TIMESTAMP;
ALTER TABLE AUTH_USER ADD COLUMN account_status VARCHAR(20) DEFAULT 'ACTIVE'; -- 'ACTIVE', 'SUSPENDED', 'ANONYMIZED'
```

**user_db:**
```sql
-- Agregar a PLAYER
ALTER TABLE PLAYER ADD COLUMN anonymized_at TIMESTAMP;
ALTER TABLE PLAYER ADD COLUMN last_activity_at TIMESTAMP DEFAULT NOW();
ALTER TABLE PLAYER ADD COLUMN deletion_scheduled_at TIMESTAMP;

-- Cifrado de campos sensibles (implementar con pgcrypto)
-- email, rut se cifrarán con AES-256
```

---

## Tareas de Implementación

### Fase 1: Infraestructura Base (Semana 1-2)

**TASK-1.1:** Crear microservicio MS-Compliance
- [ ] Scaffold Spring Boot con puerto 8087
- [ ] Configurar `compliance_db` en PostgreSQL
- [ ] Migraciones Flyway para tablas base
- [ ] Dockerfile y docker-compose.yml
- **Responsable:** Backend
- **Estimación:** 3 días

**TASK-1.2:** Implementar cifrado en reposo
- [ ] Activar extensión `pgcrypto` en todas las BDs
- [ ] Crear funciones de cifrado/descifrado
- [ ] Migrar campos sensibles (email, RUT) a cifrado
- [ ] Actualizar queries en microservicios
- **Responsable:** Backend + DBA
- **Estimación:** 5 días

**TASK-1.3:** Configurar TLS 1.3 obligatorio
- [ ] Certificados SSL para API Gateway
- [ ] Configurar Nginx con TLS 1.3 mínimo
- [ ] Forzar HTTPS en todos los endpoints
- [ ] Actualizar conexiones PostgreSQL con SSL
- **Responsable:** DevOps
- **Estimación:** 2 días

---

### Fase 2: Gestión de Consentimiento (Semana 3-4)

**TASK-2.1:** Backend de consentimientos
- [ ] Endpoints POST/GET/DELETE `/compliance/consent`
- [ ] Lógica de versionamiento de políticas
- [ ] Validación de consentimiento en operaciones críticas
- [ ] Eventos RabbitMQ: `consent.granted`, `consent.revoked`
- **Responsable:** Backend
- **Estimación:** 4 días

**TASK-2.2:** Frontend de consentimientos
- [ ] Modal de consentimiento en registro
- [ ] Página de gestión de consentimientos en perfil
- [ ] Checkbox granular por finalidad
- [ ] Visualización de política de privacidad
- **Responsable:** Frontend
- **Estimación:** 3 días

**TASK-2.3:** Integración con MS-Auth y MS-Users
- [ ] Validar consentimiento operativo antes de crear cuenta
- [ ] Bloquear operaciones si consentimiento revocado
- [ ] Sincronizar estado de consentimiento
- **Responsable:** Backend
- **Estimación:** 2 días

---

### Fase 3: Derechos ARCOP (Semana 5-7)

**TASK-3.1:** Derecho de Acceso
- [ ] Endpoint `POST /compliance/rights/access`
- [ ] Agregador de datos de todos los microservicios
- [ ] Exportación en JSON y CSV
- [ ] Generación de reporte completo del usuario
- **Responsable:** Backend
- **Estimación:** 5 días

**TASK-3.2:** Derecho de Rectificación
- [ ] Endpoint `POST /compliance/rights/rectification`
- [ ] Validación de datos a rectificar
- [ ] Propagación de cambios a microservicios
- [ ] Notificación de rectificación completada
- **Responsable:** Backend
- **Estimación:** 3 días

**TASK-3.3:** Derecho de Supresión
- [ ] Endpoint `POST /compliance/rights/erasure`
- [ ] Lógica de borrado físico vs anonimización
- [ ] Validar excepciones legales (torneos en curso, obligaciones contractuales)
- [ ] Job de supresión diferida (30 días)
- [ ] Propagación a todos los microservicios
- **Responsable:** Backend
- **Estimación:** 6 días

**TASK-3.4:** Derecho de Portabilidad
- [ ] Endpoint `POST /compliance/rights/portability`
- [ ] Exportación en formato estructurado (JSON)
- [ ] Incluir: perfil, partidas, torneos, ratings
- [ ] Generación de archivo ZIP descargable
- **Responsable:** Backend
- **Estimación:** 4 días

**TASK-3.5:** Frontend de ejercicio de derechos
- [ ] Formulario de solicitud de derechos
- [ ] Página de seguimiento de solicitudes
- [ ] Descarga de exportaciones
- [ ] Confirmación de supresión
- **Responsable:** Frontend
- **Estimación:** 4 días

---

### Fase 4: Auditoría y Seguridad (Semana 8-9)

**TASK-4.1:** Sistema de auditoría
- [ ] Interceptor/Aspect para logging automático
- [ ] Registrar accesos a datos personales
- [ ] Dashboard de auditoría para admins
- [ ] Alertas de accesos anómalos
- **Responsable:** Backend
- **Estimación:** 5 días

**TASK-4.2:** RBAC granular
- [ ] Revisar permisos actuales
- [ ] Implementar roles específicos (DATA_PROTECTION_OFFICER)
- [ ] Restringir acceso a datos sensibles
- [ ] Logs de cambios de permisos
- **Responsable:** Backend
- **Estimación:** 3 días

**TASK-4.3:** Seudonimización en staging/dev
- [ ] Script de anonimización de datos
- [ ] Job de sincronización prod → staging con anonimización
- [ ] Validar que no se exponen datos reales
- **Responsable:** DevOps
- **Estimación:** 3 días

---

### Fase 5: Retención y Purga (Semana 10)

**TASK-5.1:** Políticas de retención
- [ ] Seed de políticas por defecto en `DATA_RETENTION_POLICY`
- [ ] Configuración por tipo de dato
- [ ] UI de administración de políticas
- **Responsable:** Backend + Frontend
- **Estimación:** 2 días

**TASK-5.2:** Job de purga automática
- [ ] Cron job diario para identificar datos expirados
- [ ] Lógica de anonimización (reemplazar nombre por "Usuario Anónimo", email por hash)
- [ ] Notificación 30 días antes de purga
- [ ] Logs de purgas ejecutadas
- **Responsable:** Backend
- **Estimación:** 4 días

---

### Fase 6: Incidentes y Documentación (Semana 11-12)

**TASK-6.1:** Protocolo de incidentes
- [ ] Endpoints de gestión de incidentes
- [ ] Workflow de reporte a Agencia
- [ ] Plantillas de notificación a usuarios
- [ ] Dashboard de incidentes para admins
- **Responsable:** Backend + Frontend
- **Estimación:** 4 días

**TASK-6.2:** Documentación legal
- [ ] Política de Privacidad (redacción legal)
- [ ] Aviso de Privacidad
- [ ] Términos y Condiciones actualizados
- [ ] Procedimiento de ejercicio de derechos
- **Responsable:** Legal + Frontend
- **Estimación:** 5 días

**TASK-6.3:** Evaluación de Impacto (PIA)
- [ ] Documento de evaluación de impacto
- [ ] Identificación de riesgos
- [ ] Medidas de mitigación
- [ ] Revisión por DPO (si aplica)
- **Responsable:** Legal + Backend Lead
- **Estimación:** 3 días

---

## Cronograma

| Fase | Duración | Inicio | Fin | Entregables |
|------|----------|--------|-----|-------------|
| Fase 1: Infraestructura | 2 semanas | Sem 1 | Sem 2 | MS-Compliance, cifrado, TLS |
| Fase 2: Consentimiento | 2 semanas | Sem 3 | Sem 4 | Sistema de consentimientos |
| Fase 3: Derechos ARCOP | 3 semanas | Sem 5 | Sem 7 | Endpoints + UI de derechos |
| Fase 4: Auditoría | 2 semanas | Sem 8 | Sem 9 | Logs, RBAC, seudonimización |
| Fase 5: Retención | 1 semana | Sem 10 | Sem 10 | Políticas + job de purga |
| Fase 6: Incidentes | 2 semanas | Sem 11 | Sem 12 | Protocolo + docs legales |
| **TOTAL** | **12 semanas** | | | **Cumplimiento completo** |

**Fecha objetivo:** Agosto 2026 (4 meses antes de vigencia plena)

---

## Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Cambios en interpretación de la ley | Media | Alto | Consultar con abogado especialista, seguir instrucciones de Agencia |
| Complejidad de cifrado afecta performance | Media | Medio | Benchmarking, índices optimizados, caché |
| Usuarios no entienden consentimientos | Alta | Medio | UX claro, lenguaje simple, tooltips explicativos |
| Supresión de datos rompe integridad referencial | Media | Alto | Anonimización en lugar de borrado físico, validar dependencias |
| Falta de recursos legales | Alta | Alto | Contratar asesoría externa, usar plantillas de Agencia |

---

## Métricas de Éxito

- [ ] 100% de usuarios nuevos otorgan consentimiento explícito
- [ ] Solicitudes de derechos respondidas en <15 días (50% del plazo legal)
- [ ] 0 incidentes de seguridad no reportados
- [ ] Auditoría completa de accesos a datos sensibles
- [ ] Políticas de retención aplicadas automáticamente
- [ ] Documentación legal aprobada por asesor externo
- [ ] Evaluación de impacto completada y aprobada

---

## Referencias

- Ley N° 21.719: `texto-ley.md`
- Guía técnica: `politics.md`
- Contexto del proyecto: `CONTEXT.md`
- Historias de usuario: `HISTORIAS_USUARIO.md`
