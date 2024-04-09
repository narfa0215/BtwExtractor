package org.example;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Inflater;

public class BtwExtractor {

    private static final byte[] PREFIX_START_MAGIC_SEQUENCE = {(byte) 0x0D, 0x0A, 'B', 'a', 'r', ' ', 'T', 'e', 'n', 'd', 'e', 'r', ' ', 'F', 'o', 'r', 'm', 'a', 't', ' ', 'F', 'i', 'l', 'e', 0x0D, 0x0A};
    private static final byte[] PREFIX_END_MAGIC_SEQUENCE = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFF, 0x00};
    private static final byte[] PADDING_MAGIC_SEQUENCE = {0x00, 0x00, 0x00, 0x00};
    private static final byte[] ZLIB_MAGIC_SEQUENCE = {0x00, 0x01};
    private static final byte[] SHARE_NAME_SEQUENCE = {'S', '\0', 'h', '\0', 'a', '\0', 'r', '\0', 'e', '\0', ' ', '\0', 'N', '\0', 'a', '\0', 'm', '\0', 'e', '\0'};
    private static final byte[] SCREEN_DATA_SEQUENCE = {'S', '\0', 'c', '\0', 'r', '\0', 'e', '\0', 'e', '\0', 'n', '\0', ' ', '\0', 'D', '\0', 'a', '\0', 't', '\0', 'a', '\0'};
    private static final byte[] EMBEDDED_SUBSTRING_SEQUENCE = {(byte) 0xFF, (byte) 0xFF, 0x03, (byte) 0x80};
    private static final byte[] STRING_SEQUENCE = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFF};
    private static final byte[] STRING_EXTENDED_SEQUENCE = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF};
    private static final int STRING_SEQUENCE_LENGTH_TYPE_LENGTH = 1;
    private static final int STRING_EXTENDED_SEQUENCE_LENGTH_TYPE_LENGTH = 2;
    private static final int PNG_SEQUENCE_LENGTH_TYPE_LENGTH = 4;
    public static void main(String[] args) throws Exception {
        String filename = "test2.btw";
        boolean isExtract = true;

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

        // Find prefix data
        int prefixStartIndex = findSequence(fileData, PREFIX_START_MAGIC_SEQUENCE, 0);
        int prefixEndIndex = findSequence(fileData, PREFIX_END_MAGIC_SEQUENCE, prefixStartIndex) + PREFIX_END_MAGIC_SEQUENCE.length;
        if (prefixStartIndex == -1 || prefixEndIndex == -1) {
            System.err.println("Prefix data not found.");
            return;
        }

        if (isExtract) {
            // Write prefix data to file
            try (FileOutputStream prefixOutputStream = new FileOutputStream("prefix.bin")) {
                prefixOutputStream.write(fileData, prefixStartIndex, prefixEndIndex - prefixStartIndex);
                System.out.println("Prefix data extracted successfully.");
            } catch (IOException e) {
                System.err.println("Error writing prefix data to file: " + e.getMessage());
            }
        }

        int prefixPaddingStartIndex = skipPadding(fileData, prefixEndIndex);

        if (prefixPaddingStartIndex == -1) {
            System.err.println("Prefix padding not found.");
            return;
        }

        int previewPngStartIndex = prefixPaddingStartIndex + PNG_SEQUENCE_LENGTH_TYPE_LENGTH;
        int previewPngEndIndex = previewPngStartIndex + calculateLittleEndianPngSequenceLength(fileData, prefixPaddingStartIndex);

        if (isExtract && previewPngEndIndex - previewPngStartIndex > 0) {
            // Write preview PNG image to file
            try (FileOutputStream previewPngOutputStream = new FileOutputStream("preview.png")) {
                previewPngOutputStream.write(fileData, previewPngStartIndex, previewPngEndIndex - previewPngStartIndex);
                System.out.println("Preview PNG image extracted successfully.");
            } catch (IOException e) {
                System.err.println("Error writing preview PNG image to file: " + e.getMessage());
            }
        }

        int maskPngStartIndex = previewPngEndIndex + PNG_SEQUENCE_LENGTH_TYPE_LENGTH;
        int maskPngEndIndex = maskPngStartIndex + calculateLittleEndianPngSequenceLength(fileData, previewPngEndIndex);

        if (isExtract && maskPngEndIndex - maskPngStartIndex > 0) {
            // Write mask PNG image to file
            try (FileOutputStream maskPngOutputStream = new FileOutputStream("mask.png")) {
                maskPngOutputStream.write(fileData, maskPngStartIndex, maskPngEndIndex - maskPngStartIndex);
                System.out.println("Mask PNG image extracted successfully.");
            } catch (IOException e) {
                System.err.println("Error writing mask PNG image to file: " + e.getMessage());
            }
        }

        int pngPaddingStartIndex = skipPadding(fileData, maskPngEndIndex);

        if (pngPaddingStartIndex == -1) {
            System.err.println("PNG image padding not found.");
            return;
        }

        int zlibStartIndex = skipZlib(fileData, pngPaddingStartIndex);

        if (zlibStartIndex == -1) {
            System.err.println("Zlib data not found.");
            return;
        }

        if (isExtract) {
            // Write extracted zlib data to file
            try (FileOutputStream zlibOutputStream = new FileOutputStream("container.zlib")) {
                zlibOutputStream.write(fileData, zlibStartIndex, fileData.length - zlibStartIndex);
                System.out.println("Zlib data extracted successfully.");
            } catch (IOException e) {
                System.err.println("Error writing zlib data to file: " + e.getMessage());
            }
        }

        // Decompress zlib data
        byte[] containerData;
        try (ByteArrayOutputStream containerOutputStream = new ByteArrayOutputStream()) {
            Inflater inflater = new Inflater();
            inflater.setInput(fileData, zlibStartIndex, fileData.length - zlibStartIndex);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                containerOutputStream.write(buf, 0, count);
            }
            inflater.end();
            containerData = containerOutputStream.toByteArray();
        } catch (IOException e) {
            System.err.println("Error decompressing zlib data: " + e.getMessage());
            return;
        }

        if (isExtract) {
            // Write extracted zlib data to file
            try (FileOutputStream zlibOutputStream = new FileOutputStream("container.bin")) {
                zlibOutputStream.write(containerData, 0, containerData.length);
                System.out.println("Container data extracted successfully.");
            } catch (IOException e) {
                System.err.println("Error writing container data to file: " + e.getMessage());
            }
        }

        // Find flag string data
        for (int flagStringStartIndex = -1; ;) {
            int minFlagStringStartIndex = -1;
            int shareNameStartIndex = findSequence(containerData, SHARE_NAME_SEQUENCE, flagStringStartIndex + 1);
            int screenDataStartIndex = findSequence(containerData, SCREEN_DATA_SEQUENCE, flagStringStartIndex + 1);
            int embeddedSubstringStartIndex = findSequence(containerData, EMBEDDED_SUBSTRING_SEQUENCE, flagStringStartIndex + 1);
            if (shareNameStartIndex != -1) {
                if (minFlagStringStartIndex != -1) {
                    minFlagStringStartIndex = Math.min(minFlagStringStartIndex, shareNameStartIndex);
                } else {
                    minFlagStringStartIndex = shareNameStartIndex;
                }
            }
            if (screenDataStartIndex != -1) {
                if (minFlagStringStartIndex != -1) {
                    minFlagStringStartIndex = Math.min(minFlagStringStartIndex, screenDataStartIndex);
                } else {
                    minFlagStringStartIndex = screenDataStartIndex;
                }
            }
            if (embeddedSubstringStartIndex != -1) {
                if (minFlagStringStartIndex != -1) {
                    minFlagStringStartIndex = Math.min(minFlagStringStartIndex, embeddedSubstringStartIndex);
                } else {
                    minFlagStringStartIndex = embeddedSubstringStartIndex;
                }
            }
            if (minFlagStringStartIndex == -1) {
                break;
            }
            flagStringStartIndex = minFlagStringStartIndex;
            int nextStringSequenceStartIndex = findNextStringSequence(containerData, flagStringStartIndex);
            String namedSubStringName = getStringFromStringSequence(containerData, nextStringSequenceStartIndex);
            if (namedSubStringName != null && namedSubStringName.isEmpty()) {
                continue;
            }
            for (int i = 0; ;i++){
                if ((nextStringSequenceStartIndex = findNextStringSequence(containerData, nextStringSequenceStartIndex + 1)) == -1) {
                    break;
                }
                if (i >= 22) {
                    String namedSubStringValue = getStringFromStringSequence(containerData, nextStringSequenceStartIndex);
                    System.out.println(namedSubStringName + ":" + namedSubStringValue);
                    break;
                }
            }
        }
    }

    private static int findSequence(byte[] data, byte[] sequence, int startIndex) {
        if (startIndex < 0 || startIndex > data.length - sequence.length) {
            return -1;
        }
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

    private static int skipPadding(byte[] data, int startIndex) {
        if (startIndex < 0 || startIndex > data.length - PADDING_MAGIC_SEQUENCE.length) {
            return -1;
        }
        boolean found = true;
        for (int j = 0; j < PADDING_MAGIC_SEQUENCE.length; j++) {
            if (data[startIndex + j] != PADDING_MAGIC_SEQUENCE[j]) {
                found = false;
                break;
            }
        }
        if (found) {
            return startIndex + PADDING_MAGIC_SEQUENCE.length;
        }
        return -1;
    }

    private static int skipZlib(byte[] data, int startIndex) {
        if (startIndex < 0 || startIndex > data.length - ZLIB_MAGIC_SEQUENCE.length) {
            return -1;
        }
        boolean found = true;
        for (int j = 0; j < ZLIB_MAGIC_SEQUENCE.length; j++) {
            if (data[startIndex + j] != ZLIB_MAGIC_SEQUENCE[j]) {
                found = false;
                break;
            }
        }
        if (found) {
            return startIndex + ZLIB_MAGIC_SEQUENCE.length;
        }
        return -1;
    }

    private static int calculateLittleEndianPngSequenceLength(byte[] data, int startIndex) {
        if (startIndex < 0 || startIndex + PNG_SEQUENCE_LENGTH_TYPE_LENGTH > data.length) {
            return -1;
        }
        int littleEndianStringLength = 0;
        for (int i = 0; i < PNG_SEQUENCE_LENGTH_TYPE_LENGTH; i++) {
            littleEndianStringLength |= ((data[startIndex + i] & 0xFF) << (8 * i));
        }
        return littleEndianStringLength;
    }

    private static int findNextStringSequence(byte[] data, int startIndex) {
        int nextStringSequenceStartIndex = findSequence(data, STRING_SEQUENCE, startIndex);
        if (nextStringSequenceStartIndex == -1) {
            return -1;
        }
        if (nextStringSequenceStartIndex == startIndex) {
            return findSequence(data, STRING_SEQUENCE, nextStringSequenceStartIndex + 1);
        }
        return nextStringSequenceStartIndex;
    }

    private static int calculateLittleEndianStringSequenceLength(byte[] data, int startIndex, int length) {
        if (length > 2) {
            return -1;
        }
        if (startIndex + length > data.length) {
            return -1;
        }
        int littleEndianStringLength = 0;
        for (int i = 0; i < length; i++) {
            littleEndianStringLength |= ((data[startIndex + i] & 0xFF) << (8 * i));
        }
        return littleEndianStringLength;
    }

    private static String getStringFromStringSequence(byte[] data, int startIndex) {
        if (startIndex + STRING_SEQUENCE.length > data.length) {
            return null;
        }
        for (int i = 0; i < STRING_SEQUENCE.length; i++) {
            if (data[startIndex + i] != STRING_SEQUENCE[i]) {
                return null;
            }
        }
        if (data[startIndex + STRING_EXTENDED_SEQUENCE.length - 1] == STRING_EXTENDED_SEQUENCE[STRING_EXTENDED_SEQUENCE.length - 1]) {
            if (startIndex + STRING_EXTENDED_SEQUENCE.length + STRING_EXTENDED_SEQUENCE_LENGTH_TYPE_LENGTH > data.length) {
                return null;
            }
            return new String(data, startIndex + STRING_EXTENDED_SEQUENCE.length + STRING_EXTENDED_SEQUENCE_LENGTH_TYPE_LENGTH, calculateLittleEndianStringSequenceLength(data, startIndex + STRING_EXTENDED_SEQUENCE.length, STRING_EXTENDED_SEQUENCE_LENGTH_TYPE_LENGTH) * 2, StandardCharsets.UTF_16LE);
        } else {
            if (startIndex + STRING_SEQUENCE.length + STRING_SEQUENCE_LENGTH_TYPE_LENGTH > data.length) {
                return null;
            }
            return new String(data, startIndex + STRING_SEQUENCE.length + STRING_SEQUENCE_LENGTH_TYPE_LENGTH, calculateLittleEndianStringSequenceLength(data, startIndex + STRING_SEQUENCE.length, STRING_SEQUENCE_LENGTH_TYPE_LENGTH) * 2, StandardCharsets.UTF_16LE);
        }
    }
}
