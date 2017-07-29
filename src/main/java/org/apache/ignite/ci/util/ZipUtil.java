package org.apache.ignite.ci.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.ignite.ci.HelperConfig;

/** Unzips files to specified folder */
public class ZipUtil {

    public static List<File> unZipToSameFolder(File zipFile) {
        return unZip(zipFile, zipFile.getParentFile());
    }

    public static List<File> unZip(File zipFile, File outputFolder) {
        List<File> result = new ArrayList<>();
        try {
            //get the zip file content
            try (ZipInputStream zis =
                     new ZipInputStream(new FileInputStream(zipFile))){
                //get the zipped file list entry
                ZipEntry ze = zis.getNextEntry();

                while (ze != null) {

                    String fileName = ze.getName();
                    File newFile = new File(outputFolder + File.separator + fileName);
                    System.out.println("file unzip : " + newFile.getAbsoluteFile());

                    HelperConfig.ensureDirExist(new File(newFile.getParent()));
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    result.add(newFile);
                    ze = zis.getNextEntry();
                }
                zis.closeEntry();
            }  
            return result;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }
}
