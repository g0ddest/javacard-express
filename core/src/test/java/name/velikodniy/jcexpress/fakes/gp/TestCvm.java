package name.velikodniy.jcexpress.fakes.gp;

import org.globalplatform.CVM;

/**
 * The service object of {@link CardStateApplet}: a CVM that only reports three remaining tries, enough to tell that
 * {@code GPSystem.getCVM} reached it.
 */
public class TestCvm implements CVM {

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isSubmitted() {
        return false;
    }

    @Override
    public boolean isVerified() {
        return false;
    }

    @Override
    public boolean isBlocked() {
        return false;
    }

    @Override
    public byte getTriesRemaining() {
        return 3;
    }

    @Override
    public boolean update(byte[] baBuffer, short sOffset, byte bLength, byte bFormat) {
        return false;
    }

    @Override
    public boolean resetState() {
        return false;
    }

    @Override
    public boolean blockState() {
        return false;
    }

    @Override
    public boolean resetAndUnblockState() {
        return false;
    }

    @Override
    public boolean setTryLimit(byte bTryLimit) {
        return false;
    }

    @Override
    public short verify(byte[] baBuffer, short sOffset, byte bLength, byte bFormat) {
        return CVM_FAILURE;
    }
}
