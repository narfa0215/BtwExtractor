package org.example;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.Inflater;

public class BtwExtractor {

    private static final byte[] BTW_MAGIC_SEQUENCE = { (byte) 0x0D, 0x0A, 'B', 'a', 'r', ' ', 'T', 'e', 'n', 'd', 'e', 'r', ' ', 'F', 'o', 'r', 'm', 'a', 't', ' ', 'F', 'i', 'l', 'e', 0x0D, 0x0A };
    private static final byte[] PNG_MAGIC_START = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] PNG_MAGIC_END = { 0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
    private static final byte[] ZLIB_MAGIC = {0x00, 0x01};

    public static void main(String[] args) throws Exception {
        String filename = "D:\\Test\\test.btw";

        byte[] fileData;
        try (FileInputStream fileInputStream = new FileInputStream(filename);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Read file into byte array
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fileData = outputStream.toByteArray();
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return;
        }

        // Find BTW magic sequence
        int btwStartIndex = findSequence(fileData, BTW_MAGIC_SEQUENCE, 0);
        if (btwStartIndex == -1) {
            System.err.println("BTW magic sequence not found in the file.");
            return;
        }

        // Extract preview PNG image
        int previewPngStartIndex = findSequence(fileData, PNG_MAGIC_START, btwStartIndex);
        int previewPngEndIndex = findSequence(fileData, PNG_MAGIC_END, previewPngStartIndex);
        if (previewPngStartIndex == -1 || previewPngEndIndex == -1) {
            System.err.println("Preview PNG image not found.");
            return;
        }

        // Write prefix data to file
        try (FileOutputStream prefixOutputStream = new FileOutputStream("prefix.bin")) {
            prefixOutputStream.write(fileData, btwStartIndex, previewPngStartIndex - btwStartIndex);
            System.out.println("Prefix data extracted successfully.");
        } catch (IOException e) {
            System.err.println("Error writing prefix data to file: " + e.getMessage());
        }

        // Write preview PNG image to file
        try (FileOutputStream previewPngOutputStream = new FileOutputStream("preview.png")) {
            previewPngOutputStream.write(fileData, previewPngStartIndex, previewPngEndIndex - previewPngStartIndex);
            System.out.println("Preview PNG image extracted successfully.");
        } catch (IOException e) {
            System.err.println("Error writing preview PNG image to file: " + e.getMessage());
        }

        int maskPngStartIndex = findSequence(fileData, PNG_MAGIC_START, previewPngEndIndex);
        int maskPngEndIndex = findSequence(fileData, PNG_MAGIC_END, maskPngStartIndex);
        if (maskPngStartIndex == -1 || maskPngEndIndex == -1) {
            System.err.println("Mask PNG image not found.");
            return;
        }

        // Write extracted PNG images to files
        try (FileOutputStream maskPngOutputStream = new FileOutputStream("mask.png")) {
            maskPngOutputStream.write(fileData, maskPngStartIndex, maskPngEndIndex - maskPngStartIndex);
            System.out.println("Mask PNG image extracted successfully.");
        } catch (IOException e) {
            System.err.println("Error writing mask PNG image to file: " + e.getMessage());
        }

        // Find Zlib data
        int zlibStartIndex = findSequence(fileData, ZLIB_MAGIC, maskPngEndIndex);
        if (zlibStartIndex == -1) {
            System.err.println("Zlib magic sequence not found in the file.");
            return;
        }

        // Write decompressed Zlib data to file
        try (FileOutputStream zlibFileOutputStream = new FileOutputStream("container.bin")) {
            Inflater inflater = new Inflater();
            inflater.setInput(fileData, zlibStartIndex + ZLIB_MAGIC.length, fileData.length - zlibStartIndex - ZLIB_MAGIC.length);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                zlibFileOutputStream.write(buf, 0, count);
            }
            inflater.end();
            System.out.println("Zlib data extracted successfully.");
        } catch (IOException e) {
            System.err.println("Error writing decompressing Zlib data to file: " + e.getMessage());
        }
    }

    private static int findSequence(byte[] data, byte[] sequence, int startIndex) {
        for (int i = startIndex; i < data.length - sequence.length + 1; i++) {
            boolean found = true;
            for (int j = 0; j < sequence.length; j++) {
                if (data[i + j] != sequence[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
}
