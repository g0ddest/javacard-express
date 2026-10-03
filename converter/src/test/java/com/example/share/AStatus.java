package com.example.share;

/**
 * Public interface that is not shareable. Its name sorts first, but in an applet package the
 * shareable interfaces must receive the lowest class tokens because the Export component lists
 * only them and indexes them by class token (JCVM 3.1 §4.3.7.2, §6.13).
 */
public interface AStatus {
    short status();
}
