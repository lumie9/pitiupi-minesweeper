package pitiupi.minesweeper.net;

/** Fase da sala, como aparece no anúncio do lobby. */
public enum RoomPhase {
    WAITING("Aguardando"),
    COUNTDOWN("Começando"),
    PLAYING("Em jogo"),
    FINISHED("Terminou");

    private final String label;

    RoomPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
