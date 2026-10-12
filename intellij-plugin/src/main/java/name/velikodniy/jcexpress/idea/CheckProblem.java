package name.velikodniy.jcexpress.idea;

/**
 * An error the check reports, where javac places it.
 *
 * @param path    the source file
 * @param start   the offset of the first character in the file's text
 * @param end     the offset after the last character, at least {@code start}
 * @param message the check's message, {@code Java Card: ...}
 */
record CheckProblem(String path, int start, int end, String message) {

    /**
     * Returns the range to mark in a text of the given length: never empty (an empty range would not show), never
     * past the end.
     *
     * @param text the file's text now
     * @return start and end offsets
     */
    int[] range(CharSequence text) {
        int length = text.length();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(from, Math.min(end, length));
        if (to == from) {
            to = from;
            while (to < length && Character.isJavaIdentifierPart(text.charAt(to))) {
                to++;
            }
            if (to == from && from < length) {
                to = from + 1;
            }
        }
        return new int[] {from, to};
    }
}
