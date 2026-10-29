package com.edutrack.desktop.ui.common;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * Loads FXML views whose controllers are Spring beans.
 *
 * <p>Convention: {@code FooController} lives next to {@code FooView.fxml} in the same package.
 * Controllers (and the ViewModels they receive) are prototype beans, so every load gets fresh
 * instances — one per area page, one per dialog. Every view gets the current language bundle, so a
 * language switch only has to load the views again.
 */
@Component
public class ViewLoader {

    private final ApplicationContext context;
    private final Messages messages;

    public ViewLoader(ApplicationContext context, Messages messages) {
        this.context = context;
        this.messages = messages;
    }

    public <C> LoadedView<C> load(Class<C> controllerType) {
        URL fxml = fxmlFor(controllerType);
        FXMLLoader loader = new FXMLLoader(fxml);
        loader.setControllerFactory(context::getBean);
        loader.setResources(messages.bundle()); // %key in the FXML reads the current language
        try {
            Parent root = loader.load();
            return new LoadedView<>(root, controllerType.cast(loader.getController()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load view " + fxml, e);
        }
    }

    private static URL fxmlFor(Class<?> controllerType) {
        String name = controllerType.getSimpleName().replaceFirst("Controller$", "View") + ".fxml";
        URL url = controllerType.getResource(name);
        if (url == null) {
            throw new IllegalStateException("No " + name + " next to " + controllerType.getName());
        }
        return url;
    }

    public record LoadedView<C>(Parent root, C controller) {
    }
}
