package com.edutrack.desktop.ui.notes;

import com.edutrack.desktop.ui.notes.MarkdownStyler.BlockInfo;
import com.edutrack.desktop.ui.notes.MarkdownStyler.Kind;
import com.edutrack.desktop.ui.notes.MarkdownStyler.Segment;
import com.edutrack.desktop.ui.notes.MarkdownStyler.Styling;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownStylerTest {

    private static String markedText(Styling styling) {
        StringBuilder marked = new StringBuilder();
        for (MarkdownStyler.Mark mark : styling.marks()) {
            marked.append('[').append(styling.text(), mark.start(), mark.end()).append(']');
        }
        return marked.toString();
    }

    private static String styledText(Styling styling, String styleClass) {
        StringBuilder text = new StringBuilder();
        for (MarkdownStyler.Styled range : styling.styled()) {
            if (range.styleClass().equals(styleClass)) {
                text.append('[').append(styling.text(), range.start(), range.end()).append(']');
            }
        }
        return text.toString();
    }

    @Test
    void headingsKeepTheirLevelAndTheirMarkerIsSyntax() {
        Styling styling = MarkdownStyler.style("# One\n## Two\n###### Six\nplain");

        assertThat(styling.blocks()).extracting(BlockInfo::kind).containsExactly(Kind.H1, Kind.H2, Kind.H3, Kind.TEXT);
        assertThat(markedText(styling)).isEqualTo("[# ][## ][###### ]");
        assertThat(styledText(styling, "md-h1-text")).isEqualTo("[# One]");
    }

    @Test
    void inlineStylesLeaveTheirMarkersAsSyntax() {
        Styling styling = MarkdownStyler.style("a **bold** *it* ++under++ ==hi== ~~old~~ `code` [link](http://x.org)");

        assertThat(styledText(styling, "md-strong")).isEqualTo("[bold]");
        assertThat(styledText(styling, "md-em")).isEqualTo("[it]");
        assertThat(styledText(styling, "md-underline")).isEqualTo("[under]");
        assertThat(styledText(styling, "md-highlight")).isEqualTo("[hi]");
        assertThat(styledText(styling, "md-strike")).isEqualTo("[old]");
        assertThat(styledText(styling, "md-code-inline")).isEqualTo("[code]");
        assertThat(styledText(styling, "md-link")).isEqualTo("[link]");
        assertThat(markedText(styling)).isEqualTo("[**][**][*][*][++][++][==][==][~~][~~][`][`][[][](http://x.org)]");
    }

    @Test
    void coloredHighlightsHideTheirColorMarkerAndKeepOldHighlightsYellow() {
        Styling styling = MarkdownStyler.style("a =={green}go== b ==old== c =={purple}**both**==");

        assertThat(styledText(styling, "md-highlight")).isEqualTo("[go][old][**both**]");
        assertThat(styledText(styling, "md-highlight-green")).isEqualTo("[go]");
        assertThat(styledText(styling, "md-highlight-yellow")).isEqualTo("[old]");
        assertThat(styledText(styling, "md-highlight-purple")).isEqualTo("[**both**]");
        assertThat(markedText(styling)).startsWith("[=={green}][==][==][==]");
    }

    @Test
    void nestedInlineStylesCombine() {
        Styling styling = MarkdownStyler.style("**bold and *both* here**");
        int both = styling.text().indexOf("both");

        assertThat(styling.classesAt(both)).contains("md-strong", "md-em");
        assertThat(styling.classesAt(styling.text().indexOf("bold"))).containsExactly("md-strong");
    }

    @Test
    void bulletNumberedAndTaskItemsKnowTheirKindDepthAndNumber() {
        Styling styling = MarkdownStyler.style("- one\n    - nested\n- [ ] todo\n- [x] done\n\n3. three\n4. four");

        assertThat(styling.blocks()).extracting(BlockInfo::kind).containsExactly(Kind.BULLET, Kind.BULLET, Kind.TASK,
                Kind.TASK, Kind.TEXT, Kind.NUMBERED, Kind.NUMBERED);
        assertThat(styling.block(0).level()).isEqualTo(1);
        assertThat(styling.block(1).level()).isEqualTo(2);
        assertThat(styling.block(2).checked()).isFalse();
        assertThat(styling.block(3).checked()).isTrue();
        assertThat(styling.block(5).ordinal()).isEqualTo(3);
        assertThat(styling.block(6).ordinal()).isEqualTo(4);
        assertThat(markedText(styling)).isEqualTo("[- ][    - ][- [ ] ][- [x] ][3. ][4. ]");
        assertThat(styledText(styling, "md-task-done-text")).isEqualTo("[done]");
    }

    @Test
    void quotesFlagTheirLinesAndMarkTheirMarker() {
        Styling styling = MarkdownStyler.style("> quoted *text*\n> more\n\nafter");

        assertThat(styling.block(0).quote()).isTrue();
        assertThat(styling.block(1).quote()).isTrue();
        assertThat(styling.block(3).quote()).isFalse();
        assertThat(markedText(styling)).isEqualTo("[> ][> ][*][*]");
    }

    @Test
    void fencedCodeKeepsItsContentPlainAndHidesTheFences() {
        Styling styling = MarkdownStyler.style("```java\nint **a** = 1;\n```\nafter **b**");

        assertThat(styling.blocks()).extracting(BlockInfo::kind)
                .containsExactly(Kind.CODE_FENCE, Kind.CODE, Kind.CODE_FENCE, Kind.TEXT);
        assertThat(styledText(styling, "md-code-block")).isEqualTo("[int **a** = 1;]");
        assertThat(styledText(styling, "md-strong")).isEqualTo("[b]"); // nothing is parsed inside the code
    }

    @Test
    void anUnclosedFenceRunsToTheEnd() {
        Styling styling = MarkdownStyler.style("```\ncode\nmore");

        assertThat(styling.blocks()).extracting(BlockInfo::kind).containsExactly(Kind.CODE_FENCE, Kind.CODE, Kind.CODE);
    }

    @Test
    void tablesGetHeaderRuleAndRowKinds() {
        Styling styling = MarkdownStyler.style("| A | B |\n|---|---|\n| 1 | **2** |");

        assertThat(styling.blocks()).extracting(BlockInfo::kind)
                .containsExactly(Kind.TABLE_HEAD, Kind.TABLE_RULE, Kind.TABLE_ROW);
        assertThat(styledText(styling, "md-strong")).isEqualTo("[2]");
    }

    @Test
    void aThematicBreakIsAWholeLineOfSyntax() {
        Styling styling = MarkdownStyler.style("above\n\n---\n\nbelow");

        assertThat(styling.block(2).kind()).isEqualTo(Kind.RULE);
        assertThat(markedText(styling)).isEqualTo("[---]");
    }

    @Test
    void markersAreHiddenExceptOnTheLinesBeingEdited() {
        Styling styling = MarkdownStyler.style("# Title\nsome **bold** text");

        List<Segment> hidden = styling.segments(0, styling.text().length(), Set.of());
        List<Segment> editing = styling.segments(0, styling.text().length(), Set.of(0));

        assertThat(hidden.stream().mapToInt(Segment::length).sum()).isEqualTo(styling.text().length());
        assertThat(hidden.getFirst().classes()).contains("md-mark", "md-mark-hidden");
        assertThat(editing.getFirst().classes()).contains("md-mark", "md-mark-shown").doesNotContain("md-mark-hidden");
        // the markers of line 1 stay hidden while only line 0 is active
        assertThat(editing.stream().filter(segment -> segment.classes().contains("md-mark-hidden")).count()).isEqualTo(2);
    }

    @Test
    void segmentsCoverTheRequestedRangeExactly() {
        Styling styling = MarkdownStyler.style("a **b** c");

        List<Segment> middle = styling.segments(2, 7, Set.of());

        assertThat(middle.stream().mapToInt(Segment::length).sum()).isEqualTo(5);
        assertThat(middle).extracting(Segment::classes).containsExactly(List.of("md-mark", "md-mark-hidden"),
                List.of("md-strong"), List.of("md-mark", "md-mark-hidden"));
        assertThat(styling.segments(5, 5, Set.of())).isEmpty();
    }

    @Test
    void linesAndOffsetsAgree() {
        Styling styling = MarkdownStyler.style("ab\ncd\n\nef");

        assertThat(styling.lineCount()).isEqualTo(4);
        assertThat(styling.lineOf(0)).isEqualTo(0);
        assertThat(styling.lineOf(3)).isEqualTo(1);
        assertThat(styling.lineOf(6)).isEqualTo(2);
        assertThat(styling.lineOf(styling.text().length())).isEqualTo(3);
        assertThat(styling.lineEnd(1)).isEqualTo(5);
    }

    @Test
    void textAroundEmojiAndAccentsKeepsItsOffsets() {
        Styling styling = MarkdownStyler.style("🎧 canción **énfasis** ñ");

        assertThat(styledText(styling, "md-strong")).isEqualTo("[énfasis]");
    }

    @Test
    void emptyOrBlankTextHasOneEmptyLine() {
        assertThat(MarkdownStyler.style("").lineCount()).isEqualTo(1);
        assertThat(MarkdownStyler.style(null).text()).isEmpty();
        assertThat(MarkdownStyler.style("  \n ").blocks()).allMatch(info -> info.kind() == Kind.TEXT);
    }

    @Test
    void malformedAndHugeInputNeverThrows() {
        String nasty = "**unclosed *a ~~b ==c `d [e](f\n> > > - - - 1. # ```\n| | |\n|-|\n" + "x".repeat(1000);
        MarkdownStyler.style(nasty);
        MarkdownStyler.style("*".repeat(5000) + "\n" + "- ".repeat(2000));

        String big = "# Heading\n\nSome **bold** and *italic* text with `code` and [a link](https://example.com).\n\n"
                .repeat(1500);
        long started = System.nanoTime();
        Styling styling = MarkdownStyler.style(big);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(styling.lineCount()).isGreaterThan(4000);
        assertThat(millis).as("styling %d characters", big.length()).isLessThan(2000);
    }
}
