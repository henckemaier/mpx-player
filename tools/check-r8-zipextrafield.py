#!/usr/bin/env python3
"""
Anti-regressao do R8: implementacoes de ZipExtraField precisam ser concretas e instanciaveis.

Contexto (crash da 1.6.0): o youtubedl-android descompacta o Python embarcado via
commons-compress; ExtraFieldUtils registra AsiExtraField, X5455_ExtendedTimestamp,
X7875_NewUnix, JarMarker... e as instancia por REFLEXAO (Class.newInstance). Como nada as
instancia diretamente, o R8 as tornava ABSTRATAS -> "is not a concrete class" ->
ExceptionInInitializerError e o app fechava ao baixar. A correcao esta em
app/proguard-rules.pro:
    -keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField { public <init>(); }

Este script le o APK minificado e FALHA (exit 1) se alguma classe que implemente
ZipExtraField (direta ou indiretamente) estiver abstrata/interface ou sem <init>() publico,
ou se alguma das classes obrigatorias (as 4 acima) nao existir.

USO
    python3 tools/check-r8-zipextrafield.py <apk> [mapping.txt] [--dexdump CAMINHO]

    <apk>          APK/AAB-apk release minificado (ex.: app/build/outputs/apk/release/app-release.apk)
    mapping.txt    opcional; traduz nome original -> ofuscado (app/build/outputs/mapping/release/mapping.txt).
                   Sem ele, so funciona se os nomes nao estiverem ofuscados.
    --dexdump      caminho do dexdump; senao usa $DEXDUMP, depois build-tools mais recente em
                   $ANDROID_HOME / $ANDROID_SDK_ROOT / %LOCALAPPDATA%\\Android\\Sdk, depois o PATH.

CODIGOS DE SAIDA
    0  ok (todas concretas, com <init>() publico)
    1  REGRESSAO detectada
    2  erro de uso/ambiente (dexdump ausente, interface nao encontrada, APK invalido...)

Somente biblioteca padrao do Python 3. Exemplo de uso no CI (apos ./gradlew assembleRelease):
    python3 tools/check-r8-zipextrafield.py \\
        app/build/outputs/apk/release/app-release.apk \\
        app/build/outputs/mapping/release/mapping.txt
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

IFACE = "org.apache.commons.compress.archivers.zip.ZipExtraField"
PKG = "org.apache.commons.compress.archivers.zip."
REQUIRED = [PKG + n for n in ("AsiExtraField", "X5455_ExtendedTimestamp", "X7875_NewUnix", "JarMarker")]

ACC_PUBLIC = 0x0001
ACC_INTERFACE = 0x0200
ACC_ABSTRACT = 0x0400


def fail_usage(msg):
    print(f"ERRO: {msg}", file=sys.stderr)
    sys.exit(2)


def find_dexdump(explicit):
    if explicit:
        return explicit
    if os.environ.get("DEXDUMP"):
        return os.environ["DEXDUMP"]
    exe = "dexdump.exe" if os.name == "nt" else "dexdump"
    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    if os.environ.get("LOCALAPPDATA"):
        roots.append(os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk"))
    roots.append(os.path.expanduser("~/Android/Sdk"))
    for root in filter(None, roots):
        cands = glob.glob(os.path.join(root, "build-tools", "*", exe))
        if cands:
            # "34.0.0" < "36.0.0" numericamente; ordena por tupla de ints
            def key(p):
                v = os.path.basename(os.path.dirname(p))
                return [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", v)]
            return sorted(cands, key=key)[-1]
    return shutil.which("dexdump") or fail_usage(
        "dexdump nao encontrado (use --dexdump, $DEXDUMP ou defina ANDROID_HOME)")


def load_mapping(path, wanted):
    """Retorna {nome_original: nome_ofuscado} so para os nomes pedidos."""
    out = {}
    if not path:
        return out
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith(("#", " ", "\t")) or " -> " not in line:
                continue
            orig, _, obf = line.rstrip("\n").partition(" -> ")
            if orig in wanted or orig.startswith(PKG):
                out[orig] = obf.rstrip(":")
    return out


def desc(name):  # a.b.C -> La/b/C;
    return "L" + name.replace(".", "/") + ";"


def parse_dex(text):
    """Gera dicts por classe: descriptor, access, super, interfaces, init_access (ou None)."""
    for block in text.split("\nClass #")[1:]:
        m = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
        if not m:
            continue
        acc = re.search(r"Access flags\s*:\s*0x([0-9a-fA-F]+)", block)
        sup = re.search(r"Superclass\s*:\s*'([^']+)'", block)
        ifs = []
        im = re.search(r"Interfaces\s*-\n(.*?)(?:\n  \w[^\n]*-\n|\Z)", block, re.S)
        if im:
            ifs = re.findall(r"#\d+\s*:\s*'([^']+)'", im.group(1))
        init = re.search(
            r"name\s*:\s*'<init>'\s*\n\s*type\s*:\s*'\(\)V'\s*\n\s*access\s*:\s*0x([0-9a-fA-F]+)", block)
        yield {
            "d": m.group(1),
            "access": int(acc.group(1), 16) if acc else 0,
            "super": sup.group(1) if sup else None,
            "ifs": ifs,
            "init": int(init.group(1), 16) if init else None,
        }


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    dexdump_opt = None
    if "--dexdump" in argv:
        i = argv.index("--dexdump")
        if i + 1 >= len(argv):
            fail_usage("--dexdump precisa de um caminho")
        dexdump_opt = argv[i + 1]
        args = [a for a in args if a != dexdump_opt]
    if not args or len(args) > 2 or any(a in ("-h", "--help") for a in argv):
        print(__doc__)
        return 2
    apk = args[0]
    mapping = args[1] if len(args) > 1 else None
    if not os.path.isfile(apk):
        fail_usage(f"APK nao encontrado: {apk}")
    if mapping and not os.path.isfile(mapping):
        fail_usage(f"mapping nao encontrado: {mapping}")
    dexdump = find_dexdump(dexdump_opt)

    names = load_mapping(mapping, {IFACE, *REQUIRED})
    iface_d = desc(names.get(IFACE, IFACE))
    required_d = {desc(names.get(n, n)): n for n in REQUIRED}
    pretty = {desc(v): k for k, v in names.items()}

    def show(d):
        return f"{pretty[d]} ({d})" if d in pretty and pretty[d] not in d else d

    classes = {}
    with tempfile.TemporaryDirectory() as tmp:
        try:
            with zipfile.ZipFile(apk) as z:
                dexes = [n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
                if not dexes:
                    fail_usage("nenhum classes*.dex no APK")
                for n in dexes:
                    z.extract(n, tmp)
        except zipfile.BadZipFile:
            fail_usage(f"APK invalido: {apk}")
        for n in dexes:
            p = subprocess.run([dexdump, os.path.join(tmp, n)], capture_output=True)
            if p.returncode != 0:
                fail_usage(f"dexdump falhou em {n}: {p.stderr.decode(errors='replace')[:300]}")
            # dexdump.exe emite CRLF no Windows; normaliza para LF
            text = "\n".join(p.stdout.decode("utf-8", errors="replace").splitlines())
            for c in parse_dex(text):
                classes[c["d"]] = c

    if iface_d not in classes:
        fail_usage(f"interface ZipExtraField ({iface_d}) nao encontrada no dex. Se o R8 a ofuscou, "
                   "passe o mapping.txt; se a dependencia saiu do app, atualize este script.")

    # Fecho transitivo: interfaces que estendem ZipExtraField, classes que as implementam e subclasses.
    marked = {iface_d}
    changed = True
    while changed:
        changed = False
        for d, c in classes.items():
            if d in marked:
                continue
            if any(i in marked for i in c["ifs"]) or (c["super"] in marked):
                marked.add(d)
                changed = True
    impls = {d: classes[d] for d in marked if d != iface_d and not (classes[d]["access"] & ACC_INTERFACE)}

    problems = []
    for d, c in sorted(impls.items()):
        if c["access"] & ACC_ABSTRACT:
            # Superclasses abstratas legitimas nao sao instanciadas; so reprova se for uma das obrigatorias
            # ou se nenhuma subclasse concreta existe? Mantemos simples: abstrata so e erro se
            # for obrigatoria OU nao tiver filhos (classe "folha" abstrata = quebrada pelo R8).
            has_child = any(o["super"] == d for o in impls.values())
            if d in required_d or not has_child:
                problems.append(f"{show(d)}: ABSTRATA (flags 0x{c['access']:04x})")
        elif c["init"] is None:
            problems.append(f"{show(d)}: sem <init>() (construtor sem argumentos removido)")
        elif not (c["init"] & ACC_PUBLIC):
            problems.append(f"{show(d)}: <init>() nao e publico (flags 0x{c['init']:x})")
    for d, orig in required_d.items():
        if d not in impls:
            problems.append(f"{orig} ({d}): classe obrigatoria AUSENTE ou nao implementa ZipExtraField")

    print(f"ZipExtraField = {iface_d} | implementacoes verificadas: {len(impls)}")
    for d in sorted(impls):
        c = impls[d]
        tag = "OK " if not any(p.startswith(show(d) + ":") for p in problems) else "BAD"
        print(f"  [{tag}] {show(d)} flags=0x{c['access']:04x} init={'-' if c['init'] is None else hex(c['init'])}")
    if problems:
        print("\nFALHA: regressao do R8 em ZipExtraField (ver app/proguard-rules.pro):", file=sys.stderr)
        for p in problems:
            print("  - " + p, file=sys.stderr)
        return 1
    print("OK: todas as implementacoes de ZipExtraField sao concretas e instanciaveis.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
