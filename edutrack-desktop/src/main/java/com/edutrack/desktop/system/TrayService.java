package com.edutrack.desktop.system;

import com.edutrack.desktop.ui.common.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** The Windows notification-area icon: Open / Quit, double-click to open, and toasts. Does nothing without a tray. */
@Component
public class TrayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrayService.class);

    private final Messages messages;
    private TrayIcon icon;

    public TrayService(Messages messages) {
        this.messages = messages;
    }

    public static boolean supported() {
        return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
    }

    /** Adds the icon; the callbacks run on the AWT thread. Returns false when there is no tray. */
    public synchronized boolean install(Runnable open, Runnable quit) {
        if (icon != null) {
            return true;
        }
        if (!supported()) {
            return false;
        }
        try (InputStream stream = TrayService.class.getResourceAsStream("/icons/app-icon-32.png")) {
            if (stream == null) {
                return false;
            }
            BufferedImage image = ImageIO.read(stream);
            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem(messages.get("tray.open"));
            openItem.addActionListener(event -> open.run());
            MenuItem quitItem = new MenuItem(messages.get("tray.quit"));
            quitItem.addActionListener(event -> quit.run());
            menu.add(openItem);
            menu.addSeparator();
            menu.add(quitItem);
            TrayIcon created = new TrayIcon(image, "EduTrack", menu);
            created.setImageAutoSize(true);
            created.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() >= 2) {
                        open.run();
                    }
                }
            });
            SystemTray.getSystemTray().add(created);
            icon = created;
            return true;
        } catch (IOException | AWTException | RuntimeException failure) {
            LOGGER.warn("Could not add the tray icon", failure);
            return false;
        }
    }

    public synchronized boolean isInstalled() {
        return icon != null;
    }

    /** Shows a toast; false when there is no tray icon to show it from. */
    public synchronized boolean toast(String title, String message) {
        if (icon == null) {
            return false;
        }
        icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        return true;
    }

    public synchronized void remove() {
        if (icon != null) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }
}
