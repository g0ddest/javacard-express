package name.velikodniy.jcexpress.container.applets;

/** Interface whose implementation is only reachable through the interface type in {@link InterfaceApplet}. */
public interface Counter {

    /** @return the next value */
    short next();
}
