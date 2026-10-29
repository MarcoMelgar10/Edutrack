package com.edutrack.desktop.ui.common;

import javafx.css.PseudoClass;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

/**
 * Reordering of list rows by hand: drag and drop inside one list, and Alt+Up / Alt+Down for the
 * selected row. The list itself never changes — the owner is told "move this row to that index" and
 * reloads the list, so the order always comes from the data.
 *
 * <p>Cells show where a drop would land through the {@code drop-target} pseudo-class.
 */
public final class Reorderable {

    /** What the owner of the list does when a row is moved. */
    public interface Mover<T> {
        /** Moves {@code item} one place up ({@code -1}) or down ({@code +1}). */
        void step(T item, int direction);

        /** Moves {@code item} so that it ends up at {@code index}. */
        void moveTo(T item, int index);
    }

    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    // A drag never leaves the application, so the dragged row is simply remembered here.
    private static Object dragged;
    private static ListView<?> draggedFrom;

    private Reorderable() {
    }

    /** Makes {@code cell} draggable and a drop target; call once from the cell's constructor. */
    public static <T> void install(ListCell<T> cell, Mover<T> mover) {
        cell.addEventHandler(MouseEvent.DRAG_DETECTED, event -> {
            if (cell.isEmpty() || cell.getItem() == null) {
                return;
            }
            Dragboard board = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(cell.getItem()));
            board.setContent(content);
            dragged = cell.getItem();
            draggedFrom = cell.getListView();
            event.consume();
        });
        cell.addEventHandler(DragEvent.DRAG_OVER, event -> {
            if (isDragFromSameList(cell)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        cell.addEventHandler(DragEvent.DRAG_ENTERED, event -> {
            if (isDragFromSameList(cell) && !dragged.equals(cell.getItem())) {
                cell.pseudoClassStateChanged(DROP_TARGET, true);
            }
        });
        cell.addEventHandler(DragEvent.DRAG_EXITED, event -> cell.pseudoClassStateChanged(DROP_TARGET, false));
        cell.addEventHandler(DragEvent.DRAG_DROPPED, event -> {
            cell.pseudoClassStateChanged(DROP_TARGET, false);
            if (!isDragFromSameList(cell)) {
                return;
            }
            @SuppressWarnings("unchecked")
            T item = (T) dragged;
            int rows = cell.getListView().getItems().size();
            int target = cell.isEmpty() ? rows - 1 : Math.min(cell.getIndex(), rows - 1);
            event.setDropCompleted(true);
            event.consume();
            if (!item.equals(cell.getItem())) {
                mover.moveTo(item, target);
            }
        });
        cell.addEventHandler(DragEvent.DRAG_DONE, event -> {
            dragged = null;
            draggedFrom = null;
        });
    }

    /** Alt+Up / Alt+Down moves the selected row of {@code list}. */
    public static <T> void installKeys(ListView<T> list, Mover<T> mover) {
        list.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isAltDown() || (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN)) {
                return;
            }
            T selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                mover.step(selected, event.getCode() == KeyCode.UP ? -1 : 1);
            }
            event.consume();
        });
    }

    private static boolean isDragFromSameList(ListCell<?> cell) {
        return dragged != null && draggedFrom == cell.getListView();
    }
}
