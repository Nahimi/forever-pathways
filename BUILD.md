# Building the jar

Forever Pathways is a NeoForge 1.21.1 mod and builds with the Gradle wrapper that ships in this
folder. You need **JDK 21** and an internet connection the first time, because NeoForge and
Minecraft's own libraries are downloaded from `maven.neoforged.net` on the first build.

## One command

Windows (PowerShell or Command Prompt), from inside this folder:

```
gradlew.bat build
```

macOS or Linux:

```
./gradlew build
```

The first build takes a few minutes while it downloads NeoForge 21.1.256 and decompiles Minecraft
1.21.1. Later builds take seconds.

## Where the jar lands

```
build/libs/foreverpathways-<version>.jar
```

The version comes from `mod_version` in `gradle.properties`. Copy that one file into your `mods`
folder. Ignore any `-sources` or `-slim` jar sitting beside it.

## What else goes in the mods folder

Required:

- **NeoForge** for Minecraft 1.21.1 (21.1.256 or later)
- **ProgressiveStages 3**, which Pathways writes its stage files for

Optional but assumed by most of the tree:

- **Patchouli**, which holds the Forever Grimoire. Without it the guide is simply absent
- **Legendary Tabs**, which gives the Pathways and Grimoire tabs. Without it, press **O** for the
  ring and right-click the book for the guide
- the rest of the Forever Server pack: Create, PowerGrid, Iron's Spellbooks, Supplementaries,
  Handcrafted, Farmer's Delight, Icy's Better Horses (needs GeckoLib, Modonomicon and Cloth Config),
  Sleep Tight, Envelope, Chalk, Thief, Scholar and the others listed as optional dependencies in `neoforge.mods.toml`

The tree is built so that it can be finished on vanilla plus ProgressiveStages alone. Every other mod
adds to it. Run `python3 tools/vanilla_check.py` to see that proved for any set of mods.

## If the build fails

- **"Could not resolve net.neoforged:neoforge"**: no internet, or a proxy or firewall is blocking
  `maven.neoforged.net`. The first build cannot work offline.
- **"invalid source release: 21"** or similar: Gradle picked up an older JDK. Check `java -version`
  and set `JAVA_HOME` to a JDK 21 installation.
- **Out of memory**: add `org.gradle.jvmargs=-Xmx3G` to `gradle.properties`.

## Regenerating the data

Both of these are checked in already, so you only need them after changing the tree.

```
python3 tools/check_tree.py                 # validates tree.json
python3 tools/vanilla_check.py              # proves it can be finished
python3 tools/build_book.py                 # rebuilds the Grimoire from the tree
python3 tools/build_preview.py              # rebuilds preview/data.js
python3 tools/build_sprites.py <tiles dir>  # redraws the GUI pixel art
```
