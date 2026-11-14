#!/bin/bash
# Manual build chain for crystal_multiplier 1.20.1 (Gradle is unusable in this sandbox).
# usage: build.sh <version>   e.g. build.sh 1.3.0
set -e
MCVER=1.20.1
VER="${1:?version required}"
PROJ=/root/java-mod-dev/crystal_multiplier
G=/data/data/dsh.android.client/files/linux/ubuntu/root/.gradle
JAVA=/usr/lib/jvm/java-17-openjdk-arm64
FART=$G/caches/forge_gradle/maven_downloader/net/minecraftforge/ForgeAutoRenamingTool/1.0.2/ForgeAutoRenamingTool-1.0.2-all.jar
FORGE=$G/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.2.0_mapped_official_1.20.1/forge-1.20.1-47.2.0_mapped_official_1.20.1.jar
cd "$PROJ"
CP="$FORGE:$(find $G/caches/modules-2 -name '*.jar' | tr '\n' ':')$(find /root/libs -maxdepth 1 -name '*.jar' | tr '\n' ':')"
rm -rf /tmp/mbuild/classes && mkdir -p /tmp/mbuild/classes
find src/main/java -name '*.java' > /tmp/mbuild/srcs.txt
echo "[1/3] javac ($(wc -l < /tmp/mbuild/srcs.txt) files)"
$JAVA/bin/javac -nowarn -proc:none -encoding UTF-8 -source 17 -target 17 -cp "$CP" -d /tmp/mbuild/classes @/tmp/mbuild/srcs.txt
echo "[2/3] pack dev jar"
python3 - "$VER" <<'PYEOF'
import os, sys, zipfile
proj="/root/java-mod-dev/crystal_multiplier"
ver=sys.argv[1]
out="/tmp/mbuild/dev-%s.jar"%ver
count=0
with zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED) as z:
    z.writestr("META-INF/MANIFEST.MF","Manifest-Version: 1.0\nCreated-By: manual javac build\nMixinConfigs: crystal_multiplier.mixins.json\n\n"); count+=1
    for root,dirs,files in os.walk("/tmp/mbuild/classes"):
        for f in files:
            full=os.path.join(root,f); arc=os.path.relpath(full,"/tmp/mbuild/classes")
            z.write(full,arc); count+=1
    res=os.path.join(proj,"src","main","resources")
    for root,dirs,files in os.walk(res):
        for f in files:
            full=os.path.join(root,f); arc=os.path.relpath(full,res)
            if arc.replace(os.sep,"/")=="META-INF/mods.toml":
                data=open(full,encoding="utf-8").read().replace("${mod_version}",ver)
                z.writestr(arc,data.encode("utf-8"))
            else:
                z.write(full,arc)
            count+=1
print("  dev entries",count,"->",out)
PYEOF
echo "[3/3] reobfuscate official -> srg (FART)"
LIBS=""
for j in /root/libs/*.jar $(find $G/caches/modules-2 -name '*.jar'); do LIBS="$LIBS --lib $j"; done
$JAVA/bin/java -jar $FART --input /tmp/mbuild/dev-$VER.jar --output build/libs/crystal_multiplier-$VER-mc1.20.1.jar --map build/reobfJar/mappings.tsrg --lib "$FORGE" $LIBS > /tmp/fart.log 2>&1
grep "Can't Find Class" /tmp/fart.log || true
python3 - "$VER" <<'PYEOF'
import zipfile, sys
ver=sys.argv[1]
z=zipfile.ZipFile("/root/java-mod-dev/crystal_multiplier/build/libs/crystal_multiplier-%s-mc1.20.1.jar"%ver)
n=z.namelist()
print("  final entries",len(n),"classes",len([x for x in n if x.endswith('.class')]),"size",__import__("os").path.getsize("/root/java-mod-dev/crystal_multiplier/build/libs/crystal_multiplier-%s-mc1.20.1.jar"%ver))
print("  testzip", z.testzip())
print("  toml", [l for l in z.read("META-INF/mods.toml").decode().splitlines() if l.startswith("version")])
PYEOF
