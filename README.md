# Mod Scanning Manager
<Mod ScanMan (ANSI colours edition!)>

A small program that goes through a whole JAR and checks filetypes. If it finds an archive (.7z for now) , and it 's less than 40MB raw, it will then scan those files too. (don't worry, it cleans up the temporary files it creates)

Despite this being a mess of parallel foreach loops, performance is pretty good, capable of scanning, extracting, and further scanning thousands of files in less than a second (1776 files -> 50ms).

Is this a useful tool? Not really. It won't tell you what the .class files do, but it will tell you how many strange files such as `.dll` and `.so`'s are present in a mod.

Scanning the .Jar is the easy part, and I'm sure going through the bytecode is pretty painful.

To see cli arguments use `--help`.