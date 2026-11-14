# Plugin Campo Minado 1v1

Duelo de campo minado na rede local. Na galeria de plugins aparece o botão **Campo Minado 1v1**. Ele abre o lobby com as salas existentes e o botão **Criar sala…**. Cada sala comporta dois jogadores. Quando o segundo entra, começa uma contagem de 5 segundos. Os dois recebem o **mesmo campo** e vence quem limpar tudo primeiro.

Contrato do núcleo: [docs/nucleo-para-plugins.md](../../docs/nucleo-para-plugins.md). Descoberta do JAR: [docs/plugins.md](../../docs/plugins.md).

## Regras

- Os dois jogam o mesmo campo, sempre de 16×16 com 40 minas (`GameRules`). Não há escolha de dificuldade.
- Uma região inicial sem minas já vem aberta para os dois, no mesmo lugar. Não há "primeiro clique seguro" porque os dois começam juntos.
- Vence quem abrir todas as casas sem mina primeiro. **Quem abre uma mina perde na hora.**
- Sair no meio da partida (ou cair da rede) dá a vitória ao outro (W.O.).
- Depois do resultado, **Jogar de novo** (anfitrião) ou **Pedir revanche** (convidado) abre nova contagem com outro campo.
- Botão esquerdo abre, direito põe bandeira. Clique num número com as bandeiras certas ao redor abre as vizinhas.

## Protocolo

Dois canais, escolhidos pelo que cada mensagem precisa:

| Canal | Para quê | Por quê |
| --- | --- | --- |
| UDP (multicast, ou uma cópia unicast por pessoa) | Lobby: anúncios das salas | Aviso periódico para todos. Perder um pacote não importa: o próximo anúncio chega em 2 s |
| TCP (`MessageDispatcher` com `TransportMode.TCP`), só entre os dois jogadores | Entrar, começar, progresso, fim, resultado, sair | "Começou" e "quem ganhou" não podem se perder nem chegar fora de ordem |

O anfitrião (quem criou a sala) é o **árbitro**: sorteia a semente, comanda a contagem e declara o vencedor. Só ele decide, então os dois jogadores nunca discordam do resultado.

Todas as mensagens estendem `MineMessage` (sala, id, nome e IP de quem enviou). O IP vai dentro da mensagem porque o núcleo entrega ao plugin só o objeto.

| Mensagem | Canal | Quem → quem | Conteúdo útil |
| --- | --- | --- | --- |
| `RoomQueryMessage` | UDP | qualquer → todos | "Quem tem sala?" Os anfitriões respondem com um anúncio na hora |
| `RoomAnnounceMessage` | UDP | anfitrião → todos, a cada 2 s | nome, fase, jogadores. Sala some da lista após 7 s sem anúncio |
| `RoomClosedMessage` | UDP | anfitrião → todos | sala fechada, some na hora |
| `JoinRequestMessage` | TCP | convidado → anfitrião | quero entrar |
| `JoinResponseMessage` | TCP | anfitrião → convidado | `ACCEPTED` ou `FULL` |
| `StartMessage` | TCP | anfitrião → convidado | rodada, **semente**, segundos de contagem |
| `StatusMessage` | TCP | nos dois sentidos, 1/s e após jogadas | casas seguras abertas (barra de progresso) e sinal de vida |
| `FinishMessage` | TCP | convidado → anfitrião | `CLEARED` ou `EXPLODED` e o tempo |
| `ResultMessage` | TCP | anfitrião → convidado | vencedor, motivo (`CLEARED`, `OPPONENT_EXPLODED`, `OPPONENT_LEFT`) |
| `LeaveMessage` | TCP | quem sai → o outro | saí da sala |
| `RematchMessage` | TCP | convidado → anfitrião | quero jogar de novo |

### Decisões que valem explicar

- **O campo não trafega.** Só a semente (um `long`). Os dois chamam `new Board(linhas, colunas, minas, semente)` e obtêm o mesmo campo. O sorteio usa só `Random.nextInt(int)`, cujo algoritmo é fixado pela documentação do Java.
- **Relógios não são sincronizados**, como já aponta o tutorial do ping. Por isso a contagem vai em segundos e cada lado conta no próprio relógio a partir do recebimento. A diferença entre os dois inícios é de uma ida de pacote na LAN.
- **Vence quem chega primeiro ao anfitrião.** O primeiro `FinishMessage` (ou a própria conclusão do anfitrião) define o vencedor e os demais são ignorados. Uma vantagem de poucos milissegundos para o anfitrião é possível e aceita.
- **Detecção de queda.** Cada lado manda `StatusMessage` a cada segundo. Oito segundos sem ouvir o outro contam como saída.
- **Rodada numerada.** Mensagens de uma rodada antiga (progresso, fim) são descartadas na nova.
- **Multicast no Wi-Fi** costuma ser bloqueado. A caixa **Anúncios** do lobby troca para unicast (uma cópia por pessoa). Só o anúncio do lobby muda; o jogo sempre usa TCP.

### Fluxo

```
Lobby ──criar──▶ WAITING (anfitrião, anuncia a sala)
Lobby ──entrar─▶ JOINING ──ACCEPTED──▶ WAITING
dois na sala ──▶ COUNTDOWN (5 s) ──▶ PLAYING ──▶ FINISHED ──revanche──▶ COUNTDOWN
convidado sai ──▶ anfitrião volta a WAITING (vitória por W.O. se estava jogando)
anfitrião sai ──▶ convidado vai para CLOSED ("Voltar ao lobby")
```

## Estrutura

```
src/pitiupi/minesweeper/
  net/      mensagens e enums serializáveis (o protocolo)
  game/     Board: regras do campo, sem Swing e sem rede; GameRules: tamanho fixo do campo
  control/  GameController: máquina de estados; Net: o que precisa da rede; PitiupiNet: implementação com MessageDispatcher
  GUI/      MinesweeperPanel (lobby/sala), LobbyView, GameView, BoardView
  plugin/   MinesweeperPlugin: liga tudo ao núcleo
src/META-INF/services/pitiupi.models.Plugin
test/pitiupi/minesweeper/SelfTest.java
```

O `GameController` só fala com a rede pela interface `Net` e roda inteiro na thread do Swing (o plugin repassa `receiveMessage` com `invokeLater`). Por isso o protocolo é testável sem rede: o `SelfTest` liga dois ou três controllers por uma rede em memória, com as mensagens passando pela serialização de verdade.

## Compilar e instalar

Na raiz do clone (o script compila o núcleo e todos os plugins do catálogo, inclusive este):

```
scripts\pitiupi.cmd run --all          (Windows, pelo Prompt de Comando)
./scripts/pitiupi.sh run --all         (Linux/WSL)
```

Só este plugin: `scripts\pitiupi.cmd run --plugins minesweeper`. Para compilar à mão: `cd plugins/minesweeper && ant jar` (precisa de `pitiupi/dist/pitiupi.jar`) e conferir `jar tf dist/pitiupi.minesweeper.jar | grep META-INF/services`.

## Testar

Duas instâncias no mesmo PC **não** servem para jogar: a porta define a sala e o TCP para o próprio endereço é proibido pelo núcleo. Use dois computadores na mesma porta (5000), com o firewall liberando UDP e TCP nela.

Testes automáticos (sem JUnit), a partir da raiz, com o núcleo já compilado em `pitiupi/dist/pitiupi.jar`:

```
javac -encoding UTF-8 -cp pitiupi/dist/pitiupi.jar -d /tmp/ms plugins/minesweeper/src/pitiupi/minesweeper/*/*.java
javac -encoding UTF-8 -cp pitiupi/dist/pitiupi.jar:/tmp/ms -d /tmp/ms-test plugins/minesweeper/test/pitiupi/minesweeper/SelfTest.java
java -Djava.awt.headless=true -cp pitiupi/dist/pitiupi.jar:/tmp/ms:/tmp/ms-test pitiupi.minesweeper.SelfTest
```

No Windows troque `:` por `;` no classpath. Resultado esperado: `16 ok, 0 falhou`.
