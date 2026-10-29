package com.edutrack.desktop.ui.notes;

import com.edutrack.desktop.ui.notes.MarkdownEdits.Block;
import com.edutrack.desktop.ui.notes.MarkdownEdits.Edit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownEditsTest {

    /** The text after the edit with the selection shown between ⟦ and ⟧ (a caret as ⟦⟧). */
    private static String result(String text, Edit edit) {
        String after = edit.applyTo(text);
        return after.substring(0, edit.selStart()) + "⟦" + after.substring(edit.selStart(), edit.selEnd()) + "⟧"
                + after.substring(edit.selEnd());
    }

    private static int at(String text, String part) {
        int index = text.indexOf(part);
        assertThat(index).as("'%s' in '%s'", part, text).isNotNegative();
        return index;
    }

    private static String bold(String text, String selected) {
        int start = at(text, selected);
        return result(text, MarkdownEdits.toggleInline(text, start, start + selected.length(), "**"));
    }

    // ---------- inline ----------

    @Test
    void wrapsTheSelectionAndKeepsItSelected() {
        assertThat(bold("one two three", "two")).isEqualTo("one **⟦two⟧** three");
    }

    @Test
    void takesTheMarkersOffWhenTheyAreAlreadyThere() {
        String text = "one **two** three";
        int start = text.indexOf("two");

        assertThat(result(text, MarkdownEdits.toggleInline(text, start, start + 3, "**"))).isEqualTo("one ⟦two⟧ three");
    }

    @Test
    void togglingTwiceGivesBackTheOriginalText() {
        String text = "one two three";
        int start = text.indexOf("two");
        Edit wrapped = MarkdownEdits.toggleInline(text, start, start + 3, "++");
        String after = wrapped.applyTo(text);

        Edit unwrapped = MarkdownEdits.toggleInline(after, wrapped.selStart(), wrapped.selEnd(), "++");

        assertThat(unwrapped.applyTo(after)).isEqualTo(text);
    }

    @Test
    void withNoSelectionTheWordUnderTheCaretIsWrapped() {
        String text = "one two three";

        assertThat(result(text, MarkdownEdits.toggleInline(text, 5, 5, "*"))).isEqualTo("one *⟦two⟧* three");
    }

    @Test
    void withNoWordAnEmptyPairOfMarkersIsInsertedWithTheCaretBetween() {
        String text = "one  three";

        assertThat(result(text, MarkdownEdits.toggleInline(text, 4, 4, "**"))).isEqualTo("one **⟦⟧** three");
    }

    @Test
    void spacesAtTheEndsOfTheSelectionStayOutsideTheMarkers() {
        String text = "one two three";

        assertThat(result(text, MarkdownEdits.toggleInline(text, 3, 8, "**"))).isEqualTo("one **⟦two⟧** three");
    }

    @Test
    void italicAndBoldAreToldApartWhenTheyShareTheStar() {
        String boldText = "a **x** b";
        int x = boldText.indexOf('x');

        // italic on bold text adds a star on each side instead of removing one
        assertThat(result(boldText, MarkdownEdits.toggleInline(boldText, x, x + 1, "*"))).isEqualTo("a ***⟦x⟧*** b");
        assertThat(MarkdownEdits.isWrapped(boldText, x, x + 1, "*")).isFalse();
        assertThat(MarkdownEdits.isWrapped(boldText, x, x + 1, "**")).isTrue();

        String both = "a ***x*** b";
        int y = both.indexOf('x');
        assertThat(MarkdownEdits.isWrapped(both, y, y + 1, "*")).isTrue();
        assertThat(result(both, MarkdownEdits.toggleInline(both, y, y + 1, "*"))).isEqualTo("a **⟦x⟧** b");
    }

    @Test
    void codeUnderlineStrikeAndHighlightUseTheirOwnMarkers() {
        String text = "say hello";
        int hello = text.indexOf("hello");

        assertThat(result(text, MarkdownEdits.toggleInline(text, hello, text.length(), "`"))).isEqualTo("say `⟦hello⟧`");
        assertThat(result(text, MarkdownEdits.toggleInline(text, hello, text.length(), "~~"))).isEqualTo("say ~~⟦hello⟧~~");
        assertThat(result(text, MarkdownEdits.toggleInline(text, hello, text.length(), "=="))).isEqualTo("say ==⟦hello⟧==");
    }

    @Test
    void highlightColorsWrapRecolorAndRemoveInPlace() {
        String text = "say hello now";
        int hello = text.indexOf("hello");

        assertThat(result(text, MarkdownEdits.applyHighlight(text, hello, hello + 5, "yellow"))).isEqualTo("say ==⟦hello⟧== now");
        assertThat(result(text, MarkdownEdits.applyHighlight(text, hello, hello + 5, "green")))
                .isEqualTo("say =={green}⟦hello⟧== now");
        assertThat(result(text, MarkdownEdits.applyHighlight(text, hello + 2, hello + 2, "blue")))
                .isEqualTo("say =={blue}⟦hello⟧== now");
        assertThat(MarkdownEdits.applyHighlight(text, hello, hello + 5, null)).isNull();

        String green = "say =={green}hello== now";
        int inner = green.indexOf("hello");
        assertThat(MarkdownEdits.highlightColor(green, inner, inner + 5)).isEqualTo("green");
        assertThat(result(green, MarkdownEdits.applyHighlight(green, inner, inner + 5, "pink")))
                .isEqualTo("say =={pink}⟦hello⟧== now");
        assertThat(result(green, MarkdownEdits.applyHighlight(green, inner, inner + 5, "yellow")))
                .isEqualTo("say ==⟦hello⟧== now");
        assertThat(result(green, MarkdownEdits.applyHighlight(green, inner, inner + 5, null))).isEqualTo("say ⟦hello⟧ now");
        // the whole span, markers included, and a caret in the middle of a longer highlight
        assertThat(result(green, MarkdownEdits.applyHighlight(green, 4, green.indexOf(" now"), null)))
                .isEqualTo("say ⟦hello⟧ now");
        String longer = "==two words==";
        assertThat(MarkdownEdits.highlightColor(longer, 8, 8)).isEqualTo("yellow");
        assertThat(result(longer, MarkdownEdits.applyHighlight(longer, 8, 8, "orange"))).isEqualTo("=={orange}two wo⟦⟧rds==");
    }

    @Test
    void toggleHighlightTakesAnyColorOffAndOtherwiseAppliesTheOne() {
        String text = "a =={green}x== b y";
        int x = text.indexOf('x');
        int y = text.indexOf('y');

        assertThat(result(text, MarkdownEdits.toggleHighlight(text, x, x + 1, "yellow"))).isEqualTo("a ⟦x⟧ b y");
        assertThat(result(text, MarkdownEdits.toggleHighlight(text, y, y + 1, "blue"))).isEqualTo("a =={green}x== b =={blue}⟦y⟧==");
        assertThat(MarkdownEdits.highlightColor(text, y, y + 1)).isNull();
        assertThat(result("", MarkdownEdits.applyHighlight("", 0, 0, "pink"))).isEqualTo("=={pink}⟦⟧==");
    }

    @Test
    void aLinkWrapsTheSelectionOrInvitesText() {
        String text = "see the docs now";
        int docs = text.indexOf("docs");

        assertThat(result(text, MarkdownEdits.makeLink(text, docs, docs + 4, "https://example.com")))
                .isEqualTo("see the [⟦docs⟧](https://example.com) now");
        assertThat(result("", MarkdownEdits.makeLink("", 0, 0, "https://x.org"))).isEqualTo("[⟦link⟧](https://x.org)");
    }

    // ---------- blocks ----------

    @Test
    void aLineBecomesAHeadingAndAHeadingChangesLevelWithoutStacking() {
        String text = "title\nrest";

        assertThat(result(text, MarkdownEdits.setBlock(text, 2, 2, Block.H2))).startsWith("## ti⟦⟧tle");
        String heading = "## title\nrest";
        assertThat(MarkdownEdits.setBlock(heading, 4, 4, Block.H1).applyTo(heading)).isEqualTo("# title\nrest");
    }

    @Test
    void applyingTheSameBlockAgainTurnsItBackIntoText() {
        String text = "# title";

        assertThat(MarkdownEdits.setBlock(text, 3, 3, Block.H1).applyTo(text)).isEqualTo("title");
    }

    @Test
    void severalLinesBecomeANumberedListFromOne() {
        String text = "first\nsecond\n\nthird";

        Edit edit = MarkdownEdits.setBlock(text, 0, text.length(), Block.NUMBERED);

        assertThat(edit.applyTo(text)).isEqualTo("1. first\n2. second\n\n3. third");
    }

    @Test
    void bulletsTasksAndQuotesReplaceEachOther() {
        String bullets = "- one\n- two";

        assertThat(MarkdownEdits.setBlock(bullets, 0, bullets.length(), Block.TASK).applyTo(bullets))
                .isEqualTo("- [ ] one\n- [ ] two");
        String tasks = "- [x] one";
        assertThat(MarkdownEdits.setBlock(tasks, 3, 3, Block.QUOTE).applyTo(tasks)).isEqualTo("> one");
        assertThat(MarkdownEdits.setBlock("> one", 3, 3, Block.BULLET).applyTo("> one")).isEqualTo("- one");
    }

    @Test
    void anEmptyLineCanBecomeABlockToTypeIn() {
        assertThat(result("", MarkdownEdits.setBlock("", 0, 0, Block.BULLET))).isEqualTo("- ⟦⟧");
        assertThat(MarkdownEdits.setBlock("a\n\nb", 2, 2, Block.H1).applyTo("a\n\nb")).isEqualTo("a\n# \nb");
    }

    @Test
    void aSelectionEndingAtTheStartOfALineLeavesThatLineAlone() {
        String text = "one\ntwo\nthree";

        Edit edit = MarkdownEdits.setBlock(text, 0, 8, Block.BULLET); // "one\ntwo\n"

        assertThat(edit.applyTo(text)).isEqualTo("- one\n- two\nthree");
    }

    @Test
    void codeWrapsTheLinesInFences() {
        String text = "x = 1";

        assertThat(MarkdownEdits.setBlock(text, 0, 0, Block.CODE).applyTo(text)).isEqualTo("```\nx = 1\n```");
    }

    @Test
    void blockKindsAreReadFromTheMarker() {
        assertThat(MarkdownEdits.kindOf("# x")).isEqualTo(Block.H1);
        assertThat(MarkdownEdits.kindOf("### x")).isEqualTo(Block.H3);
        assertThat(MarkdownEdits.kindOf("- x")).isEqualTo(Block.BULLET);
        assertThat(MarkdownEdits.kindOf("  2. x")).isEqualTo(Block.NUMBERED);
        assertThat(MarkdownEdits.kindOf("- [ ] x")).isEqualTo(Block.TASK);
        assertThat(MarkdownEdits.kindOf("> x")).isEqualTo(Block.QUOTE);
        assertThat(MarkdownEdits.kindOf("x")).isEqualTo(Block.TEXT);
    }

    // ---------- inserted blocks ----------

    @Test
    void aRuleGoesAfterTheLineWithABlankLineBetween() {
        String text = "above";

        String after = MarkdownEdits.insertRule(text, 5).applyTo(text);

        assertThat(after).isEqualTo("above\n\n---\n");
    }

    @Test
    void aRuleReplacesAnEmptyLine() {
        String text = "above\n\nbelow";

        assertThat(MarkdownEdits.insertRule(text, 6).applyTo(text)).isEqualTo("above\n\n---\n\nbelow");
    }

    @Test
    void aTableHasAHeaderARuleAndRowsAndTheCaretStartsInTheFirstCell() {
        Edit edit = MarkdownEdits.insertTable("", 0, 2, 3);
        String after = edit.applyTo("");

        assertThat(after.lines().toList()).hasSize(4);
        assertThat(after).startsWith("| Column | Column | Column |\n| ------ | ------ | ------ |\n");
        assertThat(edit.selStart()).isEqualTo(2);
        assertThat(MarkdownStyler.style(after).blocks().stream().map(MarkdownStyler.BlockInfo::kind))
                .contains(MarkdownStyler.Kind.TABLE_HEAD, MarkdownStyler.Kind.TABLE_ROW);
    }

    @Test
    void aCodeBlockPutsTheCaretOnTheEmptyLineInside() {
        Edit edit = MarkdownEdits.insertCodeBlock("", 0);

        assertThat(result("", edit)).isEqualTo("```\n⟦⟧\n```\n");
    }

    // ---------- Enter ----------

    @Test
    void enterContinuesABulletList() {
        String text = "- one";

        assertThat(result(text, MarkdownEdits.onEnter(text, 5))).isEqualTo("- one\n- ⟦⟧");
    }

    @Test
    void enterContinuesANumberedListWithTheNextNumber() {
        String text = "1. one\n2. two";

        assertThat(result(text, MarkdownEdits.onEnter(text, text.length()))).isEqualTo("1. one\n2. two\n3. ⟦⟧");
    }

    @Test
    void enterOnATaskStartsAnUncheckedTask() {
        String text = "- [x] done";

        assertThat(result(text, MarkdownEdits.onEnter(text, text.length()))).isEqualTo("- [x] done\n- [ ] ⟦⟧");
    }

    @Test
    void enterInTheMiddleOfAnItemMovesTheRestToTheNextItem() {
        String text = "- onetwo";

        assertThat(result(text, MarkdownEdits.onEnter(text, 5))).isEqualTo("- one\n- ⟦⟧two");
    }

    @Test
    void enterOnAnEmptyItemEndsTheList() {
        String text = "- one\n- ";

        assertThat(MarkdownEdits.onEnter(text, text.length()).applyTo(text)).isEqualTo("- one\n");
    }

    @Test
    void enterOnAnEmptyNestedItemTakesItBackOneLevel() {
        String text = "- one\n    - ";

        assertThat(MarkdownEdits.onEnter(text, text.length()).applyTo(text)).isEqualTo("- one\n- ");
    }

    @Test
    void enterContinuesAQuoteAndEndsItWhenEmpty() {
        String text = "> said";

        assertThat(result(text, MarkdownEdits.onEnter(text, 6))).isEqualTo("> said\n> ⟦⟧");
        String empty = "> said\n> ";
        assertThat(MarkdownEdits.onEnter(empty, empty.length()).applyTo(empty)).isEqualTo("> said\n");
    }

    @Test
    void enterIsLeftAloneInPlainTextHeadingsAndCode() {
        assertThat(MarkdownEdits.onEnter("plain", 5)).isNull();
        assertThat(MarkdownEdits.onEnter("# title", 7)).isNull();
        String code = "```\n- not a list";
        assertThat(MarkdownEdits.onEnter(code, code.length())).isNull();
        assertThat(MarkdownEdits.onEnter("- item", 1)).isNull(); // caret inside the marker
    }

    // ---------- Tab and Backspace ----------

    @Test
    void tabNestsAnItemUnderTheOneAboveAndShiftTabTakesItBack() {
        String text = "- one\n- two";

        Edit nest = MarkdownEdits.onTab(text, 8, 8, false);
        String nested = nest.applyTo(text);
        assertThat(nested).isEqualTo("- one\n    - two");

        assertThat(MarkdownEdits.onTab(nested, nest.selStart(), nest.selEnd(), true).applyTo(nested)).isEqualTo(text);
    }

    @Test
    void tabCannotNestTheFirstItemOrGoDeeperThanOneLevelPerItem() {
        assertThat(MarkdownEdits.onTab("- one", 2, 2, false)).isNull();
        String text = "- one\n    - two\n- three";
        // "- three" can nest under "two" (one level deeper than "one")
        assertThat(MarkdownEdits.onTab(text, text.length(), text.length(), false)).isNotNull();
        // "two" is already one level under "one": it cannot go further without a sibling above it
        String deep = "- one\n    - two";
        assertThat(MarkdownEdits.onTab(deep, deep.length(), deep.length(), false)).isNull();
    }

    @Test
    void tabOutsideListsIsLeftAlone() {
        assertThat(MarkdownEdits.onTab("plain text", 3, 3, false)).isNull();
    }

    @Test
    void backspaceRightAfterAMarkerRemovesIt() {
        assertThat(MarkdownEdits.onBackspace("- item", 2).applyTo("- item")).isEqualTo("item");
        assertThat(MarkdownEdits.onBackspace("# title", 2).applyTo("# title")).isEqualTo("title");
        assertThat(MarkdownEdits.onBackspace("- [ ] task", 6).applyTo("- [ ] task")).isEqualTo("task");
        assertThat(MarkdownEdits.onBackspace("item", 2)).isNull();
        assertThat(MarkdownEdits.onBackspace("- item", 4)).isNull();
    }

    @Test
    void aTaskIsCheckedAndUncheckedByItsLine() {
        String text = "intro\n- [ ] todo\n- [x] done";

        assertThat(MarkdownEdits.toggleTask(text, 1).applyTo(text)).isEqualTo("intro\n- [x] todo\n- [x] done");
        assertThat(MarkdownEdits.toggleTask(text, 2).applyTo(text)).isEqualTo("intro\n- [ ] todo\n- [ ] done");
        assertThat(MarkdownEdits.toggleTask(text, 0)).isNull();
        assertThat(MarkdownEdits.toggleTask(text, 9)).isNull();
    }

    @Test
    void textInsertedReplacesTheSelection() {
        assertThat(result("a b c", MarkdownEdits.insertText("a b c", 2, 3, "28 Oct 2026"))).isEqualTo("a 28 Oct 2026⟦⟧ c");
    }
}
