package com.edutrack.desktop.ui.dialog;

import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.value.ObservableBooleanValue;

/**
 * Contract between {@link FormDialogs} and a dialog's FXML controller.
 *
 * @param <R> what a successful save returns (usually the refreshed DTO)
 */
public interface DialogForm<R> {

    /** Window title and dialog header, e.g. "New goal" or "Edit step". */
    String title();

    /** Text of the primary button, e.g. "Create goal" or "Save changes". */
    String saveLabel();

    /** Save is enabled only while this is true (required fields filled, numbers parse). */
    ObservableBooleanValue validProperty();

    /**
     * Runs the use case. Throwing {@link DomainValidationException} keeps the dialog open and
     * shows the message through {@link #showError(String)}.
     */
    R submit();

    void showError(String message);
}
