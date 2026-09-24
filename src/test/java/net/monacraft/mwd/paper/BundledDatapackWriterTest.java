package net.monacraft.mwd.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.*;

class BundledDatapackWriterTest {
    @TempDir Path temp;

    @Test void combinesIsolatedGeneratedNamespacesIntoOnePaperPack() throws IOException {
        Path first=zip("first.zip",Map.of("pack.mcmeta","{\"description\":\"first\"}", "mwd-manifest.json","first",
                "data/mwd_a/dimension/a.json","{}"));
        Path second=zip("second.zip",Map.of("pack.mcmeta","{\"description\":\"second\"}", "mwd-manifest.json","second",
                "data/mwd_b/dimension/b.json","{}"));
        Path output=new BundledDatapackWriter().write(List.of(first,second),temp.resolve("bundle.zip"));
        try(ZipFile zip=new ZipFile(output.toFile())){
            assertThat(zip.getEntry("data/mwd_a/dimension/a.json")).isNotNull();
            assertThat(zip.getEntry("data/mwd_b/dimension/b.json")).isNotNull();
            assertThat(zip.getEntry("mwd-manifest.json")).isNull();
            assertThat(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes()))
                    .contains("first");
        }
    }

    @Test void rejectsDifferentResourcesWithTheSameOutputPath() throws IOException {
        Path first=zip("first.zip",Map.of("pack.mcmeta","{}", "data/mwd_a/dimension/a.json","{}"));
        Path second=zip("second.zip",Map.of("pack.mcmeta","{}", "data/mwd_a/dimension/a.json","{\"different\":true}"));
        assertThatThrownBy(()->new BundledDatapackWriter().write(List.of(first,second),temp.resolve("bundle.zip")))
                .isInstanceOf(IOException.class).hasMessageContaining("bundle conflict");
    }

    private Path zip(String name,Map<String,String> files)throws IOException{
        Path path=temp.resolve(name);
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(path))){
            for(var file:files.entrySet()){out.putNextEntry(new ZipEntry(file.getKey()));out.write(file.getValue().getBytes());out.closeEntry();}
        }
        return path;
    }
}
