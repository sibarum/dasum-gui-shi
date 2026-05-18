package sibarum.dasum.gui.msdf;

import java.util.Set;

/**
 * Source-library icon name → Java constant name. Implements the rules
 * documented on {@link IconConfig}. Pure function, no state.
 */
final class IconNameNormalizer {

    /** Java reserved words that we must not emit as bare constant names. */
    private static final Set<String> JAVA_RESERVED = Set.of(
        "abstract","assert","boolean","break","byte","case","catch","char","class","const",
        "continue","default","do","double","else","enum","extends","final","finally","float",
        "for","goto","if","implements","import","instanceof","int","interface","long","native",
        "new","null","package","private","protected","public","return","short","static",
        "strictfp","super","switch","synchronized","this","throw","throws","transient","true",
        "false","try","void","volatile","while","yield","record","sealed","permits","non-sealed"
    );

    private IconNameNormalizer() {}

    static String normalize(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("Icon name must be non-blank");
        }
        StringBuilder sb = new StringBuilder(sourceName.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < sourceName.length(); i++) {
            char c = sourceName.charAt(i);
            char mapped;
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                mapped = c;
            } else if (c >= 'a' && c <= 'z') {
                mapped = (char) (c - 'a' + 'A');
            } else {
                mapped = '_';
            }
            if (mapped == '_') {
                if (lastUnderscore) continue;
                lastUnderscore = true;
            } else {
                lastUnderscore = false;
            }
            sb.append(mapped);
        }
        // Strip trailing underscore from a name that ended in a separator.
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.deleteCharAt(sb.length() - 1);
        }
        if (sb.length() == 0) {
            throw new IllegalArgumentException("Icon name '" + sourceName + "' normalized to empty string");
        }
        // Digit-prefixed names get an underscore prefix so they're valid identifiers.
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, '_');
        }
        // Reserved-word collision avoidance.
        String result = sb.toString();
        if (JAVA_RESERVED.contains(result.toLowerCase())) {
            result = result + "_";
        }
        return result;
    }
}
