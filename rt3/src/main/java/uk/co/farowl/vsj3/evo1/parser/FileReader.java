package uk.co.farowl.vsj3.evo1.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provide the utilities to determine the encoding of a file and transform the raw bytes into a string.
 */
public class FileReader {

    final static String LATIN_1 = "iso-8859-1";
    final static String UTF_8 = "utf-8";

    private static final Map<String, String> encoding_names = Map.of(
            "utf-8", UTF_8,
            "latin-1", LATIN_1,
            "iso-8859-1", LATIN_1,
            "iso-latin-1", LATIN_1
    );

    /**
     *  for utf-8 and latin-1
     */
    protected static String get_normal_name(String s) {
        s = s.toLowerCase().replace('_', '-');
        if (s.length() > 13)
            s = s.substring(0, 13);
        String result = encoding_names.get(s);
        if (result != null)
            return result;
        for (String key : encoding_names.keySet()) {
            if (s.startsWith(key) && s.length() > key.length() && s.charAt(key.length()) == '-')
                return encoding_names.get(key);
        }
        return s;
    }

    /**
     * Returns `true` if the character is an alpha-numeric character, a dash, underscore or dot---i.e. if the character
     * can be part of a coding spec.
     */
    private static boolean _isExtAlpha(char ch) {
        return Character.isAlphabetic(ch) || ch == '_' || ch == '-' || ch == '.';
    }

    /**
     * Return the coding spec in `s`, or null if none is found.
     */
    protected static String get_coding_spec(String s) {
        int i = 0;
        for (i = 0; i < s.length() - 6; i++) {
            char c = s.charAt(i);
            if (c == '#')
                break;
            if (c != ' ' && c != '\t' && c != '\f')
                return null;
        }
        int j = s.indexOf("coding");
        if (0 <= j && j + 6 < s.length() && (s.charAt(j + 6) == '=' || s.charAt(j + 6) == ':')) {
            j += 7;
            while (j < s.length() && (s.charAt(j) == ' ' || s.charAt(j) == '\t'))
                j++;
            int begin = j;
            while (j < s.length() && _isExtAlpha(s.charAt(j)))
                j++;
            if (begin < j)
                return get_normal_name(s.substring(begin, j));
        }
        return null;
    }

    /**
     * Check the first three lines of the provided input for a coding spec.
     */
    protected static String check_coding_spec(byte[] input) {
        int i = 0;
        for (int line_no = 0; line_no < 3; line_no++) {
            int begin = i;
            while (i < input.length && input[i] != 0x0A)
                i++;
            if (begin < i) {
                byte[] buf = new byte[i - begin];
                System.arraycopy(input, begin, buf, 0, i - begin);
                String coding = get_coding_spec(new String(buf, StandardCharsets.US_ASCII));
                if (coding != null)
                    return coding;
                i++;
            } else
                break;
        }
        return null;
    }

    /**
     * See whether the file starts with a BOM.
     */
    protected static String check_bom(byte[] input) {
        if (input.length >= 3 && input[0] == 0xEF && input[1] == 0xBB && input[2] == 0xBF) {
            return UTF_8;
        }
        return null;
    }

    /**
     * Decodes the given bytes into a string and returns it.
     *
     * At first, the byte arrays provided is treated as containing ASCII characters and it tries to discover a Python
     * coding spec (in form of a comment) at the beginning of the file (first three lines).  If no encoding is found,
     * UTF-8 is assumed.
     *
     * The bytes are then entirely decoded using the specific encoding and returned as a Java String.
     *
     * If a byte order mark (BOM) is present, all further encoding specs are ignored and the decoding of the data
     * relies on the BOM.
     */
    public static String readFromBytes(byte[] bytes) throws UnsupportedEncodingException {
        int start = 0;
        String encoding = check_bom(bytes);
        if (encoding.equals(UTF_8)) {
            byte[] buf = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, buf, 0, bytes.length - 3);
            bytes = buf;
        } else
            encoding = check_coding_spec(bytes);
        if (encoding == null)
            encoding = UTF_8;
        return new String(bytes, encoding);
    }

    /**
     * Reads all the bytes from the given file and uses `readFromBytes` to transform it to a single String.
     */
    public static String readFromFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return readFromBytes(bytes);
    }

    /**
     * Reads all the bytes from the given file and uses `readFromBytes` to transform it to a single String.
     */
    public static String readFromFile(File file) throws IOException {
        return readFromFile(file.toPath());
    }

    /**
     * Reads all the bytes from the given stream and uses `readFromBytes` to transform it to a single String.
     */
    public static String readFromStream(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return readFromBytes(bytes);
    }
}
