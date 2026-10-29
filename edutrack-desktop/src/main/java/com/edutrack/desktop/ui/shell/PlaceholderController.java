package com.edutrack.desktop.ui.shell;

import com.edutrack.desktop.ui.common.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Stands in for a rail page that a later version builds: its title and a quiet "coming" line. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PlaceholderController {

    private final Messages messages;

    @FXML
    private Label titleLabel;

    public PlaceholderController(Messages messages) {
        this.messages = messages;
    }

    /** Names the page this placeholder stands in for. */
    public void show(ShellPage page) {
        titleLabel.setText(messages.get(page.titleKey()));
    }
}
