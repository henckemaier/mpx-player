# Media3 e Compose trazem suas próprias regras de consumidor.

# Ktor (cliente HTTP do :shared, usado na busca de letras). A engine Android é referenciada
# diretamente em HttpClient(Android) — sem ServiceLoader —, mas mantemos as classes do Ktor e
# silenciamos avisos de dependências opcionais (logging) que não usamos, para o R8 da release.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
# OBS: usamos a API de JsonElement do kotlinx.serialization (parseToJsonElement), sem serializers
# gerados por @Serializable; portanto não são necessárias regras keep de serialização.
