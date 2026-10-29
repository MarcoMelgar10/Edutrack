package com.edutrack.desktop.ui.theme;

import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Makes the native Windows title bar follow the app theme, so dark mode does not keep a white
 * strip on top of the window. Uses the Foreign Function API (no extra dependency) to call
 * {@code DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE)} on Windows 10 20H1+ / 11.
 *
 * <p>Purely cosmetic: on other platforms, older Windows builds or any native failure it does nothing.
 */
final class WindowsTitleBar {

    private static final Logger log = LoggerFactory.getLogger(WindowsTitleBar.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private WindowsTitleBar() {
    }

    /** Applies the title bar color to a showing window, found by its title within this process. */
    static void apply(Window window, String title, boolean dark) {
        if (!IS_WINDOWS || !window.isShowing() || title == null) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hwnd = findOwnWindow(arena, title);
            if (hwnd.address() == 0) {
                return;
            }
            MemorySegment value = arena.allocate(JAVA_INT);
            value.set(JAVA_INT, 0, dark ? 1 : 0);
            int hr = (int) Native.DWM_SET_WINDOW_ATTRIBUTE.invokeExact(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4);
            // Repaint the non-client area now instead of on the next activation change.
            int repainted = (int) Native.SET_WINDOW_POS.invokeExact(hwnd, MemorySegment.NULL, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
            log.debug("Title bar dark={} (hr={}, repainted={})", dark, hr, repainted);
        } catch (Throwable failure) {
            log.debug("Could not style the Windows title bar", failure);
        }
    }

    /** Walks top-level windows with this title and returns the one owned by the current process. */
    private static MemorySegment findOwnWindow(Arena arena, String title) throws Throwable {
        MemorySegment wideTitle = arena.allocateFrom(title, StandardCharsets.UTF_16LE);
        MemorySegment processId = arena.allocate(JAVA_INT);
        long ownPid = ProcessHandle.current().pid();

        MemorySegment hwnd = MemorySegment.NULL;
        while (true) {
            hwnd = (MemorySegment) Native.FIND_WINDOW_EX.invokeExact(MemorySegment.NULL, hwnd, MemorySegment.NULL, wideTitle);
            if (hwnd.address() == 0) {
                return MemorySegment.NULL;
            }
            int threadId = (int) Native.GET_WINDOW_THREAD_PROCESS_ID.invokeExact(hwnd, processId);
            if (threadId != 0 && Integer.toUnsignedLong(processId.get(JAVA_INT, 0)) == ownPid) {
                return hwnd;
            }
        }
    }

    /** Native bindings, resolved on first use only (never on non-Windows platforms). */
    private static final class Native {

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());
        private static final SymbolLookup DWMAPI = SymbolLookup.libraryLookup("dwmapi", Arena.global());

        static final MethodHandle FIND_WINDOW_EX = LINKER.downcallHandle(
                USER32.findOrThrow("FindWindowExW"),
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        static final MethodHandle GET_WINDOW_THREAD_PROCESS_ID = LINKER.downcallHandle(
                USER32.findOrThrow("GetWindowThreadProcessId"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        static final MethodHandle SET_WINDOW_POS = LINKER.downcallHandle(
                USER32.findOrThrow("SetWindowPos"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        static final MethodHandle DWM_SET_WINDOW_ATTRIBUTE = LINKER.downcallHandle(
                DWMAPI.findOrThrow("DwmSetWindowAttribute"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private Native() {
        }
    }
}
