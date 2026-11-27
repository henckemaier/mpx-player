# Media3 e Compose trazem suas próprias regras de consumidor.

# youtubedl-android -> commons-compress: na 1ª inicialização (YoutubeDL.init -> initPython ->
# ZipUtils.unzip) o Python embarcado é descompactado com org.apache.commons.compress ZipFile.
# O ExtraFieldUtils.<clinit> registra as implementações de ZipExtraField e as instancia por
# REFLEXÃO (Class.newInstance). Como nada no código as instancia diretamente, o R8 tornava
# AsiExtraField & cia. ABSTRATAS => "class ... is not a concrete class" ->
# ExceptionInInitializerError e o app fechava ao baixar (crash do downloader, logcat RMX5085).
# A lib não traz regras de consumidor para isso.
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
