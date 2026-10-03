package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.APDUHistory;

import java.nio.file.Path;

/**
 * The LOAD commands of one load file in a session's history: counted, and written as one note when the sequence
 * ends (the block with P1 b8 = 1, the last block, GPCS v2.3.1 11.6, Table 11-57), when the card answers a block with
 * an error, or before the next other entry. The transcript file keeps every block; the history, which failed tests
 * show and {@code -Djcx.log=true} prints, would otherwise fill up with the CAP file in hex.
 */
final class LoadBlocks {

    /** LOAD (GPCS v2.3.1 11.6.2.1). */
    private static final int INS_LOAD = 0xE8;
    private static final int LAST_BLOCK = 0x80;

    private int blocks;
    private int bytes;
    private int lastSw;

    /**
     * Counts a LOAD command of a proprietary class.
     *
     * @param command    the command
     * @param response   the card's response
     * @param history    where the note goes when the sequence ends
     * @param transcript the transcript, whose file the note names
     * @return true if the command was a LOAD command and is counted, false for any other command
     */
    boolean add(byte[] command, byte[] response, APDUHistory history, Transcript transcript) {
        if (command.length < 4 || (command[1] & 0xFF) != INS_LOAD || (command[0] & 0x80) == 0
                || (command[0] & 0xFF) == 0xFF) {
            return false;
        }
        blocks++;
        bytes += command.length;
        lastSw = response.length < 2 ? -1
                : ((response[response.length - 2] & 0xFF) << 8) | (response[response.length - 1] & 0xFF);
        if ((command[2] & LAST_BLOCK) != 0 || lastSw != 0x9000) {
            flush(history, transcript);
        }
        return true;
    }

    /**
     * Writes the note of the counted blocks, if any, and starts counting anew.
     *
     * @param history    where the note goes
     * @param transcript the transcript, whose file the note names
     */
    void flush(APDUHistory history, Transcript transcript) {
        if (blocks == 0) {
            return;
        }
        Path file = transcript.file();
        history.note(blocks + (blocks == 1 ? " LOAD block" : " LOAD blocks") + " (" + bytes + " bytes of commands)"
                + (lastSw < 0 ? "" : String.format(", the last answered %04X", lastSw)) + "; every block is in "
                + (file == null ? "the transcript" : "the transcript " + file.getFileName() + " (" + file + ")"));
        blocks = 0;
        bytes = 0;
        lastSw = -1;
    }
}
