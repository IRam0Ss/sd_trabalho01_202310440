/**
 * Teste de Interoperabilidade Global:
 * Servidor EDEN + Cliente Iury + Cliente Luan + Cliente Lucas
 * Todos interagindo simultaneamente na mesma sala!
 */
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TesteInteroperabilidadeGlobal {
  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");

      // 1. Inicia o Servidor
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();
      Thread.sleep(1000);

      System.out.println("\n=======================================================");
      System.out.println(" TESTE DE INTEROPERABILIDADE GLOBAL (3 CLIENTES DISTINTOS)");
      System.out.println("=======================================================\n");

      // 2. Cliente 1: Luan (Cliente_LUAN)
      Model.Cliente luan = new Model.Cliente("Luan", "127.0.0.1");
      boolean luanLogou = luan.fazerLogin();
      boolean luanEntrou = luan.entrarGrupo("salaGlobal");
      System.out.println(">>> Luan conectado e no grupo: " + (luanLogou && luanEntrou));

      // 3. Cliente 2: Lucas (lucas_cliente)
      Cliente lucas = new Cliente("127.0.0.1", 6789);
      Protocol.APDU joinLucas = new Protocol.APDU("JOIN", "salaGlobal", "Lucas", null, 46111);
      String respLucasJoin = lucas.enviarComandoTCP(joinLucas);
      System.out.println(">>> Lucas conectado e no grupo: " + respLucasJoin.startsWith("OK:"));

      // 4. Cliente 3: Iury (Cliente EDEN)
      model.ClienteTCP tcpIury = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpIury = new model.ClienteUDP("127.0.0.1", 7777);
      Thread tUdpIury = new Thread(udpIury);
      tUdpIury.setDaemon(true);
      tUdpIury.start();

      utils.InfoUser infoIury = new utils.InfoUser("Iury", "127.0.0.1", udpIury.getPortaLocal());
      tcpIury.register(infoIury);
      tcpIury.join("salaGlobal", infoIury);
      System.out.println(">>> Iury conectado e no grupo salaGlobal");

      // 5. Teste de USERS: Iury consulta e verifica se todos os 3 estao listados
      Protocol.APDU usersResp = tcpIury.listUsers();
      System.out.println(">>> Lista global de usuarios online: " + usersResp.getTextoMensagem());
      boolean contemTodos = usersResp.getTextoMensagem().contains("Luan") &&
                            usersResp.getTextoMensagem().contains("Lucas") &&
                            usersResp.getTextoMensagem().contains("Iury");
      System.out.println(">>> Todos os 3 clientes presentes no servidor? " + contemTodos);

      // 6. Teste de MEMBERS da salaGlobal
      Protocol.APDU membersResp = tcpIury.listMembers("salaGlobal");
      System.out.println(">>> Membros da salaGlobal: " + membersResp.getTextoMensagem());
      boolean membrosTodos = membersResp.getTextoMensagem().contains("Luan") &&
                             membersResp.getTextoMensagem().contains("Lucas") &&
                             membersResp.getTextoMensagem().contains("Iury");
      System.out.println(">>> Todos os 3 membros presentes no grupo? " + membrosTodos);

      // 7. Envio de mensagem de um para todos
      CountDownLatch latch = new CountDownLatch(1);
      udpIury.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String texto, boolean isPrivate, boolean isVisualizacaoUnica) {
          System.out.println("  [IURY LISTENER] Recebeu de " + remetente.getNome() + ": " + texto);
          if ("Luan".equals(remetente.getNome()) && "Ola a todos do chat global!".equals(texto)) {
            latch.countDown();
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String confirmadoPor) {}
      });

      // Luan manda mensagem
      luan.enviarMensagem("salaGlobal", "Ola a todos do chat global!", false);
      boolean msgChegou = latch.await(2, TimeUnit.SECONDS);
      System.out.println(">>> Mensagem do Luan entregue para Iury? " + msgChegou);

      // 8. Desconexao limpa
      luan.sairGrupo("salaGlobal");
      luan.fazerLogout();
      luan.desligarCliente();

      tcpIury.leave("salaGlobal", infoIury);
      tcpIury.fecharConexao();
      udpIury.fecharConexao();

      if (contemTodos && membrosTodos && msgChegou) {
        System.out.println("\n=======================================================");
        System.out.println(" SUCESSO TOTAL: 100% DE INTEROPERABILIDADE GLOBAL!     ");
        System.out.println("=======================================================\n");
        System.exit(0);
      } else {
        System.err.println("FALHA na interoperabilidade global!");
        System.exit(1);
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}

