package com.github.anopensaucedev;

import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class Main {

    static int fileSizeLimit = 40_000_000;

    static int id = 0;
    static  boolean keepFiles;

    static int susFilesEncountered = 0;
    static int filesEncountered = 0;
    static int filesFailedToScan = 0;

    // JAR file scanner
    // looks for: Embedded files, usage of DLL's
    // "stretch" goals: Find URL's within JAR and via `strings` on Linux.

    public static JarFile scanFile;

    public static void main(String[] args) throws IOException {

        if(args[0] == null){
            System.err.println("ERROR: No file specified! for help, please use the arg: --help");
            return;
        }

        if(args[0].equals("--help")){
            System.err.println("Usage: java -jar TheScanMan.jar [FILENAME] [--args]");
            System.err.println("args (separate with spaces): -k (keep files), ");
            return;
        }

        // parse args
        for (int i = 1; i < args.length; i++){
            if(Objects.equals(args[i], "-k")){
                keepFiles = true;
            }
        }


            Path path = Path.of("./scanFiles");
            if(!path.toFile().isDirectory()){ // if there is no directory here, we need to make it.
                System.out.println("Creating ScanFiles directory, as it currently doesn't exist!");
                Files.createDirectory(path);
            }

            System.out.println("\033[0;37m Starting Scan!");

        scanFile = new JarFile(args[0],true);

        var benchmarkStart = System.nanoTime();

        Pattern regexPatternLibraries = Pattern.compile("\\w+\\.(dll|so)$");
        Pattern regexPatternSusArchives = Pattern.compile("\\w+\\.(zip|7z|tar.gz|tar.xz|tar)$");

        List<String> SusBinaryEntries = new ArrayList<>();
        List<String> SusArchiveEntries = new ArrayList<>();

        // Scan using the whole CPU.
        scanFile.stream().parallel().forEach(jarEntry -> {

            filesEncountered++; // entry == file

            if(regexPatternLibraries.matcher(jarEntry.getRealName()).find()){
                SusBinaryEntries.add(jarEntry.getRealName());
                System.out.println(jarEntry.getRealName() + "\033[1;33m Contains a binary library!  \033[0;37m");
                susFilesEncountered++;
            }

            if(regexPatternSusArchives.matcher(jarEntry.getRealName()).find()){
                SusArchiveEntries.add(jarEntry.getRealName());
                System.out.println(jarEntry.getRealName() + "\033[1;33m Contains a non-jar archive file!  \033[0;37m");
                susFilesEncountered++;
            }
            // InputStream javaStream = scanFile.getInputStream(jarEntry); // get jar file contents
        });


        List<String> extractedFileNames = new ArrayList<>();


        System.out.println("---### Scanning Sub-Archives ###---");

        // unpack and scan archives
        SusArchiveEntries.stream().parallel().forEach(archiveFile -> {

           id++;

            // check for 7z files
            if(Pattern.compile("\\w+\\.(7z)$").matcher(archiveFile).find()){
                try {

                    var entry = scanFile.getEntry(archiveFile);

                    long size = entry.getSize();

                    System.out.println(archiveFile + "'s size = " + (size / 1_000_000) + "MB, limit = " + fileSizeLimit / 1_000_000 + "MB.");

                    // don't bother with huge files (files over 40MB, or of unknown size)
                    if(size > fileSizeLimit || size == -1){

                        filesFailedToScan++;

                        System.out.println(archiveFile + " as an uncompressed file is huge! (or unknown size) Skipping.");

                        return;

                    }

                    byte[] bytes = scanFile.getInputStream(entry).readAllBytes();

                    String filename =  ("./scanFiles/tmp" + id + ".7z");

                    FileOutputStream fs = new FileOutputStream(filename);

                    fs.write(bytes);

                    fs.close();

                    extractedFileNames.add(filename);

                    SevenZFile sevenZFile = new SevenZFile(new File(filename));

                    // scan THOSE filenames.
                    sevenZFile.getEntries().forEach(sevenZArchiveEntry -> {

                        filesEncountered++;

                        // check for sus binaries
                        if(regexPatternLibraries.matcher(sevenZArchiveEntry.getName()).find()){
                            SusBinaryEntries.add(sevenZArchiveEntry.getName());
                            // the escape code \033 is for ANSI colouring
                            System.out.println("[ Scan for: " + archiveFile + " ]    " + sevenZArchiveEntry.getName() + " \033[1;33m Contains a binary library! \033[0;37m");
                            susFilesEncountered++;
                        }

                        // check for sus archives
                        if(regexPatternSusArchives.matcher(sevenZArchiveEntry.getName()).find()){
                            SusArchiveEntries.add(sevenZArchiveEntry.getName());
                            System.out.println(sevenZArchiveEntry.getName() + "\033[1;33m Contains a non-jar archive file! \033[0;37m");
                            susFilesEncountered++;
                        }
                        // InputStream javaStream = scanFile.getInputStream(jarEntry); // get jar file contents
                    });


                    // delete temp files
                    if(!keepFiles){
                        System.out.println("deleting temporary file: " + filename + ", as -k was not present in args.");
                        Files.delete(Path.of(filename));
                    }else {
                        System.out.println("keeping file: " + filename + ", as -k was present in args.");
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        });




        // TODO: stop doing ANSI the ugly way, please.
        System.out.println( "\033[1;33mScan Complete!\033[0;37m Time to scan from .JAR load: " + ( System.nanoTime() - benchmarkStart )/1_000_000 + "ms" );
        System.out.println(" ---### Statistics ###---");
        System.out.println("Files scanned: \033[1;33m" + filesEncountered + "  \033[0;37m");
        System.out.println("Files of suspicion: \033[1;33m" + susFilesEncountered + "  \033[0;37m");
        System.out.println("Sus%: \033[1;33m" + 100 * ((float) susFilesEncountered / (float) filesEncountered) + "  \033[0;37m");
        System.out.println("\033[1;33mFiles failed to scan: " + ((filesFailedToScan == 0) ? ("\033[1;32m0! \\(^ v ^)/") : ("\033[1;31m" + filesFailedToScan) )  + "  \033[0;37m");
    }
}