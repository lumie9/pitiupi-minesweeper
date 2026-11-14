package pitiupi.minesweeper.net;

import pitiupi.models.Messages.Message;

/**
 * Base de todas as mensagens do Campo Minado 1v1.
 *
 * <p>Cada mensagem diz de qual sala fala e quem a enviou. O IP vai dentro da mensagem porque
 * o núcleo entrega ao plugin só o objeto, sem o endereço do datagrama. A resposta unicast
 * e as conexões TCP usam este IP.</p>
 */
public abstract class MineMessage extends Message {

    private static final long serialVersionUID = 1L;

    private String roomId;
    private String senderId;
    private String senderName;
    private String senderIp;

    protected MineMessage() {
    }

    protected MineMessage(String roomId, String senderId, String senderName, String senderIp) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderIp = senderIp;
    }

    public String getRoomId() { return roomId; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderIp() { return senderIp; }
}
