import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteNoFakeSystemMessages {
  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");

      System.out.println("==========================================================");
      System.out.println(" TESTE: VERIFICAR QUE O SERVIDOR NAO EMITE FALSAS APDUS   ");
      System.out.println("          (~JOINED~ / ~LEFT~) VIA UDP NOS GRUPOS          ");
      System.out.println("==========================================================");

      // 1. Inicia o Servidor
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();
      Thread.sleep(1000);

      // 2. Cliente 1: Bob conecta e entra no grupo 'salaSilenciosa'
      model.ClienteTCP tcpBob = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpBob = new model.ClienteUDP("127.0.0.1", 7777);
      Thread tUdpBob = new Thread(udpBob);
      tUdpBob.setDaemon(true);
      tUdpBob.start();

      utils.InfoUser infoBob = new utils.InfoUser("Bob", "127.0.0.1", udpBob.getPortaLocal());
      tcpBob.register(infoBob);
      tcpBob.join("salaSilenciosa", infoBob);

      AtomicInteger fakeMessagesReceived = new AtomicInteger(0);
      CountDownLatch latchRealMessage = new CountDownLatch(1);
      AtomicBoolean realMessageReceived = new AtomicBoolean(false);

      udpBob.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String texto, boolean isPrivate, boolean isVisualizacaoUnica) {
          System.out.println("  [BOB RECEBEU UDP] de " + remetente.getNome() + ": '" + texto + "'");
          if (texto.contains("~JOINED~") || texto.contains("~LEFT~")) {
            fakeMessagesReceived.incrementAndGet();
            System.err.println("  [ERRO] Bob recebeu mensagem de sistema indevida: " + texto);
          } else if (texto.equals("Mensagem Real da Carol")) {
            realMessageReceived.set(true);
            latchRealMessage.countDown();
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      // 3. Cliente 2: Carol conecta e entra no grupo 'salaSilenciosa'
      System.out.println("\n[1] Carol entrando no grupo 'salaSilenciosa' via TCP JOIN...");
      model.ClienteTCP tcpCarol = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpCarol = new model.ClienteUDP("127.0.0.1", 7777);
      Thread tUdpCarol = new Thread(udpCarol);
      tUdpCarol.setDaemon(true);
      tUdpCarol.start();

      utils.InfoUser infoCarol = new utils.InfoUser("Carol", "127.0.0.1", udpCarol.getPortaLocal());
      tcpCarol.register(infoCarol);
      tcpCarol.join("salaSilenciosa", infoCarol);

      // Aguarda 1 segundo para garantir que nenhum pacote UDP falso chegue ao Bob
      Thread.sleep(1000);

      boolean zeroFakesAposJoin = (fakeMessagesReceived.get() == 0);
      System.out.println(">>> Nenhuma mensagem fake (~JOINED~) recebida por Bob? " + zeroFakesAposJoin);

      // 4. Carol envia uma mensagem real no grupo
      System.out.println("\n[2] Carol enviando mensagem real 'Mensagem Real da Carol'...");
      udpCarol.send("salaSilenciosa", infoCarol, "Mensagem Real da Carol");

      boolean realOk = latchRealMessage.await(3, TimeUnit.SECONDS);
      System.out.println(">>> Mensagem real entregue com sucesso? " + (realOk && realMessageReceived.get()));

      // 5. Carol sai do grupo 'salaSilenciosa' via TCP LEAVE
      System.out.println("\n[3] Carol saindo do grupo 'salaSilenciosa' via TCP LEAVE...");
      tcpCarol.leave("salaSilenciosa", infoCarol);

      // Aguarda 1 segundo para garantir que nenhum pacote UDP falso chegue ao Bob
      Thread.sleep(1000);

      boolean zeroFakesAposLeave = (fakeMessagesReceived.get() == 0);
      System.out.println(">>> Nenhuma mensagem fake (~LEFT~) recebida por Bob? " + zeroFakesAposLeave);

      // 6. Validacao final
      if (zeroFakesAposJoin && zeroFakesAposLeave && realOk && realMessageReceived.get()) {
        System.out.println("\n==========================================================");
        System.out.println(" SUCESSO TOTAL: NENHUMA APDU FALSA EMITIDA PELO SERVIDOR! ");
        System.out.println("==========================================================\n");
        System.exit(0);
      } else {
        System.err.println("FALHA: Mensagens falsas foram recebidas ou a mensagem real falhou.");
        System.exit(1);
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}

