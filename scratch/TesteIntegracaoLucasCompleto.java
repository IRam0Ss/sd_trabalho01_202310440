/**
 * Teste Completo de Integracao e Interoperabilidade: Cliente Lucas <-> Servidor EDEN
 */
import Protocol.APDU;
import Network.RecebedorUDP;
import Network.MensagemListener;
import java.util.concurrent.atomic.AtomicReference;

public class TesteIntegracaoLucasCompleto {
  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");

      // 1. Inicia o Servidor
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();
      Thread.sleep(1000);

      // 2. Testa Descoberta UDP (Broadcast Discovery do Lucas)
      System.out.println("\n--- [TESTE 1] Descoberta UDP do Lucas ---");
      String ipDescoberto = Network.Descobridor.buscarIPServidor();
      System.out.println("IP descoberto pelo cliente Lucas: " + ipDescoberto);
      if (ipDescoberto == null) {
        System.err.println("FALHA: Lucas nao descobriu o IP do servidor!");
        System.exit(1);
      }

      // 3. Inicializa Cliente do Lucas
      System.out.println("\n--- [TESTE 2] Conexao e JOIN do Cliente Lucas ---");
      int portaUDPLucas = 45555;
      Cliente clienteLucas = new Cliente(ipDescoberto, 6789);
      RecebedorUDP recebedorLucas = new RecebedorUDP(portaUDPLucas, ipDescoberto, 7777);
      recebedorLucas.setDaemon(true);
      recebedorLucas.start();

      AtomicReference<APDU> msgRecebidaLucas = new AtomicReference<>(null);
      AtomicReference<APDU> pvtRecebidoLucas = new AtomicReference<>(null);

      recebedorLucas.setListener(new MensagemListener() {
        @Override
        public void onMessageReceived(APDU apdu) {
          System.out.println("  [LUCAS LISTENER] Grupo Msg: " + apdu.getTextoMensagem());
          msgRecebidaLucas.set(apdu);
        }

        @Override
        public void onPrivateMessageReceived(APDU apdu) {
          System.out.println("  [LUCAS LISTENER] Privado Msg: " + apdu.getTextoMensagem());
          pvtRecebidoLucas.set(apdu);
        }

        @Override
        public void onTickReceived(APDU apdu) {
          System.out.println("  [LUCAS LISTENER] Tick: " + apdu.getIdMensagem() + " -> Status " + apdu.getStatusRecebido());
        }
      });

      // JOIN no grupo 'geral'
      APDU joinApdu = new APDU("JOIN", "geral", "Lucas", null, portaUDPLucas);
      String respJoin = clienteLucas.enviarComandoTCP(joinApdu);
      System.out.println("Resposta JOIN: " + respJoin);
      if (respJoin == null || !respJoin.startsWith("OK:")) {
        System.err.println("FALHA: JOIN do Lucas falhou!");
        System.exit(1);
      }

      // 4. USERS (Listar usuarios ativos)
      System.out.println("\n--- [TESTE 3] USERS do Cliente Lucas ---");
      APDU usersApdu = new APDU("USERS", null, "Lucas", null, portaUDPLucas);
      String respUsers = clienteLucas.enviarComandoTCP(usersApdu);
      System.out.println("Resposta USERS: " + respUsers);
      if (respUsers == null || !respUsers.startsWith("OK: ") || !respUsers.contains("Lucas")) {
        System.err.println("FALHA: USERS do Lucas falhou!");
        System.exit(1);
      }

      // 5. MEMBERS (Listar membros do grupo 'geral')
      System.out.println("\n--- [TESTE 4] MEMBERS do Cliente Lucas ---");
      APDU membersApdu = new APDU("MEMBERS", "geral", "Lucas", null, portaUDPLucas);
      String respMembers = clienteLucas.enviarComandoTCP(membersApdu);
      System.out.println("Resposta MEMBERS: " + respMembers);
      if (respMembers == null || !respMembers.startsWith("OK: ") || !respMembers.contains("Lucas")) {
        System.err.println("FALHA: MEMBERS do Lucas falhou!");
        System.exit(1);
      }

      // 6. Envio de mensagem em grupo (SEND)
      System.out.println("\n--- [TESTE 5] Envio de Mensagem Grupo (SEND) ---");
      APDU sendApdu = new APDU("SEND", "geral", "Lucas", "Ola do cliente Lucas!", portaUDPLucas);
      clienteLucas.enviarMensagemUDP(sendApdu, 7777);
      Thread.sleep(500);

      // 7. BLOCK e UNBLOCK
      System.out.println("\n--- [TESTE 6] BLOCK e UNBLOCK pelo Cliente Lucas ---");
      APDU blockApdu = new APDU("BLOCK", null, "Lucas", null, portaUDPLucas, "Invasor");
      String respBlock = clienteLucas.enviarComandoTCP(blockApdu);
      System.out.println("Resposta BLOCK: " + respBlock);
      if (respBlock == null || !respBlock.startsWith("OK:")) {
        System.err.println("FALHA: BLOCK do Lucas falhou!");
        System.exit(1);
      }

      APDU unblockApdu = new APDU("UNBLOCK", null, "Lucas", null, portaUDPLucas, "Invasor");
      String respUnblock = clienteLucas.enviarComandoTCP(unblockApdu);
      System.out.println("Resposta UNBLOCK: " + respUnblock);
      if (respUnblock == null || !respUnblock.startsWith("OK:")) {
        System.err.println("FALHA: UNBLOCK do Lucas falhou!");
        System.exit(1);
      }

      // 8. LEAVE do grupo
      System.out.println("\n--- [TESTE 7] LEAVE do Cliente Lucas ---");
      APDU leaveApdu = new APDU("LEAVE", "geral", "Lucas", null, portaUDPLucas);
      String respLeave = clienteLucas.enviarComandoTCP(leaveApdu);
      System.out.println("Resposta LEAVE: " + respLeave);
      if (respLeave == null || !respLeave.startsWith("OK:")) {
        System.err.println("FALHA: LEAVE do Lucas falhou!");
        System.exit(1);
      }

      System.out.println("\n=================================================");
      System.out.println(" TESTE DE INTEGRACAO COM CLIENTE LUCAS: SUCESSO! ");
      System.out.println("=================================================\n");
      System.exit(0);

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}

