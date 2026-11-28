package com.mpx.player.data

/**
 * Mascara segredos antes de irem para o log local ([CrashLog]): parâmetros `token=`/`t=` em URLs,
 * cabeçalho `Bearer <x>` e os valores literais passados em [secrets]. Defesa em profundidade: o app
 * não guarda segredos hoje, mas mensagens de exceção podem trazer URLs com credenciais de terceiros.
 * PURO (String in/out) para teste em JVM.
 */
fun maskSecrets(text: String, vararg secrets: String): String {
    var s = text
    for (sec in secrets) if (sec.length >= 4) s = s.replace(sec, "***")
    s = s.replace(Regex("""(?i)([?&](?:token|t)=)[^&\s#"'<>]+"""), "$1***")
    s = s.replace(Regex("""(?i)(Bearer\s+)[^\s"'<>]+"""), "$1***")
    // URL-encoded (ex.: dentro de outro parâmetro/redirect): %3Ftoken%3D... / %26t%3D...
    s = s.replace(Regex("""(?i)((?:%3F|%26)(?:token|t)%3D)[^&\s"'<>%]+"""), "$1***")
    return s
}
