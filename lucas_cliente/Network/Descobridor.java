/*****************************************************************
* Autor..............: Lucas de Menezes Chaves
* Matricula........: 202310282
* Inicio...........: 20/06/2026
* Ultima alteracao.: 10/09/2026
* Nome.............: Descobridor
* Funcao...........: Descobrir o IP do servidor automaticamente via UDP broadcast.
*
* Estrategia de descoberta (em ordem):
*   1. Envia broadcast para cada interface de rede ativa da maquina
*      (ex: 192.168.1.255, 10.0.0.255) -- mais confiavel no macOS/Linux
*   2. Envia broadcast global 255.255.255.255 -- fallback
*   3. Se nenhum servidor responder em 3 segundos, retorna null
*      (o chamador pode entao pedir o IP manualmente ou usar localhost)
*
* Por que multiplas interfaces?
*   O macOS muitas vezes bloqueia/ignora 255.255.255.255 ao nivel do
*   kernel para trafego local. Mandar diretamente pelo broadcast da
*   subrede (ex: 192.168.1.255) contorna esse problema.
*************************************************************** */

package Network;

import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Descobridor {
  private static final int PORTA_DISCOVERY = 8888;
  private static final int TIMEOUT_MS      = 3000;

  /********************************************************************
  * Metodo: buscarIPServidor
  * Funcao: Descobre o IP do servidor enviando broadcast UDP.
  *         Tenta cada broadcast de interface de rede local e depois o
  *         broadcast global 255.255.255.255.
  * @return IP do servidor como String, ou null se nao encontrado
  * ****************************************************************** */
  public static String buscarIPServidor() {
    //Coleta todos os enderecos de broadcast das interfaces ativas
    List<InetAddress> broadcasts = coletarBroadcasts();

    //Adiciona o broadcast global como ultimo recurso
    try {
      broadcasts.add(InetAddress.getByName("255.255.255.255"));
    } catch(UnknownHostException e) {
      //ignora
    }//fim do try-catch

    try(DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(TIMEOUT_MS);

      byte[] pedido = "SERVIDOR_IP".getBytes();
      byte[] buffer = new byte[256];

      //Tenta cada endereco de broadcast encontrado
      for(InetAddress broadcastAddr : broadcasts) {
        try {
          System.out.println("[DISCOVERY] Tentando broadcast em: " + broadcastAddr.getHostAddress());
          DatagramPacket pacoteGrito = new DatagramPacket(
            pedido, pedido.length, broadcastAddr, PORTA_DISCOVERY);
          socket.send(pacoteGrito);
        } catch(Exception e) {
          System.err.println("[DISCOVERY] Falha ao enviar para " + broadcastAddr + ": " + e.getMessage());
        }//fim do try-catch
      }//fim do for

      //Aguarda resposta de qualquer um dos broadcasts enviados
      DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(pacoteResposta);
        String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
        if(resposta.equals("IP")) {
          String ip = pacoteResposta.getAddress().getHostAddress();
          System.out.println("[DISCOVERY] Servidor encontrado em: " + ip);
          return ip;
        }//fim do if
      } catch(SocketTimeoutException e) {
        System.err.println("[DISCOVERY] Nenhum servidor respondeu em " + (TIMEOUT_MS/1000) + "s.");
      }//fim do try-catch

    } catch(Exception e) {
      System.err.println("[DISCOVERY] Erro inesperado: " + e.getMessage());
    }//fim do try-catch

    return tentarLocalhostDireto();
  }//fim do metodo

  /********************************************************************
  * Metodo: tentarLocalhostDireto
  * Funcao: Tenta descoberta direto em localhost (127.0.0.1).
  *         Util quando cliente e servidor estao na mesma maquina,
  *         pois o broadcast nao retorna pela interface loopback no macOS.
  * @return "127.0.0.1" se servidor responder, null caso contrario
  * ****************************************************************** */
  private static String tentarLocalhostDireto() {
    System.out.println("[DISCOVERY] Tentando conexao direta em localhost...");
    try(DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(1500);
      byte[] pedido = "SERVIDOR_IP".getBytes();
      byte[] buffer = new byte[256];
      DatagramPacket pacoteGrito = new DatagramPacket(
        pedido, pedido.length,
        InetAddress.getByName("127.0.0.1"),
        PORTA_DISCOVERY
      );
      socket.send(pacoteGrito);

      DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
      socket.receive(pacoteResposta);
      String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
      if(resposta.equals("IP")) {
        System.out.println("[DISCOVERY] Servidor encontrado em localhost.");
        return "127.0.0.1";
      }//fim do if
    } catch(SocketTimeoutException e) {
      System.err.println("[DISCOVERY] Servidor nao encontrado em localhost.");
    } catch(Exception e) {
      System.err.println("[DISCOVERY] Erro ao tentar localhost: " + e.getMessage());
    }//fim do try-catch
    return null;
  }//fim do metodo

  /********************************************************************
  * Metodo: coletarBroadcasts
  * Funcao: Percorre todas as interfaces de rede ativas e coleta seus
  *         enderecos de broadcast (ex: 192.168.1.255).
  *         Isso e mais confiavel que 255.255.255.255 no macOS.
  * @return lista de InetAddress de broadcast
  * ****************************************************************** */
  private static List<InetAddress> coletarBroadcasts() {
    List<InetAddress> lista = new ArrayList<InetAddress>();
    try {
      java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while(interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        //Ignora interfaces inativas, loopback e virtuais
        if(!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

        for(InterfaceAddress ia : ni.getInterfaceAddresses()) {
          InetAddress broadcast = ia.getBroadcast();
          if(broadcast != null) {
            lista.add(broadcast);
            System.out.println("[DISCOVERY] Interface '" + ni.getDisplayName() +
              "' -> broadcast: " + broadcast.getHostAddress());
          }//fim do if
        }//fim do for
      }//fim do while
    } catch(Exception e) {
      System.err.println("[DISCOVERY] Erro ao coletar interfaces: " + e.getMessage());
    }//fim do try-catch
    return lista;
  }//fim do metodo
}//fim da classe
