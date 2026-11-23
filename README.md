# Gestion de Vendedores en la Calle

Aplicacion Android nativa para que cada vendedor inicie su jornada, consulte y confirme las comunicaciones del dia, prepare su recorrido por zona, registre visitas y oportunidades, y comparta su actividad con administracion. El ERP Baboons sera la consola de administracion y el backend de la app.

## Alcance inicial

- App del vendedor para Android.
- API de Baboons como backend para usuarios, jornada, ubicaciones, mensajes, proyectado y visitas.
- Administracion de mensajes, clientes, zonas, objetivos y seguimiento desde Baboons.
- Odoo 19 queda como ERP conectado a Baboons, sujeto a confirmar que datos y operaciones ya sincroniza.
- Contrato de endpoints en [`docs/api-baboons-vendedores-calle.md`](docs/api-baboons-vendedores-calle.md); alcance funcional en [`docs/alcance-mvp.md`](docs/alcance-mvp.md).

## Android implementado

- Inicio de sesion con JWT de Baboons.
- Apertura/cierre de jornada, mensajes con acuse, proyectado de clientes habilitados, registro de visitas y alta de potenciales.
- Verificación de datos del cliente (contacto y ubicación). Queda pendiente de aprobación en el ERP. Si no hay red, se guarda y se reintenta con la misma clave.
- Seguimiento de ubicacion con notificacion visible, cola local para sincronizacion y mapa nativo de Google Maps.
- Indicadores diarios de cobertura, conversion, venta y avance del objetivo.

El mapa usa Maps SDK for Android. Para mostrarlo hay que habilitar el SDK en Google Cloud, vincular facturacion y restringir una API key al paquete `com.baboons.vendedorescalle` (y, para la variante testing, tambien a `com.baboons.vendedorescalle.testing`) y a los certificados SHA-1 de desarrollo y produccion. La app lee `MAPS_API_KEY` desde las propiedades de Gradle o desde la variable de entorno del mismo nombre. En Windows se puede agregar a `%USERPROFILE%\.gradle\gradle.properties` como `MAPS_API_KEY=...`; no guardes la clave en este repositorio. La clave se incluye en la app instalada, por lo que las restricciones de Google son obligatorias.

## Variantes prod y testing

Hay dos variantes. `prod` sigue apuntando al ERP de produccion y conserva el `applicationId` `com.baboons.vendedorescalle`. La de testing (flavor Gradle `qa`) apunta a `https://testing.baboons.com.ar/api/`, se instala al lado de produccion (`applicationId` `com.baboons.vendedorescalle.testing`), se llama **Bond Vendedores TEST** y muestra una franja `TESTING` en el login, en la jornada y en la notificacion de seguimiento.

AGP no deja crear un `productFlavor` ni un `buildType` llamado `testing`, porque los nombres no pueden empezar con `test` (chocan con `src/test`). El flavor se llama `qa`. `assembleTestingDebug` y `assembleTestingRelease` son alias de `assembleQaDebug` y `assembleQaRelease`.

La base URL sale de `BuildConfig.BABOONS_API_BASE`. Las dos variantes mantienen el prefijo `/api/` porque el cliente le concatena rutas como `login` y `negocios/...`.

| Variante | Comando | API | APK |
|---|---|---|---|
| Produccion debug | `./gradlew assembleProdDebug` | `https://concesionarias.baboons.com.ar/api/` | `app/build/outputs/apk/prod/debug/app-prod-debug.apk` |
| Produccion release | `./gradlew assembleProdRelease` | igual | `app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk` |
| Testing debug | `./gradlew assembleTestingDebug` | `https://testing.baboons.com.ar/api/` | `app/build/outputs/apk/qa/debug/app-qa-debug.apk` |
| Testing release | `./gradlew assembleTestingRelease` | igual | `app/build/outputs/apk/qa/release/app-qa-release-unsigned.apk` |

Los APK de release salen sin firmar (`-unsigned`) porque el proyecto no tiene `signingConfig`. Debug usa el certificado de debug.

Tests de ambas variantes: `./gradlew testProdDebugUnitTest testQaDebugUnitTest` (`testTestingDebugUnitTest` es alias del segundo).

No hay workflow ni script de distribucion en el repositorio. Para publicar hay que usar **prod** (`assembleProdRelease` o `assembleProdDebug`). `assembleDebug` y `assembleRelease` arman las dos variantes; el APK de produccion queda en la carpeta `prod/`.

Firebase App Distribution del proyecto `appbaboonssellers` tiene registrada solo `com.baboons.vendedorescalle`. La variante testing se puede instalar con el APK directo. Para distribuirla por Firebase hay que dar de alta una app Android nueva con package `com.baboons.vendedorescalle.testing`. Si mas adelante se agrega `app/google-services.json` solo con el package de produccion, el build genera `app/src/qa/google-services.json` (esta en `.gitignore`) copiando ese client y cambiando el package, para que el plugin de Google Services no corte la compilacion. Si ese archivo ya existe, no se pisa: hay que borrarlo para regenerarlo. No reemplaza el alta en Firebase ni sirve para distribuir la variante testing.

La migracion de Baboons ya esta mergeada y aplicada en Neon. La busqueda de clientes fuera de zona para agregar excepciones y las pantallas administrativas de mapa/tablero en Baboons siguen pendientes de concretar.

## Decisiones iniciales propuestas

1. Android nativo con Kotlin y Jetpack Compose.
2. La app habla con la API de Baboons, nunca directamente con Odoo.
3. Ubicacion enviada durante la jornada, con almacenamiento local temporal si no hay conexion.
4. Reutilizar la API actual de Baboons donde aplique y extenderla para jornada, mensajes, proyectado y productividad.

Estas decisiones son una base de trabajo y deben ajustarse a la infraestructura y operacion de la empresa.
