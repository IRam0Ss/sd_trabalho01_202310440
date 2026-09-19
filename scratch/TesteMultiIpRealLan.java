import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TesteMultiIpRealLan {
  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");

      System.out.println("==================================================");
      System.out.println("  TESTE DE COMUNICACAO MULTI-IP (127.0.0.1 + LAN) ");
      System.out.println("==================================================");

      // 1. Inicia Servidor
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();
      Thread.sleep(1000);

      // 2. Cliente A: Iury conecta via 127.0.0.1 no TCP
      model.ClienteTCP tcpIury = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpIury = new model.ClienteUDP("127.0.0.1", 7777);
      Thread tUdpIury = new Thread(udpIury);
      tUdpIury.setDaemon(true);
      tUdpIury.start();

      utils.InfoUser infoIury = new utils.InfoUser("Iury", "127.0.0.1", udpIury.getPortaLocal());
      tcpIury.register(infoIury);
      tcpIury.join("salaLan", infoIury);

      // 3. Cliente B: Colega conecta via LAN IP (10.0.39.41)
      String lanIp = "10.0.39.41";
      model.ClienteTCP tcpColega = new model.ClienteTCP(lanIp, 6789);
      model.ClienteUDP udpColega = new model.ClienteUDP(lanIp, 7777);
      Thread tUdpColega = new Thread(udpColega);
      tUdpColega.setDaemon(true);
      tUdpColega.start();

      utils.InfoUser infoColega = new utils.InfoUser("Colega", lanIp, udpColega.getPortaLocal());
      tcpColega.register(infoColega);
      tcpColega.join("salaLan", infoColega);

      // 4. Teste de envio do Iury para o grupo
      CountDownLatch latchRecebeuColega = new CountDownLatch(1);
      AtomicBoolean colegaRecebeu = new AtomicBoolean(false);

      udpColega.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String texto, boolean isPrivate, boolean isVisualizacaoUnica) {
          System.out.println("  [COLEGA RECEBEU] de " + remetente.getNome() + ": " + texto);
          if (texto.contains("Ola da maquina local")) {
            colegaRecebeu.set(true);
            latchRecebeuColega.countDown();
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      System.out.println("\n[1] Iury enviando mensagem no grupo salaLan...");
      udpIury.send("salaLan", infoIury, "Ola da maquina local para a rede!");

      boolean entregueColega = latchRecebeuColega.await(3, TimeUnit.SECONDS);
      System.out.println(">>> Mensagem do Iury recebida pelo Colega? " + (entregueColega && colegaRecebeu.get()));

      // 5. Teste de resposta do Colega para o Iury
      CountDownLatch latchRecebeuIury = new CountDownLatch(1);
      AtomicBoolean iuryRecebeu = new AtomicBoolean(false);

      udpIury.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String texto, boolean isPrivate, boolean isVisualizacaoUnica) {
          System.out.println("  [IURY RECEBEU] de " + remetente.getNome() + ": " + texto);
          if (texto.contains("Ola da rede Wi-Fi")) {
            iuryRecebeu.set(true);
            latchRecebeuIury.countDown();
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      System.out.println("\n[2] Colega respondendo no grupo salaLan...");
      udpColega.send("salaLan", infoColega, "Ola da rede Wi-Fi recebido perfeitamente!");

      boolean entregueIury = latchRecebeuIury.await(3, TimeUnit.SECONDS);
      System.out.println(">>> Mensagem do Colega recebida pelo Iury? " + (entregueIury && iuryRecebeu.get()));

      if (entregueColega && colegaRecebeu.get() && entregueIury && iuryRecebeu.get()) {
        System.out.println("\n==================================================");
        System.out.println(" SUCESSO TOTAL: COMUNICACAO MULTI-IP FUNCIONANDO! ");
        System.out.println("==================================================\n");
        System.exit(0);
      } else {
        System.err.println("FALHA: Nao houve troca bidirecional de mensagens.");
        System.exit(1);
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}

