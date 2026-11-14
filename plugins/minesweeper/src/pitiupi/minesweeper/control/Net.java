package pitiupi.minesweeper.control;

import pitiupi.application.messaging.TransportMode;
import pitiupi.minesweeper.net.MineMessage;

import java.util.function.Consumer;

/**
 * O que o controller precisa da rede. A implementação real usa o {@code MessageDispatcher} do
 * núcleo; os testes usam uma ligação em memória.
 *
 * <p>Os envios são assíncronos: um TCP novo pode demorar alguns segundos para conectar e não
 * pode travar a janela.</p>
 */
public interface Net {

    /** Lobby: manda para a sala toda (multicast, ou uma cópia unicast por pessoa). */
    void announce(MineMessage message);

    /** Jogo: manda por TCP para um IP. {@code onResult} (opcional) roda na thread do Swing. */
    void sendReliable(String ip, MineMessage message, Consumer<Boolean> onResult);

    /** Meio usado pelos anúncios do lobby. */
    void setAnnounceTransport(TransportMode mode);

    TransportMode getAnnounceTransport();

    String localIp();
}
