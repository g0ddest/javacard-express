package name.velikodniy.jcexpress.container.applets;

/** {@link Counter} that counts in steps of 0x10. */
public final class StepCounter implements Counter {

    private short value;

    @Override
    public short next() {
        value += 0x10;
        return value;
    }
}
