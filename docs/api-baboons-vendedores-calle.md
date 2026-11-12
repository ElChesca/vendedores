# API Baboons para vendedores en la calle

Estado: endpoints del MVP integrados en Baboons mediante el PR #427, ya mergeado; el SQL de `migrations/vendedores_calle_mvp.sql` fue ejecutado en Neon. Todas las rutas usan JWT Baboons y el prefijo `/api`.

Base: `https://<servidor-baboons>/api/negocios/{negocio_id}`. Enviar `Authorization: Bearer <jwt_token>` y `Content-Type: application/json`.

## Endpoints

| Metodo | Ruta | Acceso | Funcion |
|---|---|---|---|
| `GET` | `/vendedores-calle/jornada/actual?fecha=YYYY-MM-DD` | Vendedor | Jornada, zona y mensajes obligatorios pendientes. |
| `POST` | `/vendedores-calle/jornada/iniciar` | Vendedor | Abre jornada para `fecha` (opcional; por defecto hoy). |
| `POST` | `/vendedores-calle/jornada/cerrar` | Vendedor | Cierra jornada para `fecha`. |
| `GET` | `/vendedores-calle/mensajes?fecha=YYYY-MM-DD` | Vendedor/Admin | Vendedor recibe sus mensajes; admin obtiene mensajes del negocio y cantidad de respuestas. |
| `POST` | `/vendedores-calle/mensajes` | Admin | Crea mensaje global o dirigido por `vendedor_id`; acepta `idempotency_key`. |
| `POST` | `/vendedores-calle/mensajes/{mensaje_id}/respuesta` | Vendedor | Registra `aceptado` y `respuesta`. |
| `GET` | `/vendedores-calle/mensajes/{mensaje_id}/respuestas` | Admin | Consulta feedback y acuses por vendedor. |
| `PUT` | `/vendedores-calle/asignaciones/{vendedor_id}/{fecha}` | Admin | Asigna `zona_id` para una fecha. |
| `GET` | `/vendedores-calle/clientes-proyectables?fecha=YYYY-MM-DD` | Vendedor | Devuelve clientes activos de la zona y dia con visita habilitada. |
| `GET` | `/vendedores-calle/proyectado?fecha=YYYY-MM-DD` | Vendedor | Consulta los renglones proyectados. |
| `PUT` | `/vendedores-calle/proyectado` | Vendedor | Guarda `fecha` e `items: [{cliente_id, importe_estimado}]`. Requiere jornada abierta y aceptar mensajes obligatorios. |
| `POST` | `/vendedores-calle/proyectado/excepciones` | Vendedor | Agrega `fecha`, `cliente_id` y `motivo` para cliente fuera de zona. La app necesita una fuente de busqueda de clientes fuera de zona, aun no incluida en los endpoints actuales. |
| `POST` | `/vendedores-calle/visitas` | Vendedor | Registra `fecha`, `proyectado_id`, `cliente_id`, `resultado`, `observaciones` e `idempotency_key`. |
| `POST` | `/vendedores-calle/prospectos` | Vendedor | Crea lead CRM con contacto, direccion y coordenadas; acepta `idempotency_key` y envia correo a admins del negocio. |
| `POST` | `/vendedores-calle/objetivos` | Admin | Guarda `vendedor_id`, `desde`, `hasta`, `importe_objetivo`; no acepta periodos superpuestos. |
| `GET` | `/vendedores-calle/tablero?desde=YYYY-MM-DD&hasta=YYYY-MM-DD&vendedor_id=...` | Vendedor/Admin | Proyectados, visitados, clientes con venta, importe proyectado/vendido y porcentajes de cobertura, conversion, objetivo y venta contra proyectado. Admin omite `vendedor_id` para vista 360. |
| `GET` | `/vendedores-calle/rastro?fecha=YYYY-MM-DD&vendedor_id=...` | Admin | Puntos GPS y eventos de visita del periodo diario. Sin vendedor devuelve todos. |
| `POST` | `/mobile/tracking` | Vendedor | Ruta existente adaptada para lotes GPS y reintentos con `device_id`/`sequence`; cada punto debe corresponder al intervalo de una jornada. |

## Ejemplo de carga GPS

```json
{
  "device_id": "android-installation-id",
  "posiciones": [
    {"lat": -33.3012, "lng": -66.3371, "accuracy": 8.5, "speed": 2.1, "ts": 1794427200000, "sequence": 42}
  ]
}
```

## Ejemplo de prospecto

```json
{
  "nombre": "Comercio Ejemplo",
  "telefono": "+5492664000000",
  "email": "contacto@example.com",
  "cuit": "",
  "direccion": "Av. Ejemplo 123",
  "provincia": "San Luis",
  "localidad": "San Luis",
  "latitud": -33.3012,
  "longitud": -66.3371,
  "notas": "Prefiere contacto por la manana"
}
```

Una alta nueva responde `201` con `lead_id` y `email_enviado`. Un duplicado por telefono o email responde `200` con `duplicado: true`. Si no hay email de administrador o falla SMTP, el lead queda guardado y `email_enviado` es `false`.

## Datos maestros y fuente de ventas

- La app Android se autentica contra `POST /login` con `{nombre, password}`. El token JWT contiene `negocio_id`, `vendedor_id` y `empleado_id` cuando corresponda.
- Clientes y zonas se administran desde Baboons. Los clientes proyectables se filtran por zona diaria y por el indicador `visita_<dia>` del cliente.
- El tablero suma ventas de Baboons no anuladas, no vinculadas a una nota de credito y no marcadas como nota de credito. La regla se basa en `ventas.estado`, `anulada_por_nc_id` y `es_nota_credito`.
- La primera version de asignacion de zona es por fecha. Si no hay registro diario, se toma `vendedores.zona_id` como zona base.

## Migracion de esquema

Migracion aplicada por el equipo en Neon: `migrations/vendedores_calle_mvp.sql`. Crea jornadas, mensajes, acuses, asignaciones, proyectados, visitas y objetivos; agrega campos de direccion/coordenadas al CRM y secuencia de dispositivo al GPS.
