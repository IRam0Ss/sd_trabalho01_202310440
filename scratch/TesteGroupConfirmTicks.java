/**
 * Teste Automatizado da APDU CONFIRM em Grupo (Validação de Leitura por TODOS os membros)
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class TesteGroupConfirmTicks {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("   TESTANDO CONFIRM DE GRUPO (TODOS OS MEMBROS)   ");
    System.out.println("==================================================");

    try {
      // 1. Subir Servidor EDEN
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();

      Thread.sleep(1500);

      // 2. Criar Cliente Alice
      model.ClienteTCP tcpAlice = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpAlice = new model.ClienteUDP("127.0.0.1", 7777);
      udpAlice.setMeuNome("Alice");
      Thread tUdpAlice = new Thread(udpAlice);
      tUdpAlice.setDaemon(true);
      tUdpAlice.start();

      utils.InfoUser userAlice = new utils.InfoUser("Alice", "127.0.0.1", udpAlice.getPortaLocal());
      tcpAlice.register(userAlice);

      // 3. Criar Cliente Bob
      model.ClienteTCP tcpBob = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpBob = new model.ClienteUDP("127.0.0.1", 7777);
      udpBob.setMeuNome("Bob");
      Thread tUdpBob = new Thread(udpBob);
      tUdpBob.setDaemon(true);
      tUdpBob.start();

      utils.InfoUser userBob = new utils.InfoUser("Bob", "127.0.0.1", udpBob.getPortaLocal());
      tcpBob.register(userBob);

      // 4. Criar Cliente Carol
      model.ClienteTCP tcpCarol = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpCarol = new model.ClienteUDP("127.0.0.1", 7777);
      udpCarol.setMeuNome("Carol");
      Thread tUdpCarol = new Thread(udpCarol);
      tUdpCarol.setDaemon(true);
      tUdpCarol.start();

      utils.InfoUser userCarol = new utils.InfoUser("Carol", "127.0.0.1", udpCarol.getPortaLocal());
      tcpCarol.register(userCarol);

      // 5. Entrar todos no grupo "Devs"
      tcpAlice.join("Devs", userAlice);
      tcpBob.join("Devs", userBob);
      tcpCarol.join("Devs", userCarol);

      final List<Integer> statusRecebidosAlice = Collections.synchronizedList(new ArrayList<>());
      final List<String> confirmadosAlice = Collections.synchronizedList(new ArrayList<>());

      udpAlice.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {}
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {
          System.out.println("    [LISTENER ALICE] Tick -> ID: " + idMensagem + " | Status: " + status + " | Confirmou: " + nomeConfirmou);
          statusRecebidosAlice.add(status);
          confirmadosAlice.add(nomeConfirmou);
        }
      });

      // 6. Alice envia mensagem no grupo Devs
      System.out.println("\n[1] Alice enviando mensagem no grupo 'Devs'...");
      String idMsg = udpAlice.send("Devs", userAlice, "Ola grupo!");
      System.out.println("    ID Mensagem: " + idMsg);

      // Aguarda Auto-ACK de entrega dos membros
      Thread.sleep(1500);

      System.out.println("    Status recebidos ate agora por Alice: " + statusRecebidosAlice);
      boolean temStatus2 = statusRecebidosAlice.contains(2);
      if (!temStatus2) {
        throw new RuntimeException("FALHA: Alice nao recebeu o ACK de entrega (Status 2)!");
      }
      System.out.println("    >>> SUCESSO: ACK de entrega recebido pelos clientes do grupo!");

      // 7. Apenas Bob confirma leitura (Status 3)
      System.out.println("\n[2] Bob abrindo o grupo e confirmando leitura (Status 3)...");
      udpBob.sendConfirm(idMsg, 3, "Devs", "Alice");

      Thread.sleep(1500);

      // Com agregacao no servidor: Alice NAO deve receber Status 3 ainda (aguardando Carol)
      long countStatus3 = statusRecebidosAlice.stream().filter(s -> s == 3).count();
      System.out.println("    Confirmacoes de leitura (Status 3) recebidas por Alice ate agora: " + countStatus3);
      if (countStatus3 != 0) {
        throw new RuntimeException("FALHA: O servidor nao deve repassar Status 3 quando apenas 1 membro leu!");
      }
      System.out.println("    >>> SUCESSO PARCIAL: O servidor reteve o Status 3 pois Carol ainda nao leu. Alice continua em Status 2.");

      // 8. Carol confirma leitura (Status 3)
      System.out.println("\n[3] Carol abrindo o grupo e confirmando leitura (Status 3)...");
      udpCarol.sendConfirm(idMsg, 3, "Devs", "Alice");

      Thread.sleep(1500);

      // Agora que TODOS leram (Bob e Carol), o servidor deve emitir Status 3 ("TODOS") para Alice
      countStatus3 = statusRecebidosAlice.stream().filter(s -> s == 3).count();
      System.out.println("    Confirmacoes de leitura (Status 3) recebidas por Alice apos todos lerem: " + countStatus3);
      if (countStatus3 != 1) {
        throw new RuntimeException("FALHA: Esperado exatamente 1 confirmacao de leitura agregada (TODOS leram)!");
      }
      if (!confirmadosAlice.contains("TODOS")) {
        throw new RuntimeException("FALHA: Esperado que a confirmacao agregada venha identificada como 'TODOS'!");
      }

      System.out.println("    >>> SUCESSO TOTAL: O servidor agregou e repassou Status 3 (TODOS) quando todos leram!");
      System.out.println("\n==================================================");
      System.out.println("  TESTE DE CONFIRM EM GRUPO PASSOU COM 100% SUCESSO!");
      System.out.println("==================================================");
      System.exit(0);

    } catch (Exception e) {
      System.err.println("\n[ERRO NO TESTE] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}

