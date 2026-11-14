package pitiupi.minesweeper.GUI;

import javax.swing.UIManager;
import java.awt.Color;

/** Cores que acompanham o tema do Swing em uso. */
final class UIColors {

    private UIColors() {
    }

    static Color text() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.BLACK;
    }
}
