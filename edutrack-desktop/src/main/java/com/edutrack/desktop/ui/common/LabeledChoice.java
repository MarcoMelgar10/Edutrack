package com.edutrack.desktop.ui.common;

import javafx.scene.control.ComboBox;

import java.util.List;
import java.util.function.Function;

/**
 * One entry of a ComboBox: a value and the text shown for it. The value may be {@code null} for the
 * "All areas" / "None" entry, which a plain ComboBox of domain objects cannot show as text.
 */
public record LabeledChoice<T>(T value, String label) {

    /** The choice for {@code value}, or the first one (the empty choice) when none matches. */
    public static <T> LabeledChoice<T> of(List<LabeledChoice<T>> choices, T value) {
        return choices.stream()
                .filter(choice -> java.util.Objects.equals(choice.value(), value))
                .findFirst()
                .orElse(choices.getFirst());
    }

    /** {@code empty} first, then one choice per item: its value from {@code value}, its text from {@code label}. */
    public static <S, T> List<LabeledChoice<T>> listOf(String empty, List<S> items, Function<S, T> value,
                                                       Function<S, String> label) {
        java.util.ArrayList<LabeledChoice<T>> choices = new java.util.ArrayList<>();
        choices.add(new LabeledChoice<>(null, empty));
        items.forEach(item -> choices.add(new LabeledChoice<>(value.apply(item), label.apply(item))));
        return choices;
    }

    /** Like {@link #listOf(String, List, Function, Function)} for items that are their own value. */
    public static <T> List<LabeledChoice<T>> listOf(String empty, List<T> items, Function<T, String> label) {
        return listOf(empty, items, Function.identity(), label);
    }

    /**
     * Keeps {@code box} and {@code value} in step: choosing in the box sets the property, and a change of the
     * property (or of {@code source}, which rebuilds the choices) is shown in the box.
     */
    public static <T> void bind(ComboBox<LabeledChoice<T>> box, javafx.beans.property.ObjectProperty<T> value,
                                javafx.beans.Observable source, java.util.function.Supplier<List<LabeledChoice<T>>> choices) {
        boolean[] syncing = {false};
        Runnable rebuild = () -> {
            syncing[0] = true;
            try {
                fill(box, choices.get(), value.get());
            } finally {
                syncing[0] = false;
            }
        };
        source.addListener(observable -> rebuild.run());
        value.subscribe(() -> {
            if (!syncing[0] && !java.util.Objects.equals(selected(box), value.get())) {
                syncing[0] = true;
                try {
                    box.getSelectionModel().select(of(box.getItems(), value.get()));
                } finally {
                    syncing[0] = false;
                }
            }
        });
        box.getSelectionModel().selectedItemProperty().subscribe(choice -> {
            if (!syncing[0]) {
                value.set(choice == null ? null : choice.value());
            }
        });
        rebuild.run();
    }

    /** Replaces the box's items and keeps (or restores) the selection of {@code value}. */
    public static <T> void fill(ComboBox<LabeledChoice<T>> box, List<LabeledChoice<T>> choices, T value) {
        box.getItems().setAll(choices);
        box.getSelectionModel().select(of(choices, value));
    }

    /** The selected value, {@code null} when nothing or the empty choice is selected. */
    public static <T> T selected(ComboBox<LabeledChoice<T>> box) {
        LabeledChoice<T> choice = box.getSelectionModel().getSelectedItem();
        return choice == null ? null : choice.value();
    }

    @Override
    public String toString() {
        return label;
    }
}
